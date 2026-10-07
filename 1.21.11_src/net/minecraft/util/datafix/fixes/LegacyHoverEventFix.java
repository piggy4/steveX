package net.minecraft.util.datafix.fixes;

import com.google.gson.JsonElement;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Util;

public class LegacyHoverEventFix extends DataFix {
	public LegacyHoverEventFix(Schema schema) {
		super(schema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<? extends Pair<String, ?>> type = (Type<? extends Pair<String, ?>>)this.getInputSchema().getType(References.TEXT_COMPONENT).findFieldType("hoverEvent");
		return this.createFixer(this.getInputSchema().getTypeRaw(References.TEXT_COMPONENT), type);
	}

	private <C, H extends Pair<String, ?>> TypeRewriteRule createFixer(Type<C> type, Type<H> type2) {
		Type<Pair<String, Either<Either<String, List<C>>, Pair<Either<List<C>, Unit>, Pair<Either<C, Unit>, Pair<Either<H, Unit>, Dynamic<?>>>>>>> type3 = DSL.named(
			References.TEXT_COMPONENT.typeName(),
			DSL.or(
				DSL.or(DSL.string(), DSL.list(type)),
				DSL.and(
					DSL.optional(DSL.field("extra", DSL.list(type))),
					DSL.optional(DSL.field("separator", type)),
					DSL.optional(DSL.field("hoverEvent", type2)),
					DSL.remainderType()
				)
			)
		);
		if (!type3.equals(this.getInputSchema().getType(References.TEXT_COMPONENT))) {
			throw new IllegalStateException(
				"Text component type did not match, expected " + type3 + " but got " + this.getInputSchema().getType(References.TEXT_COMPONENT)
			);
		} else {
			return this.fixTypeEverywhere(
				"LegacyHoverEventFix",
				type3,
				dynamicOps -> pair -> pair.mapSecond(either -> either.mapRight(pairx -> pairx.mapSecond(pairxx -> pairxx.mapSecond(pairxxx -> {
					Dynamic<?> dynamic = (Dynamic<?>)pairxxx.getSecond();
					Optional<? extends Dynamic<?>> optional = dynamic.get("hoverEvent").result();
					if (optional.isEmpty()) {
						return pairxxx;
					}

					Optional<? extends Dynamic<?>> optional2 = optional.get().get("value").result();
					if (optional2.isEmpty()) {
						return pairxxx;
					}

					String string = ((Either)pairxxx.getFirst()).left().map(Pair::getFirst).orElse("");
					H pair2 = this.fixHoverEvent(type2, string, (Dynamic<?>)optional.get());
					return pairxxx.mapFirst(eitherx -> Either.left(pair2));
				}))))
			);
		}
	}

	private <H> H fixHoverEvent(Type<H> type, String string, Dynamic<?> dynamic) {
		return "show_text".equals(string) ? fixShowTextHover(type, dynamic) : createPlaceholderHover(type, dynamic);
	}

	private static <H> H fixShowTextHover(Type<H> type, Dynamic<?> dynamic) {
		Dynamic<?> dynamic2 = dynamic.renameField("value", "contents");
		return Util.readTypedOrThrow(type, dynamic2).getValue();
	}

	private static <H> H createPlaceholderHover(Type<H> type, Dynamic<?> dynamic) {
		JsonElement jsonElement = dynamic.convert(JsonOps.INSTANCE).getValue();
		Dynamic<?> dynamic2 = new Dynamic<>(
			JavaOps.INSTANCE, Map.of("action", "show_text", "contents", Map.<String, String>of("text", "Legacy hoverEvent: " + GsonHelper.toStableString(jsonElement)))
		);
		return Util.readTypedOrThrow(type, dynamic2).getValue();
	}
}
