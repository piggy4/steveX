package net.minecraft.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class DirectoryLock implements AutoCloseable {
	public static final String LOCK_FILE = "session.lock";
	private final FileChannel lockFile;
	private final FileLock lock;
	private static final ByteBuffer DUMMY;

	public static DirectoryLock create(Path path) throws IOException {
		Path path2 = path.resolve("session.lock");
		FileUtil.createDirectoriesSafe(path);
		FileChannel fileChannel = FileChannel.open(path2, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

		try {
			fileChannel.write(DUMMY.duplicate());
			fileChannel.force(true);
			FileLock fileLock = fileChannel.tryLock();
			if (fileLock == null) {
				throw DirectoryLock.LockException.alreadyLocked(path2);
			} else {
				return new DirectoryLock(fileChannel, fileLock);
			}
		} catch (IOException iOException) {
			try {
				fileChannel.close();
			} catch (IOException iOException2) {
				iOException.addSuppressed(iOException2);
			}

			throw iOException;
		}
	}

	private DirectoryLock(FileChannel fileChannel, FileLock fileLock) {
		this.lockFile = fileChannel;
		this.lock = fileLock;
	}

	@Override
	public void close() throws IOException {
		try {
			if (this.lock.isValid()) {
				this.lock.release();
			}
		} finally {
			if (this.lockFile.isOpen()) {
				this.lockFile.close();
			}
		}
	}

	public boolean isValid() {
		return this.lock.isValid();
	}

	public static boolean isLocked(Path path) throws IOException {
		Path path2 = path.resolve("session.lock");

		try (
			FileChannel fileChannel = FileChannel.open(path2, StandardOpenOption.WRITE);
			FileLock fileLock = fileChannel.tryLock();
		) {
			return fileLock == null;
		} catch (AccessDeniedException accessDeniedException) {
			return true;
		} catch (NoSuchFileException noSuchFileException) {
			return false;
		}
	}

	static {
		byte[] bs = "☃".getBytes(StandardCharsets.UTF_8);
		DUMMY = ByteBuffer.allocateDirect(bs.length);
		DUMMY.put(bs);
		DUMMY.flip();
	}

	public static class LockException extends IOException {
		private LockException(Path path, String string) {
			super(path.toAbsolutePath() + ": " + string);
		}

		public static DirectoryLock.LockException alreadyLocked(Path path) {
			return new DirectoryLock.LockException(path, "already locked (possibly by other Minecraft instance?)");
		}
	}
}
