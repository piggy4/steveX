package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.block.MovingBlockRenderState;

@Environment(EnvType.CLIENT)
public class FallingBlockRenderState extends EntityRenderState {
	public MovingBlockRenderState movingBlockRenderState = new MovingBlockRenderState();
}
