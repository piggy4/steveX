package net.minecraft.nbt;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.RecordBuilder.AbstractStringBuilder;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class NbtOps implements DynamicOps<Tag> {
	public static final NbtOps INSTANCE = new NbtOps();

	private NbtOps() {
	}

	public Tag empty() {
		return EndTag.INSTANCE;
	}

	public Tag emptyList() {
		return new ListTag();
	}

	public Tag emptyMap() {
		return new CompoundTag();
	}

	public <U> U convertTo(DynamicOps<U> dynamicOps, Tag tag) {
		return (U)(switch (tag) {
			case EndTag endTag -> (Object)dynamicOps.empty();
			case ByteTag(byte b) -> (Object)dynamicOps.createByte(b);
			case ShortTag(short s) -> (Object)dynamicOps.createShort(s);
			case IntTag(int i) -> (Object)dynamicOps.createInt(i);
			case LongTag(long l) -> (Object)dynamicOps.createLong(l);
			case FloatTag(float f) -> (Object)dynamicOps.createFloat(f);
			case DoubleTag(double d) -> (Object)dynamicOps.createDouble(d);
			case ByteArrayTag byteArrayTag -> (Object)dynamicOps.createByteList(ByteBuffer.wrap(byteArrayTag.getAsByteArray()));
			case StringTag(String string) -> (Object)dynamicOps.createString(string);
			case ListTag listTag -> (Object)this.convertList(dynamicOps, listTag);
			case CompoundTag compoundTag -> (Object)this.convertMap(dynamicOps, compoundTag);
			case IntArrayTag intArrayTag -> (Object)dynamicOps.createIntList(Arrays.stream(intArrayTag.getAsIntArray()));
			case LongArrayTag longArrayTag -> (Object)dynamicOps.createLongList(Arrays.stream(longArrayTag.getAsLongArray()));
			default -> throw new MatchException(null, null);
		});
	}

	public DataResult<Number> getNumberValue(Tag tag) {
		return tag.asNumber().map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Not a number"));
	}

	public Tag createNumeric(Number number) {
		return DoubleTag.valueOf(number.doubleValue());
	}

	public Tag createByte(byte b) {
		return ByteTag.valueOf(b);
	}

	public Tag createShort(short s) {
		return ShortTag.valueOf(s);
	}

	public Tag createInt(int i) {
		return IntTag.valueOf(i);
	}

	public Tag createLong(long l) {
		return LongTag.valueOf(l);
	}

	public Tag createFloat(float f) {
		return FloatTag.valueOf(f);
	}

	public Tag createDouble(double d) {
		return DoubleTag.valueOf(d);
	}

	public Tag createBoolean(boolean bl) {
		return ByteTag.valueOf(bl);
	}

	public DataResult<String> getStringValue(Tag tag) {
		return tag instanceof StringTag(String string) ? DataResult.success(string) : DataResult.error(() -> "Not a string");
	}

	public Tag createString(String string) {
		return StringTag.valueOf(string);
	}

	public DataResult<Tag> mergeToList(Tag tag, Tag tag2) {
		return createCollector(tag)
			.map(listCollector -> DataResult.success(listCollector.accept(tag2).result()))
			.orElseGet(() -> DataResult.error(() -> "mergeToList called with not a list: " + tag, tag));
	}

	public DataResult<Tag> mergeToList(Tag tag, List<Tag> list) {
		return createCollector(tag)
			.map(listCollector -> DataResult.success(listCollector.acceptAll(list).result()))
			.orElseGet(() -> DataResult.error(() -> "mergeToList called with not a list: " + tag, tag));
	}

	public DataResult<Tag> mergeToMap(Tag tag, Tag tag2, Tag tag3) {
		if (!(tag instanceof CompoundTag) && !(tag instanceof EndTag)) {
			return DataResult.error(() -> "mergeToMap called with not a map: " + tag, tag);
		} else if (tag2 instanceof StringTag(String string)) {
			CompoundTag compoundTag2 = tag instanceof CompoundTag compoundTag ? compoundTag.shallowCopy() : new CompoundTag();
			compoundTag2.put(string, tag3);
			return DataResult.success(compoundTag2);
		} else {
			return DataResult.error(() -> "key is not a string: " + tag2, tag);
		}
	}

	public DataResult<Tag> mergeToMap(Tag tag, MapLike<Tag> mapLike) {
		if (!(tag instanceof CompoundTag) && !(tag instanceof EndTag)) {
			return DataResult.error(() -> "mergeToMap called with not a map: " + tag, tag);
		}

		Iterator<Pair<Tag, Tag>> iterator = mapLike.entries().iterator();
		if (!iterator.hasNext()) {
			return tag == this.empty() ? DataResult.success(this.emptyMap()) : DataResult.success(tag);
		}

		CompoundTag compoundTag2 = tag instanceof CompoundTag compoundTag ? compoundTag.shallowCopy() : new CompoundTag();
		List<Tag> list = new ArrayList<>();
		iterator.forEachRemaining(pair -> {
			Tag tagx = pair.getFirst();
			if (tagx instanceof StringTag(String string2)) {
				compoundTag2.put(string2, pair.getSecond());
			} else {
				list.add(tagx);
			}
		});
		return !list.isEmpty() ? DataResult.error(() -> "some keys are not strings: " + list, compoundTag2) : DataResult.success(compoundTag2);
	}

	public DataResult<Tag> mergeToMap(Tag tag, Map<Tag, Tag> map) {
		if (!(tag instanceof CompoundTag) && !(tag instanceof EndTag)) {
			return DataResult.error(() -> "mergeToMap called with not a map: " + tag, tag);
		}

		if (map.isEmpty()) {
			return tag == this.empty() ? DataResult.success(this.emptyMap()) : DataResult.success(tag);
		}

		CompoundTag compoundTag2 = tag instanceof CompoundTag compoundTag ? compoundTag.shallowCopy() : new CompoundTag();
		List<Tag> list = new ArrayList<>();

		for (Entry<Tag, Tag> entry : map.entrySet()) {
			Tag tag2 = entry.getKey();
			if (tag2 instanceof StringTag(String string)) {
				compoundTag2.put(string, entry.getValue());
			} else {
				list.add(tag2);
			}
		}

		return !list.isEmpty() ? DataResult.error(() -> "some keys are not strings: " + list, compoundTag2) : DataResult.success(compoundTag2);
	}

	public DataResult<Stream<Pair<Tag, Tag>>> getMapValues(Tag tag) {
		return tag instanceof CompoundTag compoundTag
			? DataResult.success(compoundTag.entrySet().stream().map(entry -> Pair.of(this.createString(entry.getKey()), entry.getValue())))
			: DataResult.error(() -> "Not a map: " + tag);
	}

	public DataResult<Consumer<BiConsumer<Tag, Tag>>> getMapEntries(Tag tag) {
		return tag instanceof CompoundTag compoundTag ? DataResult.success(biConsumer -> {
			for (Entry<String, Tag> entry : compoundTag.entrySet()) {
				biConsumer.accept(this.createString(entry.getKey()), entry.getValue());
			}
		}) : DataResult.error(() -> "Not a map: " + tag);
	}

	public DataResult<MapLike<Tag>> getMap(Tag tag) {
		return tag instanceof CompoundTag compoundTag ? DataResult.success(new MapLike<Tag>() {
			public @Nullable Tag get(Tag tag) {
				if (tag instanceof StringTag(String string)) {
					return compoundTag.get(string);
				} else {
					throw new UnsupportedOperationException("Cannot get map entry with non-string key: " + tag);
				}
			}

			public @Nullable Tag get(String string) {
				return compoundTag.get(string);
			}

			@Override
			public Stream<Pair<Tag, Tag>> entries() {
				return compoundTag.entrySet().stream().map(entry -> Pair.of(NbtOps.this.createString(entry.getKey()), entry.getValue()));
			}

			@Override
			public String toString() {
				return "MapLike[" + compoundTag + "]";
			}
		}) : DataResult.error(() -> "Not a map: " + tag);
	}

	public Tag createMap(Stream<Pair<Tag, Tag>> stream) {
		CompoundTag compoundTag = new CompoundTag();
		stream.forEach(pair -> {
			Tag tag = pair.getFirst();
			Tag tag2 = pair.getSecond();
			if (tag instanceof StringTag(String string2)) {
				compoundTag.put(string2, tag2);
			} else {
				throw new UnsupportedOperationException("Cannot create map with non-string key: " + tag);
			}
		});
		return compoundTag;
	}

	public DataResult<Stream<Tag>> getStream(Tag tag) {
		return tag instanceof CollectionTag collectionTag ? DataResult.success(collectionTag.stream()) : DataResult.error(() -> "Not a list");
	}

	public DataResult<Consumer<Consumer<Tag>>> getList(Tag tag) {
		return tag instanceof CollectionTag collectionTag ? DataResult.success(collectionTag::forEach) : DataResult.error(() -> "Not a list: " + tag);
	}

	public DataResult<ByteBuffer> getByteBuffer(Tag tag) {
		return tag instanceof ByteArrayTag byteArrayTag ? DataResult.success(ByteBuffer.wrap(byteArrayTag.getAsByteArray())) : DynamicOps.super.getByteBuffer(tag);
	}

	public Tag createByteList(ByteBuffer byteBuffer) {
		ByteBuffer byteBuffer2 = byteBuffer.duplicate().clear();
		byte[] bs = new byte[byteBuffer.capacity()];
		byteBuffer2.get(0, bs, 0, bs.length);
		return new ByteArrayTag(bs);
	}

	public DataResult<IntStream> getIntStream(Tag tag) {
		return tag instanceof IntArrayTag intArrayTag ? DataResult.success(Arrays.stream(intArrayTag.getAsIntArray())) : DynamicOps.super.getIntStream(tag);
	}

	public Tag createIntList(IntStream intStream) {
		return new IntArrayTag(intStream.toArray());
	}

	public DataResult<LongStream> getLongStream(Tag tag) {
		return tag instanceof LongArrayTag longArrayTag ? DataResult.success(Arrays.stream(longArrayTag.getAsLongArray())) : DynamicOps.super.getLongStream(tag);
	}

	public Tag createLongList(LongStream longStream) {
		return new LongArrayTag(longStream.toArray());
	}

	public Tag createList(Stream<Tag> stream) {
		return new ListTag(stream.collect(Util.toMutableList()));
	}

	public Tag remove(Tag tag, String string) {
		if (tag instanceof CompoundTag compoundTag) {
			CompoundTag compoundTag2 = compoundTag.shallowCopy();
			compoundTag2.remove(string);
			return compoundTag2;
		} else {
			return tag;
		}
	}

	@Override
	public String toString() {
		return "NBT";
	}

	@Override
	public RecordBuilder<Tag> mapBuilder() {
		return new NbtOps.NbtRecordBuilder();
	}

	private static Optional<NbtOps.ListCollector> createCollector(Tag tag) {
		if (tag instanceof EndTag) {
			return Optional.of(new NbtOps.GenericListCollector());
		}

		if (tag instanceof CollectionTag collectionTag) {
			if (collectionTag.isEmpty()) {
				return Optional.of(new NbtOps.GenericListCollector());
			}

			return switch (collectionTag) {
				case ListTag listTag -> Optional.of(new NbtOps.GenericListCollector(listTag));
				case ByteArrayTag byteArrayTag -> Optional.of(new NbtOps.ByteListCollector(byteArrayTag.getAsByteArray()));
				case IntArrayTag intArrayTag -> Optional.of(new NbtOps.IntListCollector(intArrayTag.getAsIntArray()));
				case LongArrayTag longArrayTag -> Optional.of(new NbtOps.LongListCollector(longArrayTag.getAsLongArray()));
				default -> throw new MatchException(null, null);
			};
		} else {
			return Optional.empty();
		}
	}

	static class ByteListCollector implements NbtOps.ListCollector {
		private final ByteArrayList values = new ByteArrayList();

		public ByteListCollector(byte[] bs) {
			this.values.addElements(0, bs);
		}

		@Override
		public NbtOps.ListCollector accept(Tag tag) {
			if (tag instanceof ByteTag byteTag) {
				this.values.add(byteTag.byteValue());
				return this;
			} else {
				return new NbtOps.GenericListCollector(this.values).accept(tag);
			}
		}

		@Override
		public Tag result() {
			return new ByteArrayTag(this.values.toByteArray());
		}
	}

	static class GenericListCollector implements NbtOps.ListCollector {
		private final ListTag result = new ListTag();

		GenericListCollector() {
		}

		GenericListCollector(ListTag listTag) {
			this.result.addAll(listTag);
		}

		public GenericListCollector(IntArrayList intArrayList) {
			intArrayList.forEach(i -> this.result.add(IntTag.valueOf(i)));
		}

		public GenericListCollector(ByteArrayList byteArrayList) {
			byteArrayList.forEach(b -> this.result.add(ByteTag.valueOf(b)));
		}

		public GenericListCollector(LongArrayList longArrayList) {
			longArrayList.forEach(l -> this.result.add(LongTag.valueOf(l)));
		}

		@Override
		public NbtOps.ListCollector accept(Tag tag) {
			this.result.add(tag);
			return this;
		}

		@Override
		public Tag result() {
			return this.result;
		}
	}

	static class IntListCollector implements NbtOps.ListCollector {
		private final IntArrayList values = new IntArrayList();

		public IntListCollector(int[] is) {
			this.values.addElements(0, is);
		}

		@Override
		public NbtOps.ListCollector accept(Tag tag) {
			if (tag instanceof IntTag intTag) {
				this.values.add(intTag.intValue());
				return this;
			} else {
				return new NbtOps.GenericListCollector(this.values).accept(tag);
			}
		}

		@Override
		public Tag result() {
			return new IntArrayTag(this.values.toIntArray());
		}
	}

	interface ListCollector {
		NbtOps.ListCollector accept(Tag tag);

		default NbtOps.ListCollector acceptAll(Iterable<Tag> iterable) {
			NbtOps.ListCollector listCollector = this;

			for (Tag tag : iterable) {
				listCollector = listCollector.accept(tag);
			}

			return listCollector;
		}

		default NbtOps.ListCollector acceptAll(Stream<Tag> stream) {
			return this.acceptAll(stream::iterator);
		}

		Tag result();
	}

	static class LongListCollector implements NbtOps.ListCollector {
		private final LongArrayList values = new LongArrayList();

		public LongListCollector(long[] ls) {
			this.values.addElements(0, ls);
		}

		@Override
		public NbtOps.ListCollector accept(Tag tag) {
			if (tag instanceof LongTag longTag) {
				this.values.add(longTag.longValue());
				return this;
			} else {
				return new NbtOps.GenericListCollector(this.values).accept(tag);
			}
		}

		@Override
		public Tag result() {
			return new LongArrayTag(this.values.toLongArray());
		}
	}

	class NbtRecordBuilder extends AbstractStringBuilder<Tag, CompoundTag> {
		protected NbtRecordBuilder() {
			super(NbtOps.this);
		}

		protected CompoundTag initBuilder() {
			return new CompoundTag();
		}

		protected CompoundTag append(String string, Tag tag, CompoundTag compoundTag) {
			compoundTag.put(string, tag);
			return compoundTag;
		}

		protected DataResult<Tag> build(CompoundTag compoundTag, Tag tag) {
			if (tag == null || tag == EndTag.INSTANCE) {
				return DataResult.success(compoundTag);
			} else if (!(tag instanceof CompoundTag compoundTag2)) {
				return DataResult.error(() -> "mergeToMap called with not a map: " + tag, tag);
			} else {
				CompoundTag compoundTag3 = compoundTag2.shallowCopy();

				for (Entry<String, Tag> entry : compoundTag.entrySet()) {
					compoundTag3.put(entry.getKey(), entry.getValue());
				}

				return DataResult.success(compoundTag3);
			}
		}
	}
}
