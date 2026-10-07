package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public record LongTag(long value) implements NumericTag {
	private static final int SELF_SIZE_IN_BYTES = 16;
	public static final TagType<LongTag> TYPE = new TagType.StaticSize<LongTag>() {
		public LongTag load(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
			return LongTag.valueOf(readAccounted(dataInput, nbtAccounter));
		}

		@Override
		public StreamTagVisitor.ValueResult parse(DataInput dataInput, StreamTagVisitor streamTagVisitor, NbtAccounter nbtAccounter) throws IOException {
			return streamTagVisitor.visit(readAccounted(dataInput, nbtAccounter));
		}

		private static long readAccounted(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
			nbtAccounter.accountBytes(16L);
			return dataInput.readLong();
		}

		@Override
		public int size() {
			return 8;
		}

		@Override
		public String getName() {
			return "LONG";
		}

		@Override
		public String getPrettyName() {
			return "TAG_Long";
		}
	};

	@Deprecated(forRemoval = true)
	public LongTag {
	}

	public static LongTag valueOf(long l) {
		return l >= -128L && l <= 1024L ? LongTag.Cache.cache[(int)l - -128] : new LongTag(l);
	}

	@Override
	public void write(DataOutput dataOutput) throws IOException {
		dataOutput.writeLong(this.value);
	}

	@Override
	public int sizeInBytes() {
		return 16;
	}

	@Override
	public byte getId() {
		return 4;
	}

	@Override
	public TagType<LongTag> getType() {
		return TYPE;
	}

	public LongTag copy() {
		return this;
	}

	@Override
	public void accept(TagVisitor tagVisitor) {
		tagVisitor.visitLong(this);
	}

	@Override
	public long longValue() {
		return this.value;
	}

	@Override
	public int intValue() {
		return (int)(this.value & -1L);
	}

	@Override
	public short shortValue() {
		return (short)(this.value & 65535L);
	}

	@Override
	public byte byteValue() {
		return (byte)(this.value & 255L);
	}

	@Override
	public double doubleValue() {
		return this.value;
	}

	@Override
	public float floatValue() {
		return (float)this.value;
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
		stringTagVisitor.visitLong(this);
		return stringTagVisitor.build();
	}

	static class Cache {
		private static final int HIGH = 1024;
		private static final int LOW = -128;
		static final LongTag[] cache = new LongTag[1153];

		private Cache() {
		}

		static {
			for (int i = 0; i < cache.length; i++) {
				cache[i] = new LongTag(-128 + i);
			}
		}
	}
}
