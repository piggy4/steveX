package net.minecraft.client.multiplayer;

import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class CacheSlot<C extends CacheSlot.Cleaner<C>, D> {
	private final Function<C, D> operation;
	private @Nullable C context;
	private @Nullable D value;

	public CacheSlot(Function<C, D> function) {
		this.operation = function;
	}

	public D compute(C cleaner) {
		if (cleaner == this.context && this.value != null) {
			return this.value;
		}

		D object = this.operation.apply(cleaner);
		this.value = object;
		this.context = cleaner;
		cleaner.registerForCleaning(this);
		return object;
	}

	public void clear() {
		this.value = null;
		this.context = null;
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	public interface Cleaner<C extends CacheSlot.Cleaner<C>> {
		void registerForCleaning(CacheSlot<C, ?> cacheSlot);
	}
}
