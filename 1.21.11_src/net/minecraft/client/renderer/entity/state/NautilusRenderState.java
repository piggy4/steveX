package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.animal.nautilus.ZombieNautilusVariant;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class NautilusRenderState extends LivingEntityRenderState {
	public ItemStack saddle = ItemStack.EMPTY;
	public ItemStack bodyArmorItem = ItemStack.EMPTY;
	public @Nullable ZombieNautilusVariant variant;
}
