package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.OptionalDynamic;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import net.minecraft.util.Util;

public class TooltipDisplayComponentFix extends DataFix {
	private static final List<String> CONVERTED_ADDITIONAL_TOOLTIP_TYPES = List.of(
		"minecraft:banner_patterns",
		"minecraft:bees",
		"minecraft:block_entity_data",
		"minecraft:block_state",
		"minecraft:bundle_contents",
		"minecraft:charged_projectiles",
		"minecraft:container",
		"minecraft:container_loot",
		"minecraft:firework_explosion",
		"minecraft:fireworks",
		"minecraft:instrument",
		"minecraft:map_id",
		"minecraft:painting/variant",
		"minecraft:pot_decorations",
		"minecraft:potion_contents",
		"minecraft:tropical_fish/pattern",
		"minecraft:written_book_content"
	);

	public TooltipDisplayComponentFix(Schema schema) {
		super(schema, true);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<?> type = this.getInputSchema().getType(References.DATA_COMPONENTS);
		Type<?> type2 = this.getOutputSchema().getType(References.DATA_COMPONENTS);
		OpticFinder<?> opticFinder = type.findField("minecraft:can_place_on");
		OpticFinder<?> opticFinder2 = type.findField("minecraft:can_break");
		Type<?> type3 = type2.findFieldType("minecraft:can_place_on");
		Type<?> type4 = type2.findFieldType("minecraft:can_break");
		return this.fixTypeEverywhereTyped("TooltipDisplayComponentFix", type, type2, typed -> fix(typed, opticFinder, opticFinder2, type3, type4));
	}

	private static Typed<?> fix(Typed<?> typed, OpticFinder<?> opticFinder, OpticFinder<?> opticFinder2, Type<?> type, Type<?> type2) {
		Set<String> set = new HashSet<>();
		typed = fixAdventureModePredicate(typed, opticFinder, type, "minecraft:can_place_on", set);
		typed = fixAdventureModePredicate(typed, opticFinder2, type2, "minecraft:can_break", set);
		return typed.update(
			DSL.remainderFinder(),
			dynamic -> {
				dynamic = fixSimpleComponent(dynamic, "minecraft:trim", set);
				dynamic = fixSimpleComponent(dynamic, "minecraft:unbreakable", set);
				dynamic = fixComponentAndUnwrap(dynamic, "minecraft:dyed_color", "rgb", set);
				dynamic = fixComponentAndUnwrap(dynamic, "minecraft:attribute_modifiers", "modifiers", set);
				dynamic = fixComponentAndUnwrap(dynamic, "minecraft:enchantments", "levels", set);
				dynamic = fixComponentAndUnwrap(dynamic, "minecraft:stored_enchantments", "levels", set);
				dynamic = fixComponentAndUnwrap(dynamic, "minecraft:jukebox_playable", "song", set);
				boolean bl = dynamic.get("minecraft:hide_tooltip").result().isPresent();
				dynamic = dynamic.remove("minecraft:hide_tooltip");
				boolean bl2 = dynamic.get("minecraft:hide_additional_tooltip").result().isPresent();
				dynamic = dynamic.remove("minecraft:hide_additional_tooltip");
				if (bl2) {
					for (String string : CONVERTED_ADDITIONAL_TOOLTIP_TYPES) {
						if (dynamic.get(string).result().isPresent()) {
							set.add(string);
						}
					}
				}

				return set.isEmpty() && !bl
					? dynamic
					: dynamic.set(
						"minecraft:tooltip_display",
						dynamic.createMap(
							Map.of(
								dynamic.createString("hide_tooltip"),
								dynamic.createBoolean(bl),
								dynamic.createString("hidden_components"),
								dynamic.createList(set.stream().map(dynamic::createString))
							)
						)
					);
			}
		);
	}

	private static Dynamic<?> fixSimpleComponent(Dynamic<?> dynamic, String string, Set<String> set) {
		return fixRemainderComponent(dynamic, string, set, UnaryOperator.identity());
	}

	private static Dynamic<?> fixComponentAndUnwrap(Dynamic<?> dynamic, String string, String string2, Set<String> set) {
		return fixRemainderComponent(dynamic, string, set, dynamicx -> DataFixUtils.orElse(dynamicx.get(string2).result(), dynamicx));
	}

	private static Dynamic<?> fixRemainderComponent(Dynamic<?> dynamic, String string, Set<String> set, UnaryOperator<Dynamic<?>> unaryOperator) {
		return dynamic.update(string, dynamicx -> {
			boolean bl = dynamicx.get("show_in_tooltip").asBoolean(true);
			if (!bl) {
				set.add(string);
			}

			return unaryOperator.apply(dynamicx.remove("show_in_tooltip"));
		});
	}

	private static Typed<?> fixAdventureModePredicate(Typed<?> typed, OpticFinder<?> opticFinder, Type<?> type, String string, Set<String> set) {
		return typed.updateTyped(opticFinder, type, typedx -> Util.writeAndReadTypedOrThrow(typedx, type, dynamic -> {
			OptionalDynamic<?> optionalDynamic = dynamic.get("predicates");
			if (optionalDynamic.result().isEmpty()) {
				return dynamic;
			}

			boolean bl = dynamic.get("show_in_tooltip").asBoolean(true);
			if (!bl) {
				set.add(string);
			}

			return optionalDynamic.result().get();
		}));
	}
}
