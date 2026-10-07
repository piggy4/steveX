package net.minecraft.util;

import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;

public class FileZipper implements Closeable {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final Path outputFile;
	private final Path tempFile;
	private final FileSystem fs;

	public FileZipper(Path path) {
		this.outputFile = path;
		this.tempFile = path.resolveSibling(path.getFileName().toString() + "_tmp");

		try {
			this.fs = Util.ZIP_FILE_SYSTEM_PROVIDER.newFileSystem(this.tempFile, ImmutableMap.of("create", "true"));
		} catch (IOException iOException) {
			throw new UncheckedIOException(iOException);
		}
	}

	public void add(Path path, String string) {
		try {
			Path path2 = this.fs.getPath(File.separator);
			Path path3 = path2.resolve(path.toString());
			Files.createDirectories(path3.getParent());
			Files.write(path3, string.getBytes(StandardCharsets.UTF_8));
		} catch (IOException iOException) {
			throw new UncheckedIOException(iOException);
		}
	}

	public void add(Path path, File file) {
		try {
			Path path2 = this.fs.getPath(File.separator);
			Path path3 = path2.resolve(path.toString());
			Files.createDirectories(path3.getParent());
			Files.copy(file.toPath(), path3);
		} catch (IOException iOException) {
			throw new UncheckedIOException(iOException);
		}
	}

	public void add(Path path) {
		try {
			Path path2 = this.fs.getPath(File.separator);
			if (Files.isRegularFile(path)) {
				Path path3 = path2.resolve(path.getParent().relativize(path).toString());
				Files.copy(path3, path);
			} else {
				try (Stream<Path> stream = Files.find(path, Integer.MAX_VALUE, (pathx, basicFileAttributes) -> basicFileAttributes.isRegularFile())) {
					for (Path path4 : stream.collect(Collectors.toList())) {
						Path path5 = path2.resolve(path.relativize(path4).toString());
						Files.createDirectories(path5.getParent());
						Files.copy(path4, path5);
					}
				}
			}
		} catch (IOException iOException) {
			throw new UncheckedIOException(iOException);
		}
	}

	@Override
	public void close() {
		try {
			this.fs.close();
			Files.move(this.tempFile, this.outputFile);
			LOGGER.info("Compressed to {}", this.outputFile);
		} catch (IOException iOException) {
			throw new UncheckedIOException(iOException);
		}
	}
}
