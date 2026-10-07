package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import net.minecraft.util.datafix.LegacyComponentDataFixUtils;

public class ScoreboardDisplayNameFix extends DataFix {
	private final String name;
	private final TypeReference type;

	public ScoreboardDisplayNameFix(Schema schema, String string, TypeReference typeReference) {
		super(schema, false);
		this.name = string;
		this.type = typeReference;
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<?> type = this.getInputSchema().getType(this.type);
		OpticFinder<?> opticFinder = type.findField("DisplayName");
		OpticFinder<Pair<String, String>> opticFinder2 = DSL.typeFinder((Type<Pair<String, String>>)this.getInputSchema().getType(References.TEXT_COMPONENT));
		return this.fixTypeEverywhereTyped(
			this.name,
			type,
			typed -> typed.updateTyped(opticFinder, typedx -> typedx.update(opticFinder2, pair -> pair.mapSecond(LegacyComponentDataFixUtils::createTextComponentJson)))
		);
	}
}
