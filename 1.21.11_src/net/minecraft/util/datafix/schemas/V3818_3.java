package net.minecraft.util.datafix.schemas;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;

public class V3818_3 extends NamespacedSchema {
	public V3818_3(int i, Schema schema) {
		super(i, schema);
	}

	public static SequencedMap<String, Supplier<TypeTemplate>> components(Schema schema) {
		SequencedMap<String, Supplier<TypeTemplate>> sequencedMap = new LinkedHashMap<>();
		sequencedMap.put("minecraft:bees", () -> DSL.list(DSL.optionalFields("entity_data", References.ENTITY_TREE.in(schema))));
		sequencedMap.put("minecraft:block_entity_data", () -> References.BLOCK_ENTITY.in(schema));
		sequencedMap.put("minecraft:bundle_contents", () -> DSL.list(References.ITEM_STACK.in(schema)));
		sequencedMap.put(
			"minecraft:can_break",
			() -> DSL.optionalFields(
				"predicates", DSL.list(DSL.optionalFields("blocks", DSL.or(References.BLOCK_NAME.in(schema), DSL.list(References.BLOCK_NAME.in(schema)))))
			)
		);
		sequencedMap.put(
			"minecraft:can_place_on",
			() -> DSL.optionalFields(
				"predicates", DSL.list(DSL.optionalFields("blocks", DSL.or(References.BLOCK_NAME.in(schema), DSL.list(References.BLOCK_NAME.in(schema)))))
			)
		);
		sequencedMap.put("minecraft:charged_projectiles", () -> DSL.list(References.ITEM_STACK.in(schema)));
		sequencedMap.put("minecraft:container", () -> DSL.list(DSL.optionalFields("item", References.ITEM_STACK.in(schema))));
		sequencedMap.put("minecraft:entity_data", () -> References.ENTITY_TREE.in(schema));
		sequencedMap.put("minecraft:pot_decorations", () -> DSL.list(References.ITEM_NAME.in(schema)));
		sequencedMap.put("minecraft:food", () -> DSL.optionalFields("using_converts_to", References.ITEM_STACK.in(schema)));
		sequencedMap.put("minecraft:custom_name", () -> References.TEXT_COMPONENT.in(schema));
		sequencedMap.put("minecraft:item_name", () -> References.TEXT_COMPONENT.in(schema));
		sequencedMap.put("minecraft:lore", () -> DSL.list(References.TEXT_COMPONENT.in(schema)));
		sequencedMap.put(
			"minecraft:written_book_content",
			() -> DSL.optionalFields(
				"pages",
				DSL.list(
					DSL.or(
						DSL.optionalFields("raw", References.TEXT_COMPONENT.in(schema), "filtered", References.TEXT_COMPONENT.in(schema)), References.TEXT_COMPONENT.in(schema)
					)
				)
			)
		);
		return sequencedMap;
	}

	@Override
	public void registerTypes(Schema schema, Map<String, Supplier<TypeTemplate>> map, Map<String, Supplier<TypeTemplate>> map2) {
		super.registerTypes(schema, map, map2);
		schema.registerType(true, References.DATA_COMPONENTS, () -> DSL.optionalFieldsLazy(components(schema)));
	}
}
