package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.OptionalDynamic;
import java.util.List;

public class DropChancesFormatFix extends DataFix {
	private static final List<String> ARMOR_SLOT_NAMES = List.of("feet", "legs", "chest", "head");
	private static final List<String> HAND_SLOT_NAMES = List.of("mainhand", "offhand");
	private static final float DEFAULT_CHANCE = 0.085F;

	public DropChancesFormatFix(Schema schema) {
		super(schema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		return this.fixTypeEverywhereTyped(
			"DropChancesFormatFix", this.getInputSchema().getType(References.ENTITY), typed -> typed.update(DSL.remainderFinder(), dynamic -> {
				List<Float> list = parseDropChances(dynamic.get("ArmorDropChances"));
				List<Float> list2 = parseDropChances(dynamic.get("HandDropChances"));
				float f = dynamic.get("body_armor_drop_chance").asNumber().result().map(Number::floatValue).orElse(0.085F);
				dynamic = dynamic.remove("ArmorDropChances").remove("HandDropChances").remove("body_armor_drop_chance");
				Dynamic<?> dynamic2 = dynamic.emptyMap();
				dynamic2 = addSlotChances(dynamic2, list, ARMOR_SLOT_NAMES);
				dynamic2 = addSlotChances(dynamic2, list2, HAND_SLOT_NAMES);
				if (f != 0.085F) {
					dynamic2 = dynamic2.set("body", dynamic.createFloat(f));
				}

				return !dynamic2.equals(dynamic.emptyMap()) ? dynamic.set("drop_chances", dynamic2) : dynamic;
			})
		);
	}

	private static Dynamic<?> addSlotChances(Dynamic<?> dynamic, List<Float> list, List<String> list2) {
		for (int i = 0; i < list2.size() && i < list.size(); i++) {
			String string = list2.get(i);
			float f = list.get(i);
			if (f != 0.085F) {
				dynamic = dynamic.set(string, dynamic.createFloat(f));
			}
		}

		return dynamic;
	}

	private static List<Float> parseDropChances(OptionalDynamic<?> optionalDynamic) {
		return optionalDynamic.asStream().map(dynamic -> dynamic.asFloat(0.085F)).toList();
	}
}
