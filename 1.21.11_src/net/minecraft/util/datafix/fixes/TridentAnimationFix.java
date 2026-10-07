package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import org.jspecify.annotations.Nullable;

public class TridentAnimationFix extends DataComponentRemainderFix {
	public TridentAnimationFix(Schema schema) {
		super(schema, "TridentAnimationFix", "minecraft:consumable");
	}

	@Override
	protected <T> @Nullable Dynamic<T> fixComponent(Dynamic<T> dynamic) {
		return dynamic.update("animation", dynamicx -> {
			String string = dynamicx.asString().result().orElse("");
			return "spear".equals(string) ? dynamicx.createString("trident") : dynamicx;
		});
	}
}
