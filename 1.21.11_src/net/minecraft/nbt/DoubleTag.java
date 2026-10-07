package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import net.minecraft.util.Mth;

public record DoubleTag(double value) implements NumericTag {
	private static final int SELF_SIZE_IN_BYTES = 16;
	public static final DoubleTag ZERO = new DoubleTag(0.0);
	public static final TagType<DoubleTag> TYPE = new TagType.StaticSize<DoubleTag>() {
		public DoubleTag load(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
			return DoubleTag.valueOf(readAccounted(dataInput, nbtAccounter));
		}

		@Override
		public StreamTagVisitor.ValueResult parse(DataInput dataInput, StreamTagVisitor streamTagVisitor, NbtAccounter nbtAccounter) throws IOException {
			return streamTagVisitor.visit(readAccounted(dataInput, nbtAccounter));
		}

		private static double readAccounted(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
			nbtAccounter.accountBytes(16L);
			return dataInput.readDouble();
		}

		@Override
		public int size() {
			return 8;
		}

		@Override
		public String getName() {
			return "DOUBLE";
		}

		@Override
		public String getPrettyName() {
			return "TAG_Double";
		}
	};

	@Deprecated(forRemoval = true)
	public DoubleTag {
	}

	public static DoubleTag valueOf(double d) {
		return d == 0.0 ? ZERO : new DoubleTag(d);
	}

	@Override
	public void write(DataOutput dataOutput) throws IOException {
		dataOutput.writeDouble(this.value);
	}

	@Override
	public int sizeInBytes() {
		return 16;
	}

	@Override
	public byte getId() {
		return 6;
	}

	@Override
	public TagType<DoubleTag> getType() {
		return TYPE;
	}

	public DoubleTag copy() {
		return this;
	}

	@Override
	public void accept(TagVisitor tagVisitor) {
		tagVisitor.visitDouble(this);
	}

	@Override
	public long longValue() {
		return (long)Math.floor(this.value);
	}

	@Override
	public int intValue() {
		return Mth.floor(this.value);
	}

	@Override
	public short shortValue() {
		return (short)(Mth.floor(this.value) & 65535);
	}

	@Override
	public byte byteValue() {
		return (byte)(Mth.floor(this.value) & 0xFF);
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
		stringTagVisitor.visitDouble(this);
		return stringTagVisitor.build();
	}
}
