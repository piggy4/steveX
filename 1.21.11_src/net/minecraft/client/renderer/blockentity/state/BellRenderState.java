package net.minecraft.client.renderer.blockentity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BellRenderState extends BlockEntityRenderState {
	public @Nullable Direction shakeDirection;
	public float ticks;
}
