package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import org.apache.commons.lang3.ArrayUtils;

public final class ByteArrayTag implements CollectionTag {
	private static final int SELF_SIZE_IN_BYTES = 24;
	public static final TagType<ByteArrayTag> TYPE = new TagType.VariableSize<ByteArrayTag>() {
		public ByteArrayTag load(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
			return new ByteArrayTag(readAccounted(dataInput, nbtAccounter));
		}

		@Override
		public StreamTagVisitor.ValueResult parse(DataInput dataInput, StreamTagVisitor streamTagVisitor, NbtAccounter nbtAccounter) throws IOException {
			return streamTagVisitor.visit(readAccounted(dataInput, nbtAccounter));
		}

		private static byte[] readAccounted(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
			nbtAccounter.accountBytes(24L);
			int i = dataInput.readInt();
			nbtAccounter.accountBytes(1L, i);
			byte[] bs = new byte[i];
			dataInput.readFully(bs);
			return bs;
		}

		@Override
		public void skip(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
			dataInput.skipBytes(dataInput.readInt() * 1);
		}

		@Override
		public String getName() {
			return "BYTE[]";
		}

		@Override
		public String getPrettyName() {
			return "TAG_Byte_Array";
		}
	};
	private byte[] data;

	public ByteArrayTag(byte[] bs) {
		this.data = bs;
	}

	@Override
	public void write(DataOutput dataOutput) throws IOException {
		dataOutput.writeInt(this.data.length);
		dataOutput.write(this.data);
	}

	@Override
	public int sizeInBytes() {
		return 24 + 1 * this.data.length;
	}

	@Override
	public byte getId() {
		return 7;
	}

	@Override
	public TagType<ByteArrayTag> getType() {
		return TYPE;
	}

	@Override
	public String toString() {
		StringTagVisitor stringTagVisitor = new StringTagVisitor();
		stringTagVisitor.visitByteArray(this);
		return stringTagVisitor.build();
	}

	@Override
	public Tag copy() {
		byte[] bs = new byte[this.data.length];
		System.arraycopy(this.data, 0, bs, 0, this.data.length);
		return new ByteArrayTag(bs);
	}

	@Override
	public boolean equals(Object object) {
		return this == object ? true : object instanceof ByteArrayTag && Arrays.equals(this.data, ((ByteArrayTag)object).data);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.data);
	}

	@Override
	public void accept(TagVisitor tagVisitor) {
		tagVisitor.visitByteArray(this);
	}

	public byte[] getAsByteArray() {
		return this.data;
	}

	@Override
	public int size() {
		return this.data.length;
	}

	public ByteTag get(int i) {
		return ByteTag.valueOf(this.data[i]);
	}

	@Override
	public boolean setTag(int i, Tag tag) {
		if (tag instanceof NumericTag numericTag) {
			this.data[i] = numericTag.byteValue();
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean addTag(int i, Tag tag) {
		if (tag instanceof NumericTag numericTag) {
			this.data = ArrayUtils.add(this.data, i, numericTag.byteValue());
			return true;
		} else {
			return false;
		}
	}

	public ByteTag remove(int i) {
		byte b = this.data[i];
		this.data = ArrayUtils.remove(this.data, i);
		return ByteTag.valueOf(b);
	}

	@Override
	public void clear() {
		this.data = new byte[0];
	}

	@Override
	public Optional<byte[]> asByteArray() {
		return Optional.of(this.data);
	}

	@Override
	public StreamTagVisitor.ValueResult accept(StreamTagVisitor streamTagVisitor) {
		return streamTagVisitor.visit(this.data);
	}
}
