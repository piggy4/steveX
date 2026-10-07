package net.minecraft.world.level.chunk;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import org.apache.commons.lang3.Validate;

public class LinearPalette<T> implements Palette<T> {
	private final T[] values;
	private final int bits;
	private int size;

	private LinearPalette(int i, List<T> list) {
		this.values = (T[])(new Object[1 << i]);
		this.bits = i;
		Validate.isTrue(list.size() <= this.values.length, "Can't initialize LinearPalette of size %d with %d entries", this.values.length, list.size());

		for (int j = 0; j < list.size(); j++) {
			this.values[j] = list.get(j);
		}

		this.size = list.size();
	}

	private LinearPalette(T[] objects, int i, int j) {
		this.values = objects;
		this.bits = i;
		this.size = j;
	}

	public static <A> Palette<A> create(int i, List<A> list) {
		return new LinearPalette<>(i, list);
	}

	@Override
	public int idFor(T object, PaletteResize<T> paletteResize) {
		for (int i = 0; i < this.size; i++) {
			if (this.values[i] == object) {
				return i;
			}
		}

		int i = this.size;
		if (i < this.values.length) {
			this.values[i] = object;
			this.size++;
			return i;
		} else {
			return paletteResize.onResize(this.bits + 1, object);
		}
	}

	@Override
	public boolean maybeHas(Predicate<T> predicate) {
		for (int i = 0; i < this.size; i++) {
			if (predicate.test(this.values[i])) {
				return true;
			}
		}

		return false;
	}

	@Override
	public T valueFor(int i) {
		if (i >= 0 && i < this.size) {
			return this.values[i];
		} else {
			throw new MissingPaletteEntryException(i);
		}
	}

	@Override
	public void read(FriendlyByteBuf friendlyByteBuf, IdMap<T> idMap) {
		this.size = friendlyByteBuf.readVarInt();

		for (int i = 0; i < this.size; i++) {
			this.values[i] = idMap.byIdOrThrow(friendlyByteBuf.readVarInt());
		}
	}

	@Override
	public void write(FriendlyByteBuf friendlyByteBuf, IdMap<T> idMap) {
		friendlyByteBuf.writeVarInt(this.size);

		for (int i = 0; i < this.size; i++) {
			friendlyByteBuf.writeVarInt(idMap.getId(this.values[i]));
		}
	}

	@Override
	public int getSerializedSize(IdMap<T> idMap) {
		int i = VarInt.getByteSize(this.getSize());

		for (int j = 0; j < this.getSize(); j++) {
			i += VarInt.getByteSize(idMap.getId(this.values[j]));
		}

		return i;
	}

	@Override
	public int getSize() {
		return this.size;
	}

	@Override
	public Palette<T> copy() {
		return new LinearPalette<>((T[])((Object[])this.values.clone()), this.bits, this.size);
	}
}
