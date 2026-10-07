package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import net.minecraft.util.datafix.LegacyComponentDataFixUtils;

public class WrittenBookPagesStrictJsonFix extends ItemStackTagFix {
	public WrittenBookPagesStrictJsonFix(Schema schema) {
		super(schema, "WrittenBookPagesStrictJsonFix", string -> string.equals("minecraft:written_book"));
	}

	@Override
	protected Typed<?> fixItemStackTag(Typed<?> typed) {
		Type<Pair<String, String>> type = (Type<Pair<String, String>>)this.getInputSchema().getType(References.TEXT_COMPONENT);
		Type<?> type2 = this.getInputSchema().getType(References.ITEM_STACK);
		OpticFinder<?> opticFinder = type2.findField("tag");
		OpticFinder<?> opticFinder2 = opticFinder.type().findField("pages");
		OpticFinder<Pair<String, String>> opticFinder3 = DSL.typeFinder(type);
		return typed.updateTyped(opticFinder2, typedx -> typedx.update(opticFinder3, pair -> pair.mapSecond(LegacyComponentDataFixUtils::rewriteFromLenient)));
	}
}
