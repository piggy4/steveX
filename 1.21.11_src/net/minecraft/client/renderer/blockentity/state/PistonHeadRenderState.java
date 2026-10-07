package net.minecraft.client.renderer.blockentity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class PistonHeadRenderState extends BlockEntityRenderState {
	public @Nullable MovingBlockRenderState block;
	public @Nullable MovingBlockRenderState base;
	public float xOffset;
	public float yOffset;
	public float zOffset;
}
