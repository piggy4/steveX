package net.minecraft.util;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HexFormat;

public record PngInfo(int width, int height) {
	private static final HexFormat FORMAT = HexFormat.of().withUpperCase().withPrefix("0x");
	private static final long PNG_HEADER = -8552249625308161526L;
	private static final int IHDR_TYPE = 1229472850;
	private static final int IHDR_SIZE = 13;

	public static PngInfo fromStream(InputStream inputStream) throws IOException {
		DataInputStream dataInputStream = new DataInputStream(inputStream);
		long l = dataInputStream.readLong();
		if (l != -8552249625308161526L) {
			throw new IOException("Bad PNG Signature: " + FORMAT.toHexDigits(l));
		}

		int i = dataInputStream.readInt();
		if (i != 13) {
			throw new IOException("Bad length for IHDR chunk: " + i);
		}

		int j = dataInputStream.readInt();
		if (j != 1229472850) {
			throw new IOException("Bad type for IHDR chunk: " + FORMAT.toHexDigits(j));
		}

		int k = dataInputStream.readInt();
		int m = dataInputStream.readInt();
		return new PngInfo(k, m);
	}

	public static PngInfo fromBytes(byte[] bs) throws IOException {
		return fromStream(new ByteArrayInputStream(bs));
	}

	public static void validateHeader(ByteBuffer byteBuffer) throws IOException {
		ByteOrder byteOrder = byteBuffer.order();
		byteBuffer.order(ByteOrder.BIG_ENDIAN);
		if (byteBuffer.limit() < 16) {
			throw new IOException("PNG header missing");
		}

		if (byteBuffer.getLong(0) != -8552249625308161526L) {
			throw new IOException("Bad PNG Signature");
		}

		if (byteBuffer.getInt(8) != 13) {
			throw new IOException("Bad length for IHDR chunk!");
		}

		if (byteBuffer.getInt(12) != 1229472850) {
			throw new IOException("Bad type for IHDR chunk!");
		}

		byteBuffer.order(byteOrder);
	}
}
