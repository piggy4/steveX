package net.minecraft.nbt;

import com.google.common.annotations.VisibleForTesting;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public final class ListTag extends AbstractList<Tag> implements CollectionTag {
	private static final String WRAPPER_MARKER = "";
	private static final int SELF_SIZE_IN_BYTES = 36;
	public static final TagType<ListTag> TYPE = new TagType.VariableSize<ListTag>() {
		public ListTag load(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
			nbtAccounter.pushDepth();

			try {
				return loadList(dataInput, nbtAccounter);
			} finally {
				nbtAccounter.popDepth();
			}
		}

		private static ListTag loadList(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
			nbtAccounter.accountBytes(36L);
			byte b = dataInput.readByte();
			int i = readListCount(dataInput);
			if (b == 0 && i > 0) {
				throw new NbtFormatException("Missing type on ListTag");
			}

			nbtAccounter.accountBytes(4L, i);
			TagType<?> tagType = TagTypes.getType(b);
			ListTag listTag = new ListTag(new ArrayList<>(i));

			for (int j = 0; j < i; j++) {
				listTag.addAndUnwrap(tagType.load(dataInput, nbtAccounter));
			}

			return listTag;
		}

		@Override
		public StreamTagVisitor.ValueResult parse(DataInput dataInput, StreamTagVisitor streamTagVisitor, NbtAccounter nbtAccounter) throws IOException {
			nbtAccounter.pushDepth();

			try {
				return parseList(dataInput, streamTagVisitor, nbtAccounter);
			} finally {
				nbtAccounter.popDepth();
			}
		}

		private static StreamTagVisitor.ValueResult parseList(DataInput dataInput, StreamTagVisitor streamTagVisitor, NbtAccounter nbtAccounter) throws IOException {
			nbtAccounter.accountBytes(36L);
			TagType<?> tagType = TagTypes.getType(dataInput.readByte());
			int i = readListCount(dataInput);
			switch (streamTagVisitor.visitList(tagType, i)) {
				case HALT:
					return StreamTagVisitor.ValueResult.HALT;
				case BREAK:
					tagType.skip(dataInput, i, nbtAccounter);
					return streamTagVisitor.visitContainerEnd();
				default:
					nbtAccounter.accountBytes(4L, i);
					int j = 0;

					while (true) {
						label41: {
							if (j < i) {
								switch (streamTagVisitor.visitElement(tagType, j)) {
									case HALT:
										return StreamTagVisitor.ValueResult.HALT;
									case BREAK:
										tagType.skip(dataInput, nbtAccounter);
										break;
									case SKIP:
										tagType.skip(dataInput, nbtAccounter);
										break label41;
									default:
										switch (tagType.parse(dataInput, streamTagVisitor, nbtAccounter)) {
											case HALT:
												return StreamTagVisitor.ValueResult.HALT;
											case BREAK:
												break;
											default:
												break label41;
										}
								}
							}

							int k = i - 1 - j;
							if (k > 0) {
								tagType.skip(dataInput, k, nbtAccounter);
							}

							return streamTagVisitor.visitContainerEnd();
						}

						j++;
					}
			}
		}

		private static int readListCount(DataInput dataInput) throws IOException {
			int i = dataInput.readInt();
			if (i < 0) {
				throw new NbtFormatException("ListTag length cannot be negative: " + i);
			} else {
				return i;
			}
		}

		@Override
		public void skip(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
			nbtAccounter.pushDepth();

			try {
				TagType<?> tagType = TagTypes.getType(dataInput.readByte());
				int i = dataInput.readInt();
				tagType.skip(dataInput, i, nbtAccounter);
			} finally {
				nbtAccounter.popDepth();
			}
		}

		@Override
		public String getName() {
			return "LIST";
		}

		@Override
		public String getPrettyName() {
			return "TAG_List";
		}
	};
	private final List<Tag> list;

	public ListTag() {
		this(new ArrayList<>());
	}

	ListTag(List<Tag> list) {
		this.list = list;
	}

	private static Tag tryUnwrap(CompoundTag compoundTag) {
		if (compoundTag.size() == 1) {
			Tag tag = compoundTag.get("");
			if (tag != null) {
				return tag;
			}
		}

		return compoundTag;
	}

	private static boolean isWrapper(CompoundTag compoundTag) {
		return compoundTag.size() == 1 && compoundTag.contains("");
	}

	private static Tag wrapIfNeeded(byte b, Tag tag) {
		if (b != 10) {
			return tag;
		} else {
			return tag instanceof CompoundTag compoundTag && !isWrapper(compoundTag) ? compoundTag : wrapElement(tag);
		}
	}

	private static CompoundTag wrapElement(Tag tag) {
		return new CompoundTag(Map.of("", tag));
	}

	@Override
	public void write(DataOutput dataOutput) throws IOException {
		byte b = this.identifyRawElementType();
		dataOutput.writeByte(b);
		dataOutput.writeInt(this.list.size());

		for (Tag tag : this.list) {
			wrapIfNeeded(b, tag).write(dataOutput);
		}
	}

	@VisibleForTesting
	byte identifyRawElementType() {
		byte b = 0;

		for (Tag tag : this.list) {
			byte c = tag.getId();
			if (b == 0) {
				b = c;
			} else if (b != c) {
				return 10;
			}
		}

		return b;
	}

	public void addAndUnwrap(Tag tag) {
		if (tag instanceof CompoundTag compoundTag) {
			this.add(tryUnwrap(compoundTag));
		} else {
			this.add(tag);
		}
	}

	@Override
	public int sizeInBytes() {
		int i = 36;
		i += 4 * this.list.size();

		for (Tag tag : this.list) {
			i += tag.sizeInBytes();
		}

		return i;
	}

	@Override
	public byte getId() {
		return 9;
	}

	@Override
	public TagType<ListTag> getType() {
		return TYPE;
	}

	@Override
	public String toString() {
		StringTagVisitor stringTagVisitor = new StringTagVisitor();
		stringTagVisitor.visitList(this);
		return stringTagVisitor.build();
	}

	@Override
	public Tag remove(int i) {
		return this.list.remove(i);
	}

	@Override
	public boolean isEmpty() {
		return this.list.isEmpty();
	}

	public Optional<CompoundTag> getCompound(int i) {
		return this.getNullable(i) instanceof CompoundTag compoundTag ? Optional.of(compoundTag) : Optional.empty();
	}

	public CompoundTag getCompoundOrEmpty(int i) {
		return this.getCompound(i).orElseGet(CompoundTag::new);
	}

	public Optional<ListTag> getList(int i) {
		return this.getNullable(i) instanceof ListTag listTag ? Optional.of(listTag) : Optional.empty();
	}

	public ListTag getListOrEmpty(int i) {
		return this.getList(i).orElseGet(ListTag::new);
	}

	public Optional<Short> getShort(int i) {
		return this.getOptional(i).flatMap(Tag::asShort);
	}

	public short getShortOr(int i, short s) {
		return this.getNullable(i) instanceof NumericTag numericTag ? numericTag.shortValue() : s;
	}

	public Optional<Integer> getInt(int i) {
		return this.getOptional(i).flatMap(Tag::asInt);
	}

	public int getIntOr(int i, int j) {
		return this.getNullable(i) instanceof NumericTag numericTag ? numericTag.intValue() : j;
	}

	public Optional<int[]> getIntArray(int i) {
		return this.getNullable(i) instanceof IntArrayTag intArrayTag ? Optional.of(intArrayTag.getAsIntArray()) : Optional.empty();
	}

	public Optional<long[]> getLongArray(int i) {
		return this.getNullable(i) instanceof LongArrayTag longArrayTag ? Optional.of(longArrayTag.getAsLongArray()) : Optional.empty();
	}

	public Optional<Double> getDouble(int i) {
		return this.getOptional(i).flatMap(Tag::asDouble);
	}

	public double getDoubleOr(int i, double d) {
		return this.getNullable(i) instanceof NumericTag numericTag ? numericTag.doubleValue() : d;
	}

	public Optional<Float> getFloat(int i) {
		return this.getOptional(i).flatMap(Tag::asFloat);
	}

	public float getFloatOr(int i, float f) {
		return this.getNullable(i) instanceof NumericTag numericTag ? numericTag.floatValue() : f;
	}

	public Optional<String> getString(int i) {
		return this.getOptional(i).flatMap(Tag::asString);
	}

	public String getStringOr(int i, String string) {
		return this.getNullable(i) instanceof StringTag(String var8) ? var8 : string;
	}

	private @Nullable Tag getNullable(int i) {
		return i >= 0 && i < this.list.size() ? this.list.get(i) : null;
	}

	private Optional<Tag> getOptional(int i) {
		return Optional.ofNullable(this.getNullable(i));
	}

	@Override
	public int size() {
		return this.list.size();
	}

	@Override
	public Tag get(int i) {
		return this.list.get(i);
	}

	public Tag set(int i, Tag tag) {
		return this.list.set(i, tag);
	}

	public void add(int i, Tag tag) {
		this.list.add(i, tag);
	}

	@Override
	public boolean setTag(int i, Tag tag) {
		this.list.set(i, tag);
		return true;
	}

	@Override
	public boolean addTag(int i, Tag tag) {
		this.list.add(i, tag);
		return true;
	}

	public ListTag copy() {
		List<Tag> list = new ArrayList<>(this.list.size());

		for (Tag tag : this.list) {
			list.add(tag.copy());
		}

		return new ListTag(list);
	}

	@Override
	public Optional<ListTag> asList() {
		return Optional.of(this);
	}

	@Override
	public boolean equals(Object object) {
		return this == object ? true : object instanceof ListTag && Objects.equals(this.list, ((ListTag)object).list);
	}

	@Override
	public int hashCode() {
		return this.list.hashCode();
	}

	@Override
	public Stream<Tag> stream() {
		return super.stream();
	}

	public Stream<CompoundTag> compoundStream() {
		return this.stream().mapMulti((tag, consumer) -> {
			if (tag instanceof CompoundTag compoundTag) {
				consumer.accept(compoundTag);
			}
		});
	}

	@Override
	public void accept(TagVisitor tagVisitor) {
		tagVisitor.visitList(this);
	}

	@Override
	public void clear() {
		this.list.clear();
	}

	@Override
	public StreamTagVisitor.ValueResult accept(StreamTagVisitor streamTagVisitor) {
		byte b = this.identifyRawElementType();
		switch (streamTagVisitor.visitList(TagTypes.getType(b), this.list.size())) {
			case HALT:
				return StreamTagVisitor.ValueResult.HALT;
			case BREAK:
				return streamTagVisitor.visitContainerEnd();
			default:
				int i = 0;

				while (i < this.list.size()) {
					Tag tag = wrapIfNeeded(b, this.list.get(i));
					switch (streamTagVisitor.visitElement(tag.getType(), i)) {
						case HALT:
							return StreamTagVisitor.ValueResult.HALT;
						case BREAK:
							return streamTagVisitor.visitContainerEnd();
						default:
							switch (tag.accept(streamTagVisitor)) {
								case HALT:
									return StreamTagVisitor.ValueResult.HALT;
								case BREAK:
									return streamTagVisitor.visitContainerEnd();
							}
						case SKIP:
							i++;
					}
				}

				return streamTagVisitor.visitContainerEnd();
		}
	}
}
