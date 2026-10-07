package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;

public class WorldBorderWarningTimeFix extends DataFix {
	public WorldBorderWarningTimeFix(Schema schema) {
		super(schema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		return this.writeFixAndRead(
			"WorldBorderWarningTimeFix",
			this.getInputSchema().getType(References.SAVED_DATA_WORLD_BORDER),
			this.getOutputSchema().getType(References.SAVED_DATA_WORLD_BORDER),
			dynamic -> dynamic.update("data", dynamicx -> dynamicx.update("warning_time", dynamic2 -> dynamicx.createInt(dynamic2.asInt(15) * 20)))
		);
	}
}
