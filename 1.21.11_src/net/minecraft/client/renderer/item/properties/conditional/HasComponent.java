package net.minecraft.client.renderer.item.properties.conditional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record HasComponent(DataComponentType<?> componentType, boolean ignoreDefault) implements ConditionalItemModelProperty {
	public static final MapCodec<HasComponent> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				BuiltInRegistries.DATA_COMPONENT_TYPE.byNameCodec().fieldOf("component").forGetter(HasComponent::componentType),
				Codec.BOOL.optionalFieldOf("ignore_default", false).forGetter(HasComponent::ignoreDefault)
			)
			.apply(instance, HasComponent::new)
	);

	@Override
	public boolean get(ItemStack itemStack, @Nullable ClientLevel clientLevel, @Nullable LivingEntity livingEntity, int i, ItemDisplayContext itemDisplayContext) {
		return this.ignoreDefault ? itemStack.hasNonDefault(this.componentType) : itemStack.has(this.componentType);
	}

	@Override
	public MapCodec<HasComponent> type() {
		return MAP_CODEC;
	}
}
