package net.minecraft.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;

public class ValueInputContextHelper {
	final HolderLookup.Provider lookup;
	private final DynamicOps<Tag> ops;
	final ValueInput.ValueInputList emptyChildList = new ValueInput.ValueInputList() {
		@Override
		public boolean isEmpty() {
			return true;
		}

		@Override
		public Stream<ValueInput> stream() {
			return Stream.empty();
		}

		@Override
		public Iterator<ValueInput> iterator() {
			return Collections.emptyIterator();
		}
	};
	private final ValueInput.TypedInputList<Object> emptyTypedList = new ValueInput.TypedInputList<Object>() {
		@Override
		public boolean isEmpty() {
			return true;
		}

		@Override
		public Stream<Object> stream() {
			return Stream.empty();
		}

		@Override
		public Iterator<Object> iterator() {
			return Collections.emptyIterator();
		}
	};
	private final ValueInput empty = new ValueInput() {
		@Override
		public <T> Optional<T> read(String string, Codec<T> codec) {
			return Optional.empty();
		}

		@Override
		public <T> Optional<T> read(MapCodec<T> mapCodec) {
			return Optional.empty();
		}

		@Override
		public Optional<ValueInput> child(String string) {
			return Optional.empty();
		}

		@Override
		public ValueInput childOrEmpty(String string) {
			return this;
		}

		@Override
		public Optional<ValueInput.ValueInputList> childrenList(String string) {
			return Optional.empty();
		}

		@Override
		public ValueInput.ValueInputList childrenListOrEmpty(String string) {
			return ValueInputContextHelper.this.emptyChildList;
		}

		@Override
		public <T> Optional<ValueInput.TypedInputList<T>> list(String string, Codec<T> codec) {
			return Optional.empty();
		}

		@Override
		public <T> ValueInput.TypedInputList<T> listOrEmpty(String string, Codec<T> codec) {
			return ValueInputContextHelper.this.emptyTypedList();
		}

		@Override
		public boolean getBooleanOr(String string, boolean bl) {
			return bl;
		}

		@Override
		public byte getByteOr(String string, byte b) {
			return b;
		}

		@Override
		public int getShortOr(String string, short s) {
			return s;
		}

		@Override
		public Optional<Integer> getInt(String string) {
			return Optional.empty();
		}

		@Override
		public int getIntOr(String string, int i) {
			return i;
		}

		@Override
		public long getLongOr(String string, long l) {
			return l;
		}

		@Override
		public Optional<Long> getLong(String string) {
			return Optional.empty();
		}

		@Override
		public float getFloatOr(String string, float f) {
			return f;
		}

		@Override
		public double getDoubleOr(String string, double d) {
			return d;
		}

		@Override
		public Optional<String> getString(String string) {
			return Optional.empty();
		}

		@Override
		public String getStringOr(String string, String string2) {
			return string2;
		}

		@Override
		public HolderLookup.Provider lookup() {
			return ValueInputContextHelper.this.lookup;
		}

		@Override
		public Optional<int[]> getIntArray(String string) {
			return Optional.empty();
		}
	};

	public ValueInputContextHelper(HolderLookup.Provider provider, DynamicOps<Tag> dynamicOps) {
		this.lookup = provider;
		this.ops = provider.createSerializationContext(dynamicOps);
	}

	public DynamicOps<Tag> ops() {
		return this.ops;
	}

	public HolderLookup.Provider lookup() {
		return this.lookup;
	}

	public ValueInput empty() {
		return this.empty;
	}

	public ValueInput.ValueInputList emptyList() {
		return this.emptyChildList;
	}

	public <T> ValueInput.TypedInputList<T> emptyTypedList() {
		return (ValueInput.TypedInputList<T>)this.emptyTypedList;
	}
}
