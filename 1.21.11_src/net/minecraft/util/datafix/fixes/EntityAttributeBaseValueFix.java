package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class EntityAttributeBaseValueFix extends NamedEntityFix {
	private final String attributeId;
	private final DoubleUnaryOperator valueFixer;

	public EntityAttributeBaseValueFix(Schema schema, String string, String string2, String string3, DoubleUnaryOperator doubleUnaryOperator) {
		super(schema, false, string, References.ENTITY, string2);
		this.attributeId = string3;
		this.valueFixer = doubleUnaryOperator;
	}

	@Override
	protected Typed<?> fix(Typed<?> typed) {
		return typed.update(DSL.remainderFinder(), this::fixValue);
	}

	private Dynamic<?> fixValue(Dynamic<?> dynamic) {
		return dynamic.update("attributes", dynamic2 -> dynamic.createList(dynamic2.asStream().map(dynamicxx -> {
			String string = NamespacedSchema.ensureNamespaced(dynamicxx.get("id").asString(""));
			if (!string.equals(this.attributeId)) {
				return dynamicxx;
			}

			double d = dynamicxx.get("base").asDouble(0.0);
			return dynamicxx.set("base", dynamicxx.createDouble(this.valueFixer.applyAsDouble(d)));
		})));
	}
}
