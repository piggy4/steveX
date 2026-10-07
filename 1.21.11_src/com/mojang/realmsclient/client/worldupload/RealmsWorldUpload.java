package com.mojang.realmsclient.client.worldupload;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.FileUpload;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.RealmsSlot;
import com.mojang.realmsclient.dto.UploadInfo;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.exception.RetryCallException;
import com.mojang.realmsclient.gui.screens.UploadResult;
import com.mojang.realmsclient.util.UploadTokenCache;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.User;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class RealmsWorldUpload {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final int UPLOAD_RETRIES = 20;
	private final RealmsClient client = RealmsClient.getOrCreate();
	private final Path worldFolder;
	private final RealmsSlot realmsSlot;
	private final User user;
	private final long realmId;
	private final RealmsWorldUploadStatusTracker statusCallback;
	private volatile boolean cancelled;
	private volatile @Nullable CompletableFuture<?> uploadTask;

	public RealmsWorldUpload(Path path, RealmsSlot realmsSlot, User user, long l, RealmsWorldUploadStatusTracker realmsWorldUploadStatusTracker) {
		this.worldFolder = path;
		this.realmsSlot = realmsSlot;
		this.user = user;
		this.realmId = l;
		this.statusCallback = realmsWorldUploadStatusTracker;
	}

	public CompletableFuture<?> packAndUpload() {
		return CompletableFuture.runAsync(
			() -> {
				File file = null;

				try {
					UploadInfo uploadInfo = this.requestUploadInfoWithRetries();
					file = RealmsUploadWorldPacker.pack(this.worldFolder, () -> this.cancelled);
					this.statusCallback.setUploading();

					try (FileUpload fileUpload = new FileUpload(
							file,
							this.realmId,
							this.realmsSlot.slotId,
							uploadInfo,
							this.user,
							SharedConstants.getCurrentVersion().name(),
							this.realmsSlot.options.version,
							this.statusCallback.getUploadStatus()
						)) {
						CompletableFuture<UploadResult> completableFuture = fileUpload.startUpload();
						this.uploadTask = completableFuture;
						if (!this.cancelled) {
							UploadResult uploadResult;
							try {
								uploadResult = completableFuture.join();
							} catch (CompletionException completionException) {
								throw completionException.getCause();
							}

							String string = uploadResult.getSimplifiedErrorMessage();
							if (string != null) {
								throw new RealmsUploadFailedException(string);
							}

							UploadTokenCache.invalidate(this.realmId);
							this.client.updateSlot(this.realmId, this.realmsSlot.slotId, this.realmsSlot.options, this.realmsSlot.settings);
							return;
						}

						completableFuture.cancel(true);
					}
				} catch (RealmsServiceException realmsServiceException) {
					throw new RealmsUploadFailedException(realmsServiceException.realmsError.errorMessage());
				} catch (InterruptedException | CancellationException exception) {
					throw new RealmsUploadCanceledException();
				} catch (RealmsUploadException realmsUploadException) {
					throw realmsUploadException;
				} catch (Throwable throwable) {
					if (throwable instanceof Error error) {
						throw error;
					}

					throw new RealmsUploadFailedException(throwable.getMessage());
				} finally {
					if (file != null) {
						LOGGER.debug("Deleting file {}", file.getAbsolutePath());
						file.delete();
					}
				}
			},
			Util.backgroundExecutor()
		);
	}

	public void cancel() {
		this.cancelled = true;
		CompletableFuture<?> completableFuture = this.uploadTask;
		if (completableFuture != null) {
			completableFuture.cancel(true);
		}
	}

	private UploadInfo requestUploadInfoWithRetries() throws RealmsServiceException, InterruptedException {
		for (int i = 0; i < 20; i++) {
			try {
				UploadInfo uploadInfo = this.client.requestUploadInfo(this.realmId);
				if (this.cancelled) {
					throw new RealmsUploadCanceledException();
				}

				if (uploadInfo != null) {
					if (!uploadInfo.worldClosed()) {
						throw new RealmsUploadWorldNotClosedException();
					}

					return uploadInfo;
				}
			} catch (RetryCallException retryCallException) {
				Thread.sleep(retryCallException.delaySeconds * 1000L);
			}
		}

		throw new RealmsUploadWorldNotClosedException();
	}
}
