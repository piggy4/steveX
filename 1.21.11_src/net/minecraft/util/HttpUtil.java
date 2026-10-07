package net.minecraft.util;

import com.google.common.hash.Funnels;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class HttpUtil {
	private static final Logger LOGGER = LogUtils.getLogger();

	private HttpUtil() {
	}

	public static Path downloadFile(
		Path path,
		URL uRL,
		Map<String, String> map,
		HashFunction hashFunction,
		@Nullable HashCode hashCode,
		int i,
		Proxy proxy,
		HttpUtil.DownloadProgressListener downloadProgressListener
	) {
		HttpURLConnection httpURLConnection = null;
		InputStream inputStream = null;
		downloadProgressListener.requestStart();
		Path path2;
		if (hashCode != null) {
			path2 = cachedFilePath(path, hashCode);

			try {
				if (checkExistingFile(path2, hashFunction, hashCode)) {
					LOGGER.info("Returning cached file since actual hash matches requested");
					downloadProgressListener.requestFinished(true);
					updateModificationTime(path2);
					return path2;
				}
			} catch (IOException iOException) {
				LOGGER.warn("Failed to check cached file {}", path2, iOException);
			}

			try {
				LOGGER.warn("Existing file {} not found or had mismatched hash", path2);
				Files.deleteIfExists(path2);
			} catch (IOException iOException) {
				downloadProgressListener.requestFinished(false);
				throw new UncheckedIOException("Failed to remove existing file " + path2, iOException);
			}
		} else {
			path2 = null;
		}

		try {
			httpURLConnection = (HttpURLConnection)uRL.openConnection(proxy);
			httpURLConnection.setInstanceFollowRedirects(true);
			map.forEach(httpURLConnection::setRequestProperty);
			inputStream = httpURLConnection.getInputStream();
			long l = httpURLConnection.getContentLengthLong();
			OptionalLong optionalLong = l != -1L ? OptionalLong.of(l) : OptionalLong.empty();
			FileUtil.createDirectoriesSafe(path);
			downloadProgressListener.downloadStart(optionalLong);
			if (optionalLong.isPresent() && optionalLong.getAsLong() > i) {
				throw new IOException("Filesize is bigger than maximum allowed (file is " + optionalLong + ", limit is " + i + ")");
			}

			if (path2 != null) {
				HashCode hashCode2 = downloadAndHash(hashFunction, i, downloadProgressListener, inputStream, path2);
				if (!hashCode2.equals(hashCode)) {
					throw new IOException("Hash of downloaded file (" + hashCode2 + ") did not match requested (" + hashCode + ")");
				}

				downloadProgressListener.requestFinished(true);
				return path2;
			} else {
				Path path3 = Files.createTempFile(path, "download", ".tmp");

				try {
					HashCode hashCode3 = downloadAndHash(hashFunction, i, downloadProgressListener, inputStream, path3);
					Path path4 = cachedFilePath(path, hashCode3);
					if (!checkExistingFile(path4, hashFunction, hashCode3)) {
						Files.move(path3, path4, StandardCopyOption.REPLACE_EXISTING);
					} else {
						updateModificationTime(path4);
					}

					downloadProgressListener.requestFinished(true);
					return path4;
				} finally {
					Files.deleteIfExists(path3);
				}
			}
		} catch (Throwable throwable) {
			if (httpURLConnection != null) {
				InputStream inputStream2 = httpURLConnection.getErrorStream();
				if (inputStream2 != null) {
					try {
						LOGGER.error("HTTP response error: {}", IOUtils.toString(inputStream2, StandardCharsets.UTF_8));
					} catch (Exception exception) {
						LOGGER.error("Failed to read response from server");
					}
				}
			}

			downloadProgressListener.requestFinished(false);
			throw new IllegalStateException("Failed to download file " + uRL, throwable);
		} finally {
			IOUtils.closeQuietly(inputStream);
		}
	}

	private static void updateModificationTime(Path path) {
		try {
			Files.setLastModifiedTime(path, FileTime.from(Instant.now()));
		} catch (IOException iOException) {
			LOGGER.warn("Failed to update modification time of {}", path, iOException);
		}
	}

	private static HashCode hashFile(Path path, HashFunction hashFunction) throws IOException {
		Hasher hasher = hashFunction.newHasher();

		try (
			OutputStream outputStream = Funnels.asOutputStream(hasher);
			InputStream inputStream = Files.newInputStream(path);
		) {
			inputStream.transferTo(outputStream);
		}

		return hasher.hash();
	}

	private static boolean checkExistingFile(Path path, HashFunction hashFunction, HashCode hashCode) throws IOException {
		if (Files.exists(path)) {
			HashCode hashCode2 = hashFile(path, hashFunction);
			if (hashCode2.equals(hashCode)) {
				return true;
			}

			LOGGER.warn("Mismatched hash of file {}, expected {} but found {}", path, hashCode, hashCode2);
		}

		return false;
	}

	private static Path cachedFilePath(Path path, HashCode hashCode) {
		return path.resolve(hashCode.toString());
	}

	private static HashCode downloadAndHash(
		HashFunction hashFunction, int i, HttpUtil.DownloadProgressListener downloadProgressListener, InputStream inputStream, Path path
	) throws IOException {
		try (OutputStream outputStream = Files.newOutputStream(path, StandardOpenOption.CREATE)) {
			Hasher hasher = hashFunction.newHasher();
			byte[] bs = new byte[8196];
			long l = 0L;

			int j;
			while ((j = inputStream.read(bs)) >= 0) {
				l += j;
				downloadProgressListener.downloadedBytes(l);
				if (l > i) {
					throw new IOException("Filesize was bigger than maximum allowed (got >= " + l + ", limit was " + i + ")");
				}

				if (Thread.interrupted()) {
					LOGGER.error("INTERRUPTED");
					throw new IOException("Download interrupted");
				}

				outputStream.write(bs, 0, j);
				hasher.putBytes(bs, 0, j);
			}

			return hasher.hash();
		}
	}

	public static int getAvailablePort() {
		try (ServerSocket serverSocket = new ServerSocket(0)) {
			return serverSocket.getLocalPort();
		} catch (IOException iOException) {
			return 25564;
		}
	}

	public static boolean isPortAvailable(int i) {
		if (i >= 0 && i <= 65535) {
			try (ServerSocket serverSocket = new ServerSocket(i)) {
				return serverSocket.getLocalPort() == i;
			} catch (IOException iOException) {
				return false;
			}
		} else {
			return false;
		}
	}

	public interface DownloadProgressListener {
		void requestStart();

		void downloadStart(OptionalLong optionalLong);

		void downloadedBytes(long l);

		void requestFinished(boolean bl);
	}
}
