package net.minecraft.client.renderer.blockentity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SkullBlock;

@Environment(EnvType.CLIENT)
public class SkullBlockRenderState extends BlockEntityRenderState {
	public float animationProgress;
	public Direction direction = Direction.NORTH;
	public float rotationDegrees;
	public SkullBlock.Type skullType = SkullBlock.Types.ZOMBIE;
	public RenderType renderType;
}
