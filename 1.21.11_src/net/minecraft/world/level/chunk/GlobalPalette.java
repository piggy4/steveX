package net.minecraft.world.level.chunk;

import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;

public class GlobalPalette<T> implements Palette<T> {
	private final IdMap<T> registry;

	public GlobalPalette(IdMap<T> idMap) {
		this.registry = idMap;
	}

	@Override
	public int idFor(T object, PaletteResize<T> paletteResize) {
		int i = this.registry.getId(object);
		return i == -1 ? 0 : i;
	}

	@Override
	public boolean maybeHas(Predicate<T> predicate) {
		return true;
	}

	@Override
	public T valueFor(int i) {
		T object = this.registry.byId(i);
		if (object == null) {
			throw new MissingPaletteEntryException(i);
		} else {
			return object;
		}
	}

	@Override
	public void read(FriendlyByteBuf friendlyByteBuf, IdMap<T> idMap) {
	}

	@Override
	public void write(FriendlyByteBuf friendlyByteBuf, IdMap<T> idMap) {
	}

	@Override
	public int getSerializedSize(IdMap<T> idMap) {
		return 0;
	}

	@Override
	public int getSize() {
		return this.registry.size();
	}

	@Override
	public Palette<T> copy() {
		return this;
	}
}
