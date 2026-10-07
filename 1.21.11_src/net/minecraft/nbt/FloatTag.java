package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import net.minecraft.util.Mth;

public record FloatTag(float value) implements NumericTag {
	private static final int SELF_SIZE_IN_BYTES = 12;
	public static final FloatTag ZERO = new FloatTag(0.0F);
	public static final TagType<FloatTag> TYPE = new TagType.StaticSize<FloatTag>() {
		public FloatTag load(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
			return FloatTag.valueOf(readAccounted(dataInput, nbtAccounter));
		}

		@Override
		public StreamTagVisitor.ValueResult parse(DataInput dataInput, StreamTagVisitor streamTagVisitor, NbtAccounter nbtAccounter) throws IOException {
			return streamTagVisitor.visit(readAccounted(dataInput, nbtAccounter));
		}

		private static float readAccounted(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
			nbtAccounter.accountBytes(12L);
			return dataInput.readFloat();
		}

		@Override
		public int size() {
			return 4;
		}

		@Override
		public String getName() {
			return "FLOAT";
		}

		@Override
		public String getPrettyName() {
			return "TAG_Float";
		}
	};

	@Deprecated(forRemoval = true)
	public FloatTag {
	}

	public static FloatTag valueOf(float f) {
		return f == 0.0F ? ZERO : new FloatTag(f);
	}

	@Override
	public void write(DataOutput dataOutput) throws IOException {
		dataOutput.writeFloat(this.value);
	}

	@Override
	public int sizeInBytes() {
		return 12;
	}

	@Override
	public byte getId() {
		return 5;
	}

	@Override
	public TagType<FloatTag> getType() {
		return TYPE;
	}

	public FloatTag copy() {
		return this;
	}

	@Override
	public void accept(TagVisitor tagVisitor) {
		tagVisitor.visitFloat(this);
	}

	@Override
	public long longValue() {
		return (long)this.value;
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
		stringTagVisitor.visitFloat(this);
		return stringTagVisitor.build();
	}
}
