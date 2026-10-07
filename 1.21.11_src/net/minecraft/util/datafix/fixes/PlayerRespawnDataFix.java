package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;

public class PlayerRespawnDataFix extends DataFix {
	public PlayerRespawnDataFix(Schema schema) {
		super(schema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		return this.fixTypeEverywhereTyped(
			"PlayerRespawnDataFix",
			this.getInputSchema().getType(References.PLAYER),
			typed -> typed.update(
				DSL.remainderFinder(),
				dynamic -> dynamic.update(
					"respawn",
					dynamicx -> dynamicx.set("dimension", dynamicx.createString(dynamicx.get("dimension").asString("minecraft:overworld")))
						.set("yaw", dynamicx.createFloat(dynamicx.get("angle").asFloat(0.0F)))
						.set("pitch", dynamicx.createFloat(0.0F))
						.remove("angle")
				)
			)
		);
	}
}
