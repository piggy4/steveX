package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import net.minecraft.util.datafix.LegacyComponentDataFixUtils;

public class ItemLoreFix extends DataFix {
	public ItemLoreFix(Schema schema) {
		super(schema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
		Type<Pair<String, String>> type2 = (Type<Pair<String, String>>)this.getInputSchema().getType(References.TEXT_COMPONENT);
		OpticFinder<?> opticFinder = type.findField("tag");
		OpticFinder<?> opticFinder2 = opticFinder.type().findField("display");
		OpticFinder<?> opticFinder3 = opticFinder2.type().findField("Lore");
		OpticFinder<Pair<String, String>> opticFinder4 = DSL.typeFinder(type2);
		return this.fixTypeEverywhereTyped(
			"Item Lore componentize",
			type,
			typed -> typed.updateTyped(
				opticFinder,
				typedx -> typedx.updateTyped(
					opticFinder2,
					typedxx -> typedxx.updateTyped(
						opticFinder3, typedxxx -> typedxxx.update(opticFinder4, pair -> pair.mapSecond(LegacyComponentDataFixUtils::createTextComponentJson))
					)
				)
			)
		);
	}
}
