package net.minecraft.util.datafix.schemas;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;

public class V100 extends Schema {
	public V100(int i, Schema schema) {
		super(i, schema);
	}

	@Override
	public void registerTypes(Schema schema, Map<String, Supplier<TypeTemplate>> map, Map<String, Supplier<TypeTemplate>> map2) {
		super.registerTypes(schema, map, map2);
		schema.registerType(
			true,
			References.ENTITY_EQUIPMENT,
			() -> DSL.and(
				DSL.optional(DSL.field("ArmorItems", DSL.list(References.ITEM_STACK.in(schema)))),
				DSL.optional(DSL.field("HandItems", DSL.list(References.ITEM_STACK.in(schema)))),
				DSL.optional(DSL.field("body_armor_item", References.ITEM_STACK.in(schema))),
				DSL.optional(DSL.field("saddle", References.ITEM_STACK.in(schema)))
			)
		);
	}
}
