package net.minecraft.client.renderer.item.properties.numeric;

import com.mojang.serialization.MapCodec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record Cooldown() implements RangeSelectItemModelProperty {
	public static final MapCodec<Cooldown> MAP_CODEC = MapCodec.unit(new Cooldown());

	@Override
	public float get(ItemStack itemStack, @Nullable ClientLevel clientLevel, @Nullable ItemOwner itemOwner, int i) {
		return itemOwner != null && itemOwner.asLivingEntity() instanceof Player player ? player.getCooldowns().getCooldownPercent(itemStack, 0.0F) : 0.0F;
	}

	@Override
	public MapCodec<Cooldown> type() {
		return MAP_CODEC;
	}
}
