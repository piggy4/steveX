package net.minecraft.world.attribute;

import com.google.common.collect.ImmutableBiMap;
import com.mojang.serialization.Codec;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Util;
import net.minecraft.world.attribute.modifier.AttributeModifier;

public record AttributeType<Value>(
	Codec<Value> valueCodec,
	Map<AttributeModifier.OperationId, AttributeModifier<Value, ?>> modifierLibrary,
	Codec<AttributeModifier<Value, ?>> modifierCodec,
	LerpFunction<Value> keyframeLerp,
	LerpFunction<Value> stateChangeLerp,
	LerpFunction<Value> spatialLerp,
	LerpFunction<Value> partialTickLerp
) {
	public static <Value> AttributeType<Value> ofInterpolated(
		Codec<Value> codec, Map<AttributeModifier.OperationId, AttributeModifier<Value, ?>> map, LerpFunction<Value> lerpFunction
	) {
		return ofInterpolated(codec, map, lerpFunction, lerpFunction);
	}

	public static <Value> AttributeType<Value> ofInterpolated(
		Codec<Value> codec, Map<AttributeModifier.OperationId, AttributeModifier<Value, ?>> map, LerpFunction<Value> lerpFunction, LerpFunction<Value> lerpFunction2
	) {
		return new AttributeType<>(codec, map, createModifierCodec(map), lerpFunction, lerpFunction, lerpFunction, lerpFunction2);
	}

	public static <Value> AttributeType<Value> ofNotInterpolated(Codec<Value> codec, Map<AttributeModifier.OperationId, AttributeModifier<Value, ?>> map) {
		return new AttributeType<>(
			codec, map, createModifierCodec(map), LerpFunction.ofStep(1.0F), LerpFunction.ofStep(0.0F), LerpFunction.ofStep(0.5F), LerpFunction.ofStep(0.0F)
		);
	}

	public static <Value> AttributeType<Value> ofNotInterpolated(Codec<Value> codec) {
		return ofNotInterpolated(codec, Map.of());
	}

	private static <Value> Codec<AttributeModifier<Value, ?>> createModifierCodec(Map<AttributeModifier.OperationId, AttributeModifier<Value, ?>> map) {
		ImmutableBiMap<AttributeModifier.OperationId, AttributeModifier<Value, ?>> immutableBiMap = ImmutableBiMap.<AttributeModifier.OperationId, AttributeModifier<Value, ?>>builder()
			.put(AttributeModifier.OperationId.OVERRIDE, AttributeModifier.override())
			.putAll(map)
			.buildOrThrow();
		return ExtraCodecs.idResolverCodec(AttributeModifier.OperationId.CODEC, immutableBiMap::get, immutableBiMap.inverse()::get);
	}

	public void checkAllowedModifier(AttributeModifier<Value, ?> attributeModifier) {
		if (attributeModifier != AttributeModifier.override() && !this.modifierLibrary.containsValue(attributeModifier)) {
			throw new IllegalArgumentException("Modifier " + attributeModifier + " is not valid for " + this);
		}
	}

	@Override
	public String toString() {
		return Util.getRegisteredName(BuiltInRegistries.ATTRIBUTE_TYPE, this);
	}
}
