package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;

public class OptionsGraphicsModeSplitFix extends DataFix {
	private final String newFieldName;
	private final String valueIfFast;
	private final String valueIfFancy;
	private final String valueIfFabulous;

	public OptionsGraphicsModeSplitFix(Schema schema, String string, String string2, String string3, String string4) {
		super(schema, true);
		this.newFieldName = string;
		this.valueIfFast = string2;
		this.valueIfFancy = string3;
		this.valueIfFabulous = string4;
	}

	@Override
	public TypeRewriteRule makeRule() {
		return this.fixTypeEverywhereTyped(
			"graphicsMode split to " + this.newFieldName,
			this.getInputSchema().getType(References.OPTIONS),
			typed -> typed.update(
				DSL.remainderFinder(),
				dynamic -> DataFixUtils.orElseGet(
					dynamic.get("graphicsMode").asString().map(string -> dynamic.set(this.newFieldName, dynamic.createString(this.getValue(string)))).result(),
					() -> dynamic.set(this.newFieldName, dynamic.createString(this.valueIfFancy))
				)
			)
		);
	}

	private String getValue(String string) {
		return switch (string) {
			case "2" -> this.valueIfFabulous;
			case "0" -> this.valueIfFast;
			default -> this.valueIfFancy;
		};
	}
}
