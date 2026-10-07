package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import java.util.Map;
import java.util.stream.Stream;

public class CustomModelDataExpandFix extends DataFix {
	public CustomModelDataExpandFix(Schema schema) {
		super(schema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<?> type = this.getInputSchema().getType(References.DATA_COMPONENTS);
		return this.fixTypeEverywhereTyped(
			"Custom Model Data expansion", type, typed -> typed.update(DSL.remainderFinder(), dynamic -> dynamic.update("minecraft:custom_model_data", dynamicx -> {
				float f = dynamicx.asNumber(0.0F).floatValue();
				return dynamicx.createMap(Map.of(dynamicx.createString("floats"), dynamicx.createList(Stream.of(dynamicx.createFloat(f)))));
			}))
		);
	}
}
