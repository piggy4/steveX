package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import net.minecraft.util.Util;

public class OminousBannerRenameFix extends ItemStackTagFix {
	public OminousBannerRenameFix(Schema schema) {
		super(schema, "OminousBannerRenameFix", string -> string.equals("minecraft:white_banner"));
	}

	private <T> Dynamic<T> fixItemStackTag(Dynamic<T> dynamic) {
		return dynamic.update(
			"display",
			dynamicx -> dynamicx.update(
				"Name",
				dynamicxx -> {
					Optional<String> optional = dynamicxx.asString().result();
					return optional.isPresent()
						? dynamicxx.createString(optional.get().replace("\"translate\":\"block.minecraft.illager_banner\"", "\"translate\":\"block.minecraft.ominous_banner\""))
						: dynamicxx;
				}
			)
		);
	}

	@Override
	protected Typed<?> fixItemStackTag(Typed<?> typed) {
		return Util.writeAndReadTypedOrThrow(typed, typed.getType(), this::fixItemStackTag);
	}
}
