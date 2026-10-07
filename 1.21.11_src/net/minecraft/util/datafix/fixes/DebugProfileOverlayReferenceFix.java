package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;

public class DebugProfileOverlayReferenceFix extends DataFix {
	public DebugProfileOverlayReferenceFix(Schema schema) {
		super(schema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		return this.fixTypeEverywhereTyped(
			"DebugProfileOverlayReferenceFix",
			this.getInputSchema().getType(References.DEBUG_PROFILE),
			typed -> typed.update(
				DSL.remainderFinder(),
				dynamic -> dynamic.update(
					"custom",
					dynamicx -> dynamicx.updateMapValues(
						pair -> pair.mapSecond(dynamicxx -> dynamicxx.asString("").equals("inF3") ? dynamicxx.createString("inOverlay") : dynamicxx)
					)
				)
			)
		);
	}
}
