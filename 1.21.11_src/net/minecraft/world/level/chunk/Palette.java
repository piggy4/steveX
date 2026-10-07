package net.minecraft.world.level.chunk;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;

public interface Palette<T> {
	int idFor(T object, PaletteResize<T> paletteResize);

	boolean maybeHas(Predicate<T> predicate);

	T valueFor(int i);

	void read(FriendlyByteBuf friendlyByteBuf, IdMap<T> idMap);

	void write(FriendlyByteBuf friendlyByteBuf, IdMap<T> idMap);

	int getSerializedSize(IdMap<T> idMap);

	int getSize();

	Palette<T> copy();

	interface Factory {
		<A> Palette<A> create(int i, List<A> list);
	}
}
