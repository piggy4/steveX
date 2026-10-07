package net.minecraft.util.datafix.schemas;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import com.mojang.datafixers.util.Pair;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;

public class V4301 extends NamespacedSchema {
	public V4301(int i, Schema schema) {
		super(i, schema);
	}

	@Override
	public void registerTypes(Schema schema, Map<String, Supplier<TypeTemplate>> map, Map<String, Supplier<TypeTemplate>> map2) {
		super.registerTypes(schema, map, map2);
		schema.registerType(
			true,
			References.ENTITY_EQUIPMENT,
			() -> DSL.optional(
				DSL.field(
					"equipment",
					DSL.optionalFields(
						Pair.of("mainhand", References.ITEM_STACK.in(schema)),
						Pair.of("offhand", References.ITEM_STACK.in(schema)),
						Pair.of("feet", References.ITEM_STACK.in(schema)),
						Pair.of("legs", References.ITEM_STACK.in(schema)),
						Pair.of("chest", References.ITEM_STACK.in(schema)),
						Pair.of("head", References.ITEM_STACK.in(schema)),
						Pair.of("body", References.ITEM_STACK.in(schema)),
						Pair.of("saddle", References.ITEM_STACK.in(schema))
					)
				)
			)
		);
	}
}
