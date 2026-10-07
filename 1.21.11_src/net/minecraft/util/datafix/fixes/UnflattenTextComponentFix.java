package net.minecraft.util.datafix.fixes;

import com.google.gson.JsonElement;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.LenientJsonParser;
import net.minecraft.util.Util;
import org.slf4j.Logger;

public class UnflattenTextComponentFix extends DataFix {
	private static final Logger LOGGER = LogUtils.getLogger();

	public UnflattenTextComponentFix(Schema schema) {
		super(schema, true);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<Pair<String, String>> type = (Type<Pair<String, String>>)this.getInputSchema().getType(References.TEXT_COMPONENT);
		Type<?> type2 = this.getOutputSchema().getType(References.TEXT_COMPONENT);
		return this.createFixer(type, type2);
	}

	private <T> TypeRewriteRule createFixer(Type<Pair<String, String>> type, Type<T> type2) {
		return this.fixTypeEverywhere(
			"UnflattenTextComponentFix", type, type2, dynamicOps -> pair -> Util.readTypedOrThrow(type2, unflattenJson(dynamicOps, pair.getSecond()), true).getValue()
		);
	}

	private static <T> Dynamic<T> unflattenJson(DynamicOps<T> dynamicOps, String string) {
		try {
			JsonElement jsonElement = LenientJsonParser.parse(string);
			if (!jsonElement.isJsonNull()) {
				return new Dynamic<>(dynamicOps, JsonOps.INSTANCE.convertTo(dynamicOps, jsonElement));
			}
		} catch (Exception exception) {
			LOGGER.error("Failed to unflatten text component json: {}", string, exception);
		}

		return new Dynamic<>(dynamicOps, dynamicOps.createString(string));
	}
}
