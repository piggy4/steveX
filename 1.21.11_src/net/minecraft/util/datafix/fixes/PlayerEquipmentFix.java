package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.HashMap;
import java.util.Map;

public class PlayerEquipmentFix extends DataFix {
	private static final Map<Integer, String> SLOT_TRANSLATIONS = Map.of(100, "feet", 101, "legs", 102, "chest", 103, "head", -106, "offhand");

	public PlayerEquipmentFix(Schema schema) {
		super(schema, true);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<?> type = this.getInputSchema().getTypeRaw(References.PLAYER);
		Type<?> type2 = this.getOutputSchema().getTypeRaw(References.PLAYER);
		return this.writeFixAndRead("Player Equipment Fix", type, type2, dynamic -> {
			Map<Dynamic<?>, Dynamic<?>> map = new HashMap<>();
			dynamic = dynamic.update("Inventory", dynamicx -> dynamicx.createList(dynamicx.asStream().filter(dynamic2 -> {
				int i = dynamic2.get("Slot").asInt(-1);
				String string = SLOT_TRANSLATIONS.get(i);
				if (string != null) {
					map.put(dynamicx.createString(string), dynamic2.remove("Slot"));
				}

				return string == null;
			})));
			return dynamic.set("equipment", dynamic.createMap(map));
		});
	}
}
