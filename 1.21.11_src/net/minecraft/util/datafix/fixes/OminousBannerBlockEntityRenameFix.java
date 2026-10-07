package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;

public class OminousBannerBlockEntityRenameFix extends NamedEntityFix {
	public OminousBannerBlockEntityRenameFix(Schema schema, boolean bl) {
		super(schema, bl, "OminousBannerBlockEntityRenameFix", References.BLOCK_ENTITY, "minecraft:banner");
	}

	@Override
	protected Typed<?> fix(Typed<?> typed) {
		OpticFinder<?> opticFinder = typed.getType().findField("CustomName");
		OpticFinder<Pair<String, String>> opticFinder2 = DSL.typeFinder((Type<Pair<String, String>>)this.getInputSchema().getType(References.TEXT_COMPONENT));
		return typed.updateTyped(
			opticFinder,
			typedx -> typedx.update(
				opticFinder2,
				pair -> pair.mapSecond(string -> string.replace("\"translate\":\"block.minecraft.illager_banner\"", "\"translate\":\"block.minecraft.ominous_banner\""))
			)
		);
	}
}
