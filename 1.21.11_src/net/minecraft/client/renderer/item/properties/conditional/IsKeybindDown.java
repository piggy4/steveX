package net.minecraft.client.renderer.item.properties.conditional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record IsKeybindDown(KeyMapping keybind) implements ConditionalItemModelProperty {
	private static final Codec<KeyMapping> KEYBIND_CODEC = Codec.STRING.comapFlatMap(string -> {
		KeyMapping keyMapping = KeyMapping.get(string);
		return keyMapping != null ? DataResult.success(keyMapping) : DataResult.error(() -> "Invalid keybind: " + string);
	}, KeyMapping::getName);
	public static final MapCodec<IsKeybindDown> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(KEYBIND_CODEC.fieldOf("keybind").forGetter(IsKeybindDown::keybind)).apply(instance, IsKeybindDown::new)
	);

	@Override
	public boolean get(ItemStack itemStack, @Nullable ClientLevel clientLevel, @Nullable LivingEntity livingEntity, int i, ItemDisplayContext itemDisplayContext) {
		return this.keybind.isDown();
	}

	@Override
	public MapCodec<IsKeybindDown> type() {
		return MAP_CODEC;
	}
}
