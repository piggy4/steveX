package net.minecraft.nbt;

import com.google.common.annotations.VisibleForTesting;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.util.DelegateDataOutput;
import net.minecraft.util.FastBufferedInputStream;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class NbtIo {
	private static final OpenOption[] SYNC_OUTPUT_OPTIONS = new OpenOption[]{
		StandardOpenOption.SYNC, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
	};

	public static CompoundTag readCompressed(Path path, NbtAccounter nbtAccounter) throws IOException {
		try (
			InputStream inputStream = Files.newInputStream(path);
			InputStream inputStream2 = new FastBufferedInputStream(inputStream);
		) {
			return readCompressed(inputStream2, nbtAccounter);
		}
	}

	private static DataInputStream createDecompressorStream(InputStream inputStream) throws IOException {
		return new DataInputStream(new FastBufferedInputStream(new GZIPInputStream(inputStream)));
	}

	private static DataOutputStream createCompressorStream(OutputStream outputStream) throws IOException {
		return new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(outputStream)));
	}

	public static CompoundTag readCompressed(InputStream inputStream, NbtAccounter nbtAccounter) throws IOException {
		try (DataInputStream dataInputStream = createDecompressorStream(inputStream)) {
			return read(dataInputStream, nbtAccounter);
		}
	}

	public static void parseCompressed(Path path, StreamTagVisitor streamTagVisitor, NbtAccounter nbtAccounter) throws IOException {
		try (
			InputStream inputStream = Files.newInputStream(path);
			InputStream inputStream2 = new FastBufferedInputStream(inputStream);
		) {
			parseCompressed(inputStream2, streamTagVisitor, nbtAccounter);
		}
	}

	public static void parseCompressed(InputStream inputStream, StreamTagVisitor streamTagVisitor, NbtAccounter nbtAccounter) throws IOException {
		try (DataInputStream dataInputStream = createDecompressorStream(inputStream)) {
			parse(dataInputStream, streamTagVisitor, nbtAccounter);
		}
	}

	public static void writeCompressed(CompoundTag compoundTag, Path path) throws IOException {
		try (
			OutputStream outputStream = Files.newOutputStream(path, SYNC_OUTPUT_OPTIONS);
			OutputStream outputStream2 = new BufferedOutputStream(outputStream);
		) {
			writeCompressed(compoundTag, outputStream2);
		}
	}

	public static void writeCompressed(CompoundTag compoundTag, OutputStream outputStream) throws IOException {
		try (DataOutputStream dataOutputStream = createCompressorStream(outputStream)) {
			write(compoundTag, dataOutputStream);
		}
	}

	public static void write(CompoundTag compoundTag, Path path) throws IOException {
		try (
			OutputStream outputStream = Files.newOutputStream(path, SYNC_OUTPUT_OPTIONS);
			OutputStream outputStream2 = new BufferedOutputStream(outputStream);
			DataOutputStream dataOutputStream = new DataOutputStream(outputStream2);
		) {
			write(compoundTag, dataOutputStream);
		}
	}

	public static @Nullable CompoundTag read(Path path) throws IOException {
		if (!Files.exists(path)) {
			return null;
		}

		try (
			InputStream inputStream = Files.newInputStream(path);
			DataInputStream dataInputStream = new DataInputStream(inputStream);
		) {
			return read(dataInputStream, NbtAccounter.unlimitedHeap());
		}
	}

	public static CompoundTag read(DataInput dataInput) throws IOException {
		return read(dataInput, NbtAccounter.unlimitedHeap());
	}

	public static CompoundTag read(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
		Tag tag = readUnnamedTag(dataInput, nbtAccounter);
		if (tag instanceof CompoundTag) {
			return (CompoundTag)tag;
		} else {
			throw new IOException("Root tag must be a named compound tag");
		}
	}

	public static void write(CompoundTag compoundTag, DataOutput dataOutput) throws IOException {
		writeUnnamedTagWithFallback(compoundTag, dataOutput);
	}

	public static void parse(DataInput dataInput, StreamTagVisitor streamTagVisitor, NbtAccounter nbtAccounter) throws IOException {
		TagType<?> tagType = TagTypes.getType(dataInput.readByte());
		if (tagType == EndTag.TYPE) {
			if (streamTagVisitor.visitRootEntry(EndTag.TYPE) == StreamTagVisitor.ValueResult.CONTINUE) {
				streamTagVisitor.visitEnd();
			}
		} else {
			switch (streamTagVisitor.visitRootEntry(tagType)) {
				case HALT:
				default:
					break;
				case BREAK:
					StringTag.skipString(dataInput);
					tagType.skip(dataInput, nbtAccounter);
					break;
				case CONTINUE:
					StringTag.skipString(dataInput);
					tagType.parse(dataInput, streamTagVisitor, nbtAccounter);
			}
		}
	}

	public static Tag readAnyTag(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
		byte b = dataInput.readByte();
		return b == 0 ? EndTag.INSTANCE : readTagSafe(dataInput, nbtAccounter, b);
	}

	public static void writeAnyTag(Tag tag, DataOutput dataOutput) throws IOException {
		dataOutput.writeByte(tag.getId());
		if (tag.getId() != 0) {
			tag.write(dataOutput);
		}
	}

	public static void writeUnnamedTag(Tag tag, DataOutput dataOutput) throws IOException {
		dataOutput.writeByte(tag.getId());
		if (tag.getId() != 0) {
			dataOutput.writeUTF("");
			tag.write(dataOutput);
		}
	}

	public static void writeUnnamedTagWithFallback(Tag tag, DataOutput dataOutput) throws IOException {
		writeUnnamedTag(tag, new NbtIo.StringFallbackDataOutput(dataOutput));
	}

	@VisibleForTesting
	public static Tag readUnnamedTag(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
		byte b = dataInput.readByte();
		if (b == 0) {
			return EndTag.INSTANCE;
		}

		StringTag.skipString(dataInput);
		return readTagSafe(dataInput, nbtAccounter, b);
	}

	private static Tag readTagSafe(DataInput dataInput, NbtAccounter nbtAccounter, byte b) {
		try {
			return TagTypes.getType(b).load(dataInput, nbtAccounter);
		} catch (IOException iOException) {
			CrashReport crashReport = CrashReport.forThrowable(iOException, "Loading NBT data");
			CrashReportCategory crashReportCategory = crashReport.addCategory("NBT Tag");
			crashReportCategory.setDetail("Tag type", b);
			throw new ReportedNbtException(crashReport);
		}
	}

	public static class StringFallbackDataOutput extends DelegateDataOutput {
		public StringFallbackDataOutput(DataOutput dataOutput) {
			super(dataOutput);
		}

		@Override
		public void writeUTF(String string) throws IOException {
			try {
				super.writeUTF(string);
			} catch (UTFDataFormatException uTFDataFormatException) {
				Util.logAndPauseIfInIde("Failed to write NBT String", uTFDataFormatException);
				super.writeUTF("");
			}
		}
	}
}
