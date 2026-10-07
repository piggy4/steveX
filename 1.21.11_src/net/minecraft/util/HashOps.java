package net.minecraft.util;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.ListBuilder;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.RecordBuilder.AbstractUniversalBuilder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class HashOps implements DynamicOps<HashCode> {
	private static final byte TAG_EMPTY = 1;
	private static final byte TAG_MAP_START = 2;
	private static final byte TAG_MAP_END = 3;
	private static final byte TAG_LIST_START = 4;
	private static final byte TAG_LIST_END = 5;
	private static final byte TAG_BYTE = 6;
	private static final byte TAG_SHORT = 7;
	private static final byte TAG_INT = 8;
	private static final byte TAG_LONG = 9;
	private static final byte TAG_FLOAT = 10;
	private static final byte TAG_DOUBLE = 11;
	private static final byte TAG_STRING = 12;
	private static final byte TAG_BOOLEAN = 13;
	private static final byte TAG_BYTE_ARRAY_START = 14;
	private static final byte TAG_BYTE_ARRAY_END = 15;
	private static final byte TAG_INT_ARRAY_START = 16;
	private static final byte TAG_INT_ARRAY_END = 17;
	private static final byte TAG_LONG_ARRAY_START = 18;
	private static final byte TAG_LONG_ARRAY_END = 19;
	private static final byte[] EMPTY_PAYLOAD = new byte[]{1};
	private static final byte[] FALSE_PAYLOAD = new byte[]{13, 0};
	private static final byte[] TRUE_PAYLOAD = new byte[]{13, 1};
	public static final byte[] EMPTY_MAP_PAYLOAD = new byte[]{2, 3};
	public static final byte[] EMPTY_LIST_PAYLOAD = new byte[]{4, 5};
	private static final DataResult<Object> UNSUPPORTED_OPERATION_ERROR = DataResult.error(() -> "Unsupported operation");
	private static final Comparator<HashCode> HASH_COMPARATOR = Comparator.comparingLong(HashCode::padToLong);
	private static final Comparator<Entry<HashCode, HashCode>> MAP_ENTRY_ORDER = Entry.<HashCode, HashCode>comparingByKey(HASH_COMPARATOR)
		.thenComparing(Entry.comparingByValue(HASH_COMPARATOR));
	private static final Comparator<Pair<HashCode, HashCode>> MAPLIKE_ENTRY_ORDER = Comparator.comparing(Pair::getFirst, HASH_COMPARATOR)
		.thenComparing(Pair::getSecond, HASH_COMPARATOR);
	public static final HashOps CRC32C_INSTANCE = new HashOps(Hashing.crc32c());
	final HashFunction hashFunction;
	final HashCode empty;
	private final HashCode emptyMap;
	private final HashCode emptyList;
	private final HashCode trueHash;
	private final HashCode falseHash;

	public HashOps(HashFunction hashFunction) {
		this.hashFunction = hashFunction;
		this.empty = hashFunction.hashBytes(EMPTY_PAYLOAD);
		this.emptyMap = hashFunction.hashBytes(EMPTY_MAP_PAYLOAD);
		this.emptyList = hashFunction.hashBytes(EMPTY_LIST_PAYLOAD);
		this.falseHash = hashFunction.hashBytes(FALSE_PAYLOAD);
		this.trueHash = hashFunction.hashBytes(TRUE_PAYLOAD);
	}

	public HashCode empty() {
		return this.empty;
	}

	public HashCode emptyMap() {
		return this.emptyMap;
	}

	public HashCode emptyList() {
		return this.emptyList;
	}

	public HashCode createNumeric(Number number) {
		return switch (number) {
			case Byte byte_ -> this.createByte(byte_);
			case Short short_ -> this.createShort(short_);
			case Integer integer -> this.createInt(integer);
			case Long long_ -> this.createLong(long_);
			case Double double_ -> this.createDouble(double_);
			case Float float_ -> this.createFloat(float_);
			default -> this.createDouble(number.doubleValue());
		};
	}

	public HashCode createByte(byte b) {
		return this.hashFunction.newHasher(2).putByte((byte)6).putByte(b).hash();
	}

	public HashCode createShort(short s) {
		return this.hashFunction.newHasher(3).putByte((byte)7).putShort(s).hash();
	}

	public HashCode createInt(int i) {
		return this.hashFunction.newHasher(5).putByte((byte)8).putInt(i).hash();
	}

	public HashCode createLong(long l) {
		return this.hashFunction.newHasher(9).putByte((byte)9).putLong(l).hash();
	}

	public HashCode createFloat(float f) {
		return this.hashFunction.newHasher(5).putByte((byte)10).putFloat(f).hash();
	}

	public HashCode createDouble(double d) {
		return this.hashFunction.newHasher(9).putByte((byte)11).putDouble(d).hash();
	}

	public HashCode createString(String string) {
		return this.hashFunction.newHasher().putByte((byte)12).putInt(string.length()).putUnencodedChars(string).hash();
	}

	public HashCode createBoolean(boolean bl) {
		return bl ? this.trueHash : this.falseHash;
	}

	private static Hasher hashMap(Hasher hasher, Map<HashCode, HashCode> map) {
		hasher.putByte((byte)2);
		map.entrySet().stream().sorted(MAP_ENTRY_ORDER).forEach(entry -> hasher.putBytes(entry.getKey().asBytes()).putBytes(entry.getValue().asBytes()));
		hasher.putByte((byte)3);
		return hasher;
	}

	static Hasher hashMap(Hasher hasher, Stream<Pair<HashCode, HashCode>> stream) {
		hasher.putByte((byte)2);
		stream.sorted(MAPLIKE_ENTRY_ORDER).forEach(pair -> hasher.putBytes(pair.getFirst().asBytes()).putBytes(pair.getSecond().asBytes()));
		hasher.putByte((byte)3);
		return hasher;
	}

	public HashCode createMap(Stream<Pair<HashCode, HashCode>> stream) {
		return hashMap(this.hashFunction.newHasher(), stream).hash();
	}

	public HashCode createMap(Map<HashCode, HashCode> map) {
		return hashMap(this.hashFunction.newHasher(), map).hash();
	}

	public HashCode createList(Stream<HashCode> stream) {
		Hasher hasher = this.hashFunction.newHasher();
		hasher.putByte((byte)4);
		stream.forEach(hashCode -> hasher.putBytes(hashCode.asBytes()));
		hasher.putByte((byte)5);
		return hasher.hash();
	}

	public HashCode createByteList(ByteBuffer byteBuffer) {
		Hasher hasher = this.hashFunction.newHasher();
		hasher.putByte((byte)14);
		hasher.putBytes(byteBuffer);
		hasher.putByte((byte)15);
		return hasher.hash();
	}

	public HashCode createIntList(IntStream intStream) {
		Hasher hasher = this.hashFunction.newHasher();
		hasher.putByte((byte)16);
		intStream.forEach(hasher::putInt);
		hasher.putByte((byte)17);
		return hasher.hash();
	}

	public HashCode createLongList(LongStream longStream) {
		Hasher hasher = this.hashFunction.newHasher();
		hasher.putByte((byte)18);
		longStream.forEach(hasher::putLong);
		hasher.putByte((byte)19);
		return hasher.hash();
	}

	public HashCode remove(HashCode hashCode, String string) {
		return hashCode;
	}

	@Override
	public RecordBuilder<HashCode> mapBuilder() {
		return new HashOps.MapHashBuilder();
	}

	@Override
	public ListBuilder<HashCode> listBuilder() {
		return new HashOps.ListHashBuilder();
	}

	@Override
	public String toString() {
		return "Hash " + this.hashFunction;
	}

	public <U> U convertTo(DynamicOps<U> dynamicOps, HashCode hashCode) {
		throw new UnsupportedOperationException("Can't convert from this type");
	}

	public Number getNumberValue(HashCode hashCode, Number number) {
		return number;
	}

	public HashCode set(HashCode hashCode, String string, HashCode hashCode2) {
		return hashCode;
	}

	public HashCode update(HashCode hashCode, String string, Function<HashCode, HashCode> function) {
		return hashCode;
	}

	public HashCode updateGeneric(HashCode hashCode, HashCode hashCode2, Function<HashCode, HashCode> function) {
		return hashCode;
	}

	private static <T> DataResult<T> unsupported() {
		return (DataResult<T>)UNSUPPORTED_OPERATION_ERROR;
	}

	public DataResult<HashCode> get(HashCode hashCode, String string) {
		return unsupported();
	}

	public DataResult<HashCode> getGeneric(HashCode hashCode, HashCode hashCode2) {
		return unsupported();
	}

	public DataResult<Number> getNumberValue(HashCode hashCode) {
		return unsupported();
	}

	public DataResult<Boolean> getBooleanValue(HashCode hashCode) {
		return unsupported();
	}

	public DataResult<String> getStringValue(HashCode hashCode) {
		return unsupported();
	}

	boolean isEmpty(HashCode hashCode) {
		return hashCode.equals(this.empty);
	}

	public DataResult<HashCode> mergeToList(HashCode hashCode, HashCode hashCode2) {
		return this.isEmpty(hashCode) ? DataResult.success(this.createList(Stream.of(hashCode2))) : unsupported();
	}

	public DataResult<HashCode> mergeToList(HashCode hashCode, List<HashCode> list) {
		return this.isEmpty(hashCode) ? DataResult.success(this.createList(list.stream())) : unsupported();
	}

	public DataResult<HashCode> mergeToMap(HashCode hashCode, HashCode hashCode2, HashCode hashCode3) {
		return this.isEmpty(hashCode) ? DataResult.success(this.createMap(Map.of(hashCode2, hashCode3))) : unsupported();
	}

	public DataResult<HashCode> mergeToMap(HashCode hashCode, Map<HashCode, HashCode> map) {
		return this.isEmpty(hashCode) ? DataResult.success(this.createMap(map)) : unsupported();
	}

	public DataResult<HashCode> mergeToMap(HashCode hashCode, MapLike<HashCode> mapLike) {
		return this.isEmpty(hashCode) ? DataResult.success(this.createMap(mapLike.entries())) : unsupported();
	}

	public DataResult<Stream<Pair<HashCode, HashCode>>> getMapValues(HashCode hashCode) {
		return unsupported();
	}

	public DataResult<Consumer<BiConsumer<HashCode, HashCode>>> getMapEntries(HashCode hashCode) {
		return unsupported();
	}

	public DataResult<Stream<HashCode>> getStream(HashCode hashCode) {
		return unsupported();
	}

	public DataResult<Consumer<Consumer<HashCode>>> getList(HashCode hashCode) {
		return unsupported();
	}

	public DataResult<MapLike<HashCode>> getMap(HashCode hashCode) {
		return unsupported();
	}

	public DataResult<ByteBuffer> getByteBuffer(HashCode hashCode) {
		return unsupported();
	}

	public DataResult<IntStream> getIntStream(HashCode hashCode) {
		return unsupported();
	}

	public DataResult<LongStream> getLongStream(HashCode hashCode) {
		return unsupported();
	}

	class ListHashBuilder extends AbstractListBuilder<HashCode, Hasher> {
		public ListHashBuilder() {
			super(HashOps.this);
		}

		protected Hasher initBuilder() {
			return HashOps.this.hashFunction.newHasher().putByte((byte)4);
		}

		protected Hasher append(Hasher hasher, HashCode hashCode) {
			return hasher.putBytes(hashCode.asBytes());
		}

		protected DataResult<HashCode> build(Hasher hasher, HashCode hashCode) {
			assert hashCode.equals(HashOps.this.empty);
			hasher.putByte((byte)5);
			return DataResult.success(hasher.hash());
		}
	}

	final class MapHashBuilder extends AbstractUniversalBuilder<HashCode, List<Pair<HashCode, HashCode>>> {
		public MapHashBuilder() {
			super(HashOps.this);
		}

		protected List<Pair<HashCode, HashCode>> initBuilder() {
			return new ArrayList<>();
		}

		protected List<Pair<HashCode, HashCode>> append(HashCode hashCode, HashCode hashCode2, List<Pair<HashCode, HashCode>> list) {
			list.add(Pair.of(hashCode, hashCode2));
			return list;
		}

		protected DataResult<HashCode> build(List<Pair<HashCode, HashCode>> list, HashCode hashCode) {
			assert HashOps.this.isEmpty(hashCode);
			return DataResult.success(HashOps.hashMap(HashOps.this.hashFunction.newHasher(), list.stream()).hash());
		}
	}
}
