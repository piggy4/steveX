package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public record ShortTag(short value) implements NumericTag {
	private static final int SELF_SIZE_IN_BYTES = 10;
	public static final TagType<ShortTag> TYPE = new TagType.StaticSize<ShortTag>() {
		public ShortTag load(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
			return ShortTag.valueOf(readAccounted(dataInput, nbtAccounter));
		}

		@Override
		public StreamTagVisitor.ValueResult parse(DataInput dataInput, StreamTagVisitor streamTagVisitor, NbtAccounter nbtAccounter) throws IOException {
			return streamTagVisitor.visit(readAccounted(dataInput, nbtAccounter));
		}

		private static short readAccounted(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
			nbtAccounter.accountBytes(10L);
			return dataInput.readShort();
		}

		@Override
		public int size() {
			return 2;
		}

		@Override
		public String getName() {
			return "SHORT";
		}

		@Override
		public String getPrettyName() {
			return "TAG_Short";
		}
	};

	@Deprecated(forRemoval = true)
	public ShortTag {
	}

	public static ShortTag valueOf(short s) {
		return s >= -128 && s <= 1024 ? ShortTag.Cache.cache[s - -128] : new ShortTag(s);
	}

	@Override
	public void write(DataOutput dataOutput) throws IOException {
		dataOutput.writeShort(this.value);
	}

	@Override
	public int sizeInBytes() {
		return 10;
	}

	@Override
	public byte getId() {
		return 2;
	}

	@Override
	public TagType<ShortTag> getType() {
		return TYPE;
	}

	public ShortTag copy() {
		return this;
	}

	@Override
	public void accept(TagVisitor tagVisitor) {
		tagVisitor.visitShort(this);
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
		return (byte)(this.value & 0xFF);
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
		stringTagVisitor.visitShort(this);
		return stringTagVisitor.build();
	}

	static class Cache {
		private static final int HIGH = 1024;
		private static final int LOW = -128;
		static final ShortTag[] cache = new ShortTag[1153];

		private Cache() {
		}

		static {
			for (int i = 0; i < cache.length; i++) {
				cache[i] = new ShortTag((short)(-128 + i));
			}
		}
	}
}
