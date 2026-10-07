package net.minecraft.client.renderer.blockentity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.CopperGolemStatueBlock;

@Environment(EnvType.CLIENT)
public class CopperGolemStatueRenderState extends BlockEntityRenderState {
	public CopperGolemStatueBlock.Pose pose = CopperGolemStatueBlock.Pose.STANDING;
	public Direction direction = Direction.NORTH;
}
