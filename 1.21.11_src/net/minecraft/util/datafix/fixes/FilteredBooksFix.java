package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import net.minecraft.util.Util;

public class FilteredBooksFix extends ItemStackTagFix {
	public FilteredBooksFix(Schema schema) {
		super(schema, "Remove filtered text from books", string -> string.equals("minecraft:writable_book") || string.equals("minecraft:written_book"));
	}

	@Override
	protected Typed<?> fixItemStackTag(Typed<?> typed) {
		return Util.writeAndReadTypedOrThrow(typed, typed.getType(), dynamic -> dynamic.remove("filtered_title").remove("filtered_pages"));
	}
}
