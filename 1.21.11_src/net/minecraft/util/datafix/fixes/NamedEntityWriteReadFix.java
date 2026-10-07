package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public abstract class NamedEntityWriteReadFix extends DataFix {
	private final String name;
	private final String entityName;
	private final TypeReference type;

	public NamedEntityWriteReadFix(Schema schema, boolean bl, String string, TypeReference typeReference, String string2) {
		super(schema, bl);
		this.name = string;
		this.type = typeReference;
		this.entityName = string2;
	}

	@Override
	public TypeRewriteRule makeRule() {
		Type<?> type = this.getInputSchema().getType(this.type);
		Type<?> type2 = this.getInputSchema().getChoiceType(this.type, this.entityName);
		Type<?> type3 = this.getOutputSchema().getType(this.type);
		OpticFinder<?> opticFinder = DSL.namedChoice(this.entityName, type2);
		Type<?> type4 = ExtraDataFixUtils.patchSubType(type, type, type3);
		return this.fix(type, type3, type4, opticFinder);
	}

	private <S, T, A> TypeRewriteRule fix(Type<S> type, Type<T> type2, Type<?> type3, OpticFinder<A> opticFinder) {
		return this.fixTypeEverywhereTyped(this.name, type, type2, typed -> {
			if (typed.getOptional(opticFinder).isEmpty()) {
				return ExtraDataFixUtils.cast(type2, typed);
			}

			Typed<?> typed2 = ExtraDataFixUtils.cast(type3, typed);
			return Util.writeAndReadTypedOrThrow(typed2, type2, this::fix);
		});
	}

	protected abstract <T> Dynamic<T> fix(Dynamic<T> dynamic);
}
