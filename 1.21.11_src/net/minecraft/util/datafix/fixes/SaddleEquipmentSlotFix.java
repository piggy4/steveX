package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.TaggedChoice.TaggedChoiceType;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Set;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.ExtraDataFixUtils;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class SaddleEquipmentSlotFix extends DataFix {
	private static final Set<String> ENTITIES_WITH_SADDLE_ITEM = Set.of(
		"minecraft:horse",
		"minecraft:skeleton_horse",
		"minecraft:zombie_horse",
		"minecraft:donkey",
		"minecraft:mule",
		"minecraft:camel",
		"minecraft:llama",
		"minecraft:trader_llama"
	);
	private static final Set<String> ENTITIES_WITH_SADDLE_FLAG = Set.of("minecraft:pig", "minecraft:strider");
	private static final String SADDLE_FLAG = "Saddle";
	private static final String NEW_SADDLE = "saddle";

	public SaddleEquipmentSlotFix(Schema schema) {
		super(schema, true);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		TaggedChoiceType<String> taggedChoiceType = (TaggedChoiceType<String>)this.getInputSchema().findChoiceType(References.ENTITY);
		OpticFinder<Pair<String, ?>> opticFinder = DSL.typeFinder(taggedChoiceType);
		Type<?> type = this.getInputSchema().getType(References.ENTITY);
		Type<?> type2 = this.getOutputSchema().getType(References.ENTITY);
		Type<?> type3 = ExtraDataFixUtils.patchSubType(type, type, type2);
		return this.fixTypeEverywhereTyped(
			"SaddleEquipmentSlotFix",
			type,
			type2,
			typed -> {
				String string = typed.getOptional(opticFinder).map(Pair::getFirst).map(NamespacedSchema::ensureNamespaced).orElse("");
				Typed<?> typed2 = ExtraDataFixUtils.cast(type3, typed);
				if (ENTITIES_WITH_SADDLE_ITEM.contains(string)) {
					return Util.writeAndReadTypedOrThrow(typed2, type2, SaddleEquipmentSlotFix::fixEntityWithSaddleItem);
				} else {
					return ENTITIES_WITH_SADDLE_FLAG.contains(string)
						? Util.writeAndReadTypedOrThrow(typed2, type2, SaddleEquipmentSlotFix::fixEntityWithSaddleFlag)
						: ExtraDataFixUtils.cast(type2, typed);
				}
			}
		);
	}

	private static Dynamic<?> fixEntityWithSaddleItem(Dynamic<?> dynamic) {
		return dynamic.get("SaddleItem").result().isEmpty() ? dynamic : fixDropChances(dynamic.renameField("SaddleItem", "saddle"));
	}

	private static Dynamic<?> fixEntityWithSaddleFlag(Dynamic<?> dynamic) {
		boolean bl = dynamic.get("Saddle").asBoolean(false);
		dynamic = dynamic.remove("Saddle");
		if (!bl) {
			return dynamic;
		}

		Dynamic<?> dynamic2 = dynamic.emptyMap().set("id", dynamic.createString("minecraft:saddle")).set("count", dynamic.createInt(1));
		return fixDropChances(dynamic.set("saddle", dynamic2));
	}

	private static Dynamic<?> fixDropChances(Dynamic<?> dynamic) {
		Dynamic<?> dynamic2 = dynamic.get("drop_chances").orElseEmptyMap().set("saddle", dynamic.createFloat(2.0F));
		return dynamic.set("drop_chances", dynamic2);
	}
}
