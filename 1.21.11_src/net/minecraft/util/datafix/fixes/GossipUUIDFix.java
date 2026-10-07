package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;

public class GossipUUIDFix extends NamedEntityFix {
	public GossipUUIDFix(Schema schema, String string) {
		super(schema, false, "Gossip for for " + string, References.ENTITY, string);
	}

	@Override
	protected Typed<?> fix(Typed<?> typed) {
		return typed.update(
			DSL.remainderFinder(),
			dynamic -> dynamic.update(
				"Gossips",
				dynamicx -> DataFixUtils.orElse(
					dynamicx.asStreamOpt()
						.result()
						.map(stream -> stream.map(dynamicxx -> AbstractUUIDFix.replaceUUIDLeastMost(dynamicxx, "Target", "Target").orElse(dynamicxx)))
						.map(dynamicx::createList),
					dynamicx
				)
			)
		);
	}
}
