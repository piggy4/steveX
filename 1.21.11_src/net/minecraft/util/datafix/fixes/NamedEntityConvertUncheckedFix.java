package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class NamedEntityConvertUncheckedFix extends NamedEntityFix {
	public NamedEntityConvertUncheckedFix(Schema schema, String string, TypeReference typeReference, String string2) {
		super(schema, true, string, typeReference, string2);
	}

	@Override
	protected Typed<?> fix(Typed<?> typed) {
		Type<?> type = this.getOutputSchema().getChoiceType(this.type, this.entityName);
		return ExtraDataFixUtils.cast(type, typed);
	}
}
