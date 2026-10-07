package com.mojang.realmsclient;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.gui.screens.RealmsClientOutdatedScreen;
import com.mojang.realmsclient.gui.screens.RealmsGenericErrorScreen;
import com.mojang.realmsclient.gui.screens.RealmsParentalConsentScreen;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class RealmsAvailability {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static @Nullable CompletableFuture<RealmsAvailability.Result> future;

	public static CompletableFuture<RealmsAvailability.Result> get() {
		if (future == null || shouldRefresh(future)) {
			future = check();
		}

		return future;
	}

	private static boolean shouldRefresh(CompletableFuture<RealmsAvailability.Result> completableFuture) {
		RealmsAvailability.Result result = completableFuture.getNow(null);
		return result != null && result.exception() != null;
	}

	private static CompletableFuture<RealmsAvailability.Result> check() {
		if (Minecraft.getInstance().isOfflineDeveloperMode()) {
			return CompletableFuture.completedFuture(new RealmsAvailability.Result(RealmsAvailability.Type.AUTHENTICATION_ERROR));
		} else {
			return SharedConstants.DEBUG_BYPASS_REALMS_VERSION_CHECK
				? CompletableFuture.completedFuture(new RealmsAvailability.Result(RealmsAvailability.Type.SUCCESS))
				: CompletableFuture.supplyAsync(
					() -> {
						RealmsClient realmsClient = RealmsClient.getOrCreate();

						try {
							if (realmsClient.clientCompatible() != RealmsClient.CompatibleVersionResponse.COMPATIBLE) {
								return new RealmsAvailability.Result(RealmsAvailability.Type.INCOMPATIBLE_CLIENT);
							} else {
								return !realmsClient.hasParentalConsent()
									? new RealmsAvailability.Result(RealmsAvailability.Type.NEEDS_PARENTAL_CONSENT)
									: new RealmsAvailability.Result(RealmsAvailability.Type.SUCCESS);
							}
						} catch (RealmsServiceException realmsServiceException) {
							LOGGER.error("Couldn't connect to realms", realmsServiceException);
							return realmsServiceException.realmsError.errorCode() == 401
								? new RealmsAvailability.Result(RealmsAvailability.Type.AUTHENTICATION_ERROR)
								: new RealmsAvailability.Result(realmsServiceException);
						}
					},
					Util.ioPool()
				);
		}
	}

	@Environment(EnvType.CLIENT)
	public record Result(RealmsAvailability.Type type, @Nullable RealmsServiceException exception) {
		public Result(RealmsAvailability.Type type) {
			this(type, null);
		}

		public Result(RealmsServiceException realmsServiceException) {
			this(RealmsAvailability.Type.UNEXPECTED_ERROR, realmsServiceException);
		}

		public @Nullable Screen createErrorScreen(Screen screen) {
			return switch (this.type) {
				case SUCCESS -> null;
				case INCOMPATIBLE_CLIENT -> new RealmsClientOutdatedScreen(screen);
				case NEEDS_PARENTAL_CONSENT -> new RealmsParentalConsentScreen(screen);
				case AUTHENTICATION_ERROR -> new RealmsGenericErrorScreen(
					Component.translatable("mco.error.invalid.session.title"), Component.translatable("mco.error.invalid.session.message"), screen
				);
				case UNEXPECTED_ERROR -> new RealmsGenericErrorScreen(Objects.requireNonNull(this.exception), screen);
			};
		}
	}

	@Environment(EnvType.CLIENT)
	public enum Type {
		SUCCESS,
		INCOMPATIBLE_CLIENT,
		NEEDS_PARENTAL_CONSENT,
		AUTHENTICATION_ERROR,
		UNEXPECTED_ERROR;
	}
}
