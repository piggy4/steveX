package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;

public class OptionsMusicToastFix extends DataFix {
	public OptionsMusicToastFix(Schema schema, boolean bl) {
		super(schema, bl);
	}

	@Override
	public TypeRewriteRule makeRule() {
		return this.fixTypeEverywhereTyped(
			"OptionsMusicToastFix",
			this.getInputSchema().getType(References.OPTIONS),
			typed -> typed.update(
				DSL.remainderFinder(),
				dynamic -> dynamic.renameAndFixField(
					"showNowPlayingToast", "musicToast", dynamic2 -> dynamic.createString(dynamic2.asString("false").equals("false") ? "never" : "pause_and_toast")
				)
			)
		);
	}
}
