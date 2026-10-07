package net.minecraft.world.level.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;

public class HashMapPalette<T> implements Palette<T> {
	private final CrudeIncrementalIntIdentityHashBiMap<T> values;
	private final int bits;

	public HashMapPalette(int i, List<T> list) {
		this(i);
		list.forEach(this.values::add);
	}

	public HashMapPalette(int i) {
		this(i, CrudeIncrementalIntIdentityHashBiMap.create(1 << i));
	}

	private HashMapPalette(int i, CrudeIncrementalIntIdentityHashBiMap<T> crudeIncrementalIntIdentityHashBiMap) {
		this.bits = i;
		this.values = crudeIncrementalIntIdentityHashBiMap;
	}

	public static <A> Palette<A> create(int i, List<A> list) {
		return new HashMapPalette<>(i, list);
	}

	@Override
	public int idFor(T object, PaletteResize<T> paletteResize) {
		int i = this.values.getId(object);
		if (i == -1) {
			i = this.values.add(object);
			if (i >= 1 << this.bits) {
				i = paletteResize.onResize(this.bits + 1, object);
			}
		}

		return i;
	}

	@Override
	public boolean maybeHas(Predicate<T> predicate) {
		for (int i = 0; i < this.getSize(); i++) {
			if (predicate.test(this.values.byId(i))) {
				return true;
			}
		}

		return false;
	}

	@Override
	public T valueFor(int i) {
		T object = this.values.byId(i);
		if (object == null) {
			throw new MissingPaletteEntryException(i);
		} else {
			return object;
		}
	}

	@Override
	public void read(FriendlyByteBuf friendlyByteBuf, IdMap<T> idMap) {
		this.values.clear();
		int i = friendlyByteBuf.readVarInt();

		for (int j = 0; j < i; j++) {
			this.values.add(idMap.byIdOrThrow(friendlyByteBuf.readVarInt()));
		}
	}

	@Override
	public void write(FriendlyByteBuf friendlyByteBuf, IdMap<T> idMap) {
		int i = this.getSize();
		friendlyByteBuf.writeVarInt(i);

		for (int j = 0; j < i; j++) {
			friendlyByteBuf.writeVarInt(idMap.getId(this.values.byId(j)));
		}
	}

	@Override
	public int getSerializedSize(IdMap<T> idMap) {
		int i = VarInt.getByteSize(this.getSize());

		for (int j = 0; j < this.getSize(); j++) {
			i += VarInt.getByteSize(idMap.getId(this.values.byId(j)));
		}

		return i;
	}

	public List<T> getEntries() {
		ArrayList<T> arrayList = new ArrayList<>();
		this.values.iterator().forEachRemaining(arrayList::add);
		return arrayList;
	}

	@Override
	public int getSize() {
		return this.values.size();
	}

	@Override
	public Palette<T> copy() {
		return new HashMapPalette<>(this.bits, this.values.copy());
	}
}
