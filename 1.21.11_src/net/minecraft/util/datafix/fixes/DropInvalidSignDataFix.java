package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Streams;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.LegacyComponentDataFixUtils;

public class DropInvalidSignDataFix extends DataFix {
	private final String entityName;

	public DropInvalidSignDataFix(Schema schema, String string) {
		super(schema, false);
		this.entityName = string;
	}

	private <T> Dynamic<T> fix(Dynamic<T> dynamic) {
		dynamic = dynamic.update("front_text", DropInvalidSignDataFix::fixText);
		dynamic = dynamic.update("back_text", DropInvalidSignDataFix::fixText);

		for (String string : BlockEntitySignDoubleSidedEditableTextFix.FIELDS_TO_DROP) {
			dynamic = dynamic.remove(string);
		}

		return dynamic;
	}

	private static <T> Dynamic<T> fixText(Dynamic<T> dynamic) {
		Optional<Stream<Dynamic<T>>> optional = dynamic.get("filtered_messages").asStreamOpt().result();
		if (optional.isEmpty()) {
			return dynamic;
		}

		Dynamic<T> dynamic2 = LegacyComponentDataFixUtils.createEmptyComponent(dynamic.getOps());
		List<Dynamic<T>> list = dynamic.get("messages").asStreamOpt().result().orElse(Stream.of()).toList();
		List<Dynamic<T>> list2 = Streams.mapWithIndex(optional.get(), (dynamic2x, l) -> {
			Dynamic<T> dynamic3 = l < list.size() ? list.get((int)l) : dynamic2;
			return dynamic2x.equals(dynamic2) ? dynamic3 : dynamic2x;
		}).toList();
		return list2.equals(list) ? dynamic.remove("filtered_messages") : dynamic.set("filtered_messages", dynamic.createList(list2.stream()));
	}

	@Override
	public TypeRewriteRule makeRule() {
		Type<?> type = this.getInputSchema().getType(References.BLOCK_ENTITY);
		Type<?> type2 = this.getInputSchema().getChoiceType(References.BLOCK_ENTITY, this.entityName);
		OpticFinder<?> opticFinder = DSL.namedChoice(this.entityName, type2);
		return this.fixTypeEverywhereTyped("DropInvalidSignDataFix for " + this.entityName, type, typed -> typed.updateTyped(opticFinder, type2, typedx -> {
			boolean bl = typedx.get(DSL.remainderFinder()).get("_filtered_correct").asBoolean(false);
			return bl ? typedx.update(DSL.remainderFinder(), dynamic -> dynamic.remove("_filtered_correct")) : Util.writeAndReadTypedOrThrow(typedx, type2, this::fix);
		}));
	}
}
