package net.minecraft.resources;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.ListBuilder;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public abstract class DelegatingOps<T> implements DynamicOps<T> {
	protected final DynamicOps<T> delegate;

	protected DelegatingOps(DynamicOps<T> dynamicOps) {
		this.delegate = dynamicOps;
	}

	@Override
	public T empty() {
		return this.delegate.empty();
	}

	@Override
	public T emptyMap() {
		return this.delegate.emptyMap();
	}

	@Override
	public T emptyList() {
		return this.delegate.emptyList();
	}

	@Override
	public <U> U convertTo(DynamicOps<U> dynamicOps, T object) {
		return (U)(Objects.equals(dynamicOps, this.delegate) ? object : this.delegate.convertTo(dynamicOps, object));
	}

	@Override
	public DataResult<Number> getNumberValue(T object) {
		return this.delegate.getNumberValue(object);
	}

	@Override
	public T createNumeric(Number number) {
		return this.delegate.createNumeric(number);
	}

	@Override
	public T createByte(byte b) {
		return this.delegate.createByte(b);
	}

	@Override
	public T createShort(short s) {
		return this.delegate.createShort(s);
	}

	@Override
	public T createInt(int i) {
		return this.delegate.createInt(i);
	}

	@Override
	public T createLong(long l) {
		return this.delegate.createLong(l);
	}

	@Override
	public T createFloat(float f) {
		return this.delegate.createFloat(f);
	}

	@Override
	public T createDouble(double d) {
		return this.delegate.createDouble(d);
	}

	@Override
	public DataResult<Boolean> getBooleanValue(T object) {
		return this.delegate.getBooleanValue(object);
	}

	@Override
	public T createBoolean(boolean bl) {
		return this.delegate.createBoolean(bl);
	}

	@Override
	public DataResult<String> getStringValue(T object) {
		return this.delegate.getStringValue(object);
	}

	@Override
	public T createString(String string) {
		return this.delegate.createString(string);
	}

	@Override
	public DataResult<T> mergeToList(T object, T object2) {
		return this.delegate.mergeToList(object, object2);
	}

	@Override
	public DataResult<T> mergeToList(T object, List<T> list) {
		return this.delegate.mergeToList(object, list);
	}

	@Override
	public DataResult<T> mergeToMap(T object, T object2, T object3) {
		return this.delegate.mergeToMap(object, object2, object3);
	}

	@Override
	public DataResult<T> mergeToMap(T object, MapLike<T> mapLike) {
		return this.delegate.mergeToMap(object, mapLike);
	}

	@Override
	public DataResult<T> mergeToMap(T object, Map<T, T> map) {
		return this.delegate.mergeToMap(object, map);
	}

	@Override
	public DataResult<T> mergeToPrimitive(T object, T object2) {
		return this.delegate.mergeToPrimitive(object, object2);
	}

	@Override
	public DataResult<Stream<Pair<T, T>>> getMapValues(T object) {
		return this.delegate.getMapValues(object);
	}

	@Override
	public DataResult<Consumer<BiConsumer<T, T>>> getMapEntries(T object) {
		return this.delegate.getMapEntries(object);
	}

	@Override
	public T createMap(Map<T, T> map) {
		return this.delegate.createMap(map);
	}

	@Override
	public T createMap(Stream<Pair<T, T>> stream) {
		return this.delegate.createMap(stream);
	}

	@Override
	public DataResult<MapLike<T>> getMap(T object) {
		return this.delegate.getMap(object);
	}

	@Override
	public DataResult<Stream<T>> getStream(T object) {
		return this.delegate.getStream(object);
	}

	@Override
	public DataResult<Consumer<Consumer<T>>> getList(T object) {
		return this.delegate.getList(object);
	}

	@Override
	public T createList(Stream<T> stream) {
		return this.delegate.createList(stream);
	}

	@Override
	public DataResult<ByteBuffer> getByteBuffer(T object) {
		return this.delegate.getByteBuffer(object);
	}

	@Override
	public T createByteList(ByteBuffer byteBuffer) {
		return this.delegate.createByteList(byteBuffer);
	}

	@Override
	public DataResult<IntStream> getIntStream(T object) {
		return this.delegate.getIntStream(object);
	}

	@Override
	public T createIntList(IntStream intStream) {
		return this.delegate.createIntList(intStream);
	}

	@Override
	public DataResult<LongStream> getLongStream(T object) {
		return this.delegate.getLongStream(object);
	}

	@Override
	public T createLongList(LongStream longStream) {
		return this.delegate.createLongList(longStream);
	}

	@Override
	public T remove(T object, String string) {
		return this.delegate.remove(object, string);
	}

	@Override
	public boolean compressMaps() {
		return this.delegate.compressMaps();
	}

	@Override
	public ListBuilder<T> listBuilder() {
		return new DelegatingOps.DelegateListBuilder(this.delegate.listBuilder());
	}

	@Override
	public RecordBuilder<T> mapBuilder() {
		return new DelegatingOps.DelegateRecordBuilder(this.delegate.mapBuilder());
	}

	protected class DelegateListBuilder implements ListBuilder<T> {
		private final ListBuilder<T> original;

		protected DelegateListBuilder(final ListBuilder<T> listBuilder) {
			this.original = listBuilder;
		}

		@Override
		public DynamicOps<T> ops() {
			return DelegatingOps.this;
		}

		@Override
		public DataResult<T> build(T object) {
			return this.original.build(object);
		}

		@Override
		public ListBuilder<T> add(T object) {
			this.original.add(object);
			return this;
		}

		@Override
		public ListBuilder<T> add(DataResult<T> dataResult) {
			this.original.add(dataResult);
			return this;
		}

		@Override
		public <E> ListBuilder<T> add(E object, Encoder<E> encoder) {
			this.original.add(encoder.encodeStart(this.ops(), object));
			return this;
		}

		@Override
		public <E> ListBuilder<T> addAll(Iterable<E> iterable, Encoder<E> encoder) {
			iterable.forEach(object -> this.original.add(encoder.encode((E)object, this.ops(), (T)this.ops().empty())));
			return this;
		}

		@Override
		public ListBuilder<T> withErrorsFrom(DataResult<?> dataResult) {
			this.original.withErrorsFrom(dataResult);
			return this;
		}

		@Override
		public ListBuilder<T> mapError(UnaryOperator<String> unaryOperator) {
			this.original.mapError(unaryOperator);
			return this;
		}

		@Override
		public DataResult<T> build(DataResult<T> dataResult) {
			return this.original.build(dataResult);
		}
	}

	protected class DelegateRecordBuilder implements RecordBuilder<T> {
		private final RecordBuilder<T> original;

		protected DelegateRecordBuilder(final RecordBuilder<T> recordBuilder) {
			this.original = recordBuilder;
		}

		@Override
		public DynamicOps<T> ops() {
			return DelegatingOps.this;
		}

		@Override
		public RecordBuilder<T> add(T object, T object2) {
			this.original.add(object, object2);
			return this;
		}

		@Override
		public RecordBuilder<T> add(T object, DataResult<T> dataResult) {
			this.original.add(object, dataResult);
			return this;
		}

		@Override
		public RecordBuilder<T> add(DataResult<T> dataResult, DataResult<T> dataResult2) {
			this.original.add(dataResult, dataResult2);
			return this;
		}

		@Override
		public RecordBuilder<T> add(String string, T object) {
			this.original.add(string, object);
			return this;
		}

		@Override
		public RecordBuilder<T> add(String string, DataResult<T> dataResult) {
			this.original.add(string, dataResult);
			return this;
		}

		@Override
		public <E> RecordBuilder<T> add(String string, E object, Encoder<E> encoder) {
			return this.original.add(string, encoder.encodeStart(this.ops(), object));
		}

		@Override
		public RecordBuilder<T> withErrorsFrom(DataResult<?> dataResult) {
			this.original.withErrorsFrom(dataResult);
			return this;
		}

		@Override
		public RecordBuilder<T> setLifecycle(Lifecycle lifecycle) {
			this.original.setLifecycle(lifecycle);
			return this;
		}

		@Override
		public RecordBuilder<T> mapError(UnaryOperator<String> unaryOperator) {
			this.original.mapError(unaryOperator);
			return this;
		}

		@Override
		public DataResult<T> build(T object) {
			return this.original.build(object);
		}

		@Override
		public DataResult<T> build(DataResult<T> dataResult) {
			return this.original.build(dataResult);
		}
	}
}
