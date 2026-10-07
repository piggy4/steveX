package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.function.Predicate;

public abstract class ItemStackTagRemainderFix extends ItemStackTagFix {
	public ItemStackTagRemainderFix(Schema schema, String string, Predicate<String> predicate) {
		super(schema, string, predicate);
	}

	protected abstract <T> Dynamic<T> fixItemStackTag(Dynamic<T> dynamic);

	@Override
	protected final Typed<?> fixItemStackTag(Typed<?> typed) {
		return typed.update(DSL.remainderFinder(), this::fixItemStackTag);
	}
}
