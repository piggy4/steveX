package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;

public class EntityFallDistanceFloatToDoubleFix extends DataFix {
	private final TypeReference type;

	public EntityFallDistanceFloatToDoubleFix(Schema schema, TypeReference typeReference) {
		super(schema, false);
		this.type = typeReference;
	}

	@Override
	protected TypeRewriteRule makeRule() {
		return this.fixTypeEverywhereTyped(
			"EntityFallDistanceFloatToDoubleFixFor" + this.type.typeName(), this.getOutputSchema().getType(this.type), EntityFallDistanceFloatToDoubleFix::fixEntity
		);
	}

	private static Typed<?> fixEntity(Typed<?> typed) {
		return typed.update(
			DSL.remainderFinder(), dynamic -> dynamic.renameAndFixField("FallDistance", "fall_distance", dynamicx -> dynamicx.createDouble(dynamicx.asFloat(0.0F)))
		);
	}
}
