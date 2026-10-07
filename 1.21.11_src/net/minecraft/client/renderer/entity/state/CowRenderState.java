package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.animal.cow.CowVariant;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class CowRenderState extends LivingEntityRenderState {
	public @Nullable CowVariant variant;
}
