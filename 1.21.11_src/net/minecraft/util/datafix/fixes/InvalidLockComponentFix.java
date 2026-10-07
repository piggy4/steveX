package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

public class InvalidLockComponentFix extends DataComponentRemainderFix {
	private static final Optional<String> INVALID_LOCK_CUSTOM_NAME = Optional.of("\"\"");

	public InvalidLockComponentFix(Schema schema) {
		super(schema, "InvalidLockComponentPredicateFix", "minecraft:lock");
	}

	@Override
	protected <T> @Nullable Dynamic<T> fixComponent(Dynamic<T> dynamic) {
		return fixLock(dynamic);
	}

	public static <T> @Nullable Dynamic<T> fixLock(Dynamic<T> dynamic) {
		return isBrokenLock(dynamic) ? null : dynamic;
	}

	private static <T> boolean isBrokenLock(Dynamic<T> dynamic) {
		return isMapWithOneField(
			dynamic,
			"components",
			dynamicx -> isMapWithOneField(dynamicx, "minecraft:custom_name", dynamicxx -> dynamicxx.asString().result().equals(INVALID_LOCK_CUSTOM_NAME))
		);
	}

	private static <T> boolean isMapWithOneField(Dynamic<T> dynamic, String string, Predicate<Dynamic<T>> predicate) {
		Optional<Map<Dynamic<T>, Dynamic<T>>> optional = dynamic.getMapValues().result();
		return !optional.isEmpty() && optional.get().size() == 1 ? dynamic.get(string).result().filter(predicate).isPresent() : false;
	}
}
