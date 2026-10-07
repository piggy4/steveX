package net.minecraft.util.datafix.fixes;

import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public class LockComponentPredicateFix extends DataComponentRemainderFix {
	public static final Escaper ESCAPER = Escapers.builder().addEscape('"', "\\\"").addEscape('\\', "\\\\").build();

	public LockComponentPredicateFix(Schema schema) {
		super(schema, "LockComponentPredicateFix", "minecraft:lock");
	}

	@Override
	protected <T> @Nullable Dynamic<T> fixComponent(Dynamic<T> dynamic) {
		return fixLock(dynamic);
	}

	public static <T> @Nullable Dynamic<T> fixLock(Dynamic<T> dynamic) {
		Optional<String> optional = dynamic.asString().result();
		if (optional.isEmpty()) {
			return null;
		}

		if (optional.get().isEmpty()) {
			return null;
		}

		Dynamic<T> dynamic2 = dynamic.createString("\"" + ESCAPER.escape(optional.get()) + "\"");
		Dynamic<T> dynamic3 = dynamic.emptyMap().set("minecraft:custom_name", dynamic2);
		return dynamic.emptyMap().set("components", dynamic3);
	}
}
