package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public abstract class AbstractBlockPropertyFix extends DataFix {
	private final String name;

	public AbstractBlockPropertyFix(Schema schema, String string) {
		super(schema, false);
		this.name = string;
	}

	@Override
	protected TypeRewriteRule makeRule() {
		return this.fixTypeEverywhereTyped(
			this.name, this.getInputSchema().getType(References.BLOCK_STATE), typed -> typed.update(DSL.remainderFinder(), this::fixBlockState)
		);
	}

	private Dynamic<?> fixBlockState(Dynamic<?> dynamic) {
		Optional<String> optional = dynamic.get("Name").asString().result().map(NamespacedSchema::ensureNamespaced);
		return optional.isPresent() && this.shouldFix(optional.get())
			? dynamic.update("Properties", dynamicx -> this.fixProperties(optional.get(), dynamicx))
			: dynamic;
	}

	protected abstract boolean shouldFix(String string);

	protected abstract <T> Dynamic<T> fixProperties(String string, Dynamic<T> dynamic);
}
