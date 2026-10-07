package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

public class AddFieldFix extends DataFix {
	private final String name;
	private final TypeReference type;
	private final String fieldName;
	private final String[] path;
	private final Function<Dynamic<?>, Dynamic<?>> fieldGenerator;

	public AddFieldFix(Schema schema, TypeReference typeReference, String string, Function<Dynamic<?>, Dynamic<?>> function, String... strings) {
		super(schema, false);
		this.name = "Adding field `" + string + "` to type `" + typeReference.typeName().toLowerCase(Locale.ROOT) + "`";
		this.type = typeReference;
		this.fieldName = string;
		this.path = strings;
		this.fieldGenerator = function;
	}

	@Override
	protected TypeRewriteRule makeRule() {
		return this.fixTypeEverywhereTyped(
			this.name,
			this.getInputSchema().getType(this.type),
			this.getOutputSchema().getType(this.type),
			typed -> typed.update(DSL.remainderFinder(), dynamic -> this.addField(dynamic, 0))
		);
	}

	private Dynamic<?> addField(Dynamic<?> dynamic, int i) {
		if (i >= this.path.length) {
			return dynamic.set(this.fieldName, this.fieldGenerator.apply(dynamic));
		}

		Optional<? extends Dynamic<?>> optional = dynamic.get(this.path[i]).result();
		return optional.isEmpty() ? dynamic : this.addField((Dynamic<?>)optional.get(), i + 1);
	}
}
