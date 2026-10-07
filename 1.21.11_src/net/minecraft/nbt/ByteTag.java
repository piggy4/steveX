package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public record ByteTag(byte value) implements NumericTag {
	private static final int SELF_SIZE_IN_BYTES = 9;
	public static final TagType<ByteTag> TYPE = new TagType.StaticSize<ByteTag>() {
		public ByteTag load(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
			return ByteTag.valueOf(readAccounted(dataInput, nbtAccounter));
		}

		@Override
		public StreamTagVisitor.ValueResult parse(DataInput dataInput, StreamTagVisitor streamTagVisitor, NbtAccounter nbtAccounter) throws IOException {
			return streamTagVisitor.visit(readAccounted(dataInput, nbtAccounter));
		}

		private static byte readAccounted(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
			nbtAccounter.accountBytes(9L);
			return dataInput.readByte();
		}

		@Override
		public int size() {
			return 1;
		}

		@Override
		public String getName() {
			return "BYTE";
		}

		@Override
		public String getPrettyName() {
			return "TAG_Byte";
		}
	};
	public static final ByteTag ZERO = valueOf((byte)0);
	public static final ByteTag ONE = valueOf((byte)1);

	@Deprecated(forRemoval = true)
	public ByteTag {
	}

	public static ByteTag valueOf(byte b) {
		return ByteTag.Cache.cache[128 + b];
	}

	public static ByteTag valueOf(boolean bl) {
		return bl ? ONE : ZERO;
	}

	@Override
	public void write(DataOutput dataOutput) throws IOException {
		dataOutput.writeByte(this.value);
	}

	@Override
	public int sizeInBytes() {
		return 9;
	}

	@Override
	public byte getId() {
		return 1;
	}

	@Override
	public TagType<ByteTag> getType() {
		return TYPE;
	}

	public ByteTag copy() {
		return this;
	}

	@Override
	public void accept(TagVisitor tagVisitor) {
		tagVisitor.visitByte(this);
	}

	@Override
	public long longValue() {
		return this.value;
	}

	@Override
	public int intValue() {
		return this.value;
	}

	@Override
	public short shortValue() {
		return this.value;
	}

	@Override
	public byte byteValue() {
		return this.value;
	}

	@Override
	public double doubleValue() {
		return this.value;
	}

	@Override
	public float floatValue() {
		return this.value;
	}

	@Override
	public Number box() {
		return this.value;
	}

	@Override
	public StreamTagVisitor.ValueResult accept(StreamTagVisitor streamTagVisitor) {
		return streamTagVisitor.visit(this.value);
	}

	@Override
	public String toString() {
		StringTagVisitor stringTagVisitor = new StringTagVisitor();
		stringTagVisitor.visitByte(this);
		return stringTagVisitor.build();
	}

	static class Cache {
		static final ByteTag[] cache = new ByteTag[256];

		private Cache() {
		}

		static {
			for (int i = 0; i < cache.length; i++) {
				cache[i] = new ByteTag((byte)(i - 128));
			}
		}
	}
}
