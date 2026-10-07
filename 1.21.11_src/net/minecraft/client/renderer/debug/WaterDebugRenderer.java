package net.minecraft.client.renderer.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class WaterDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private final Minecraft minecraft;

	public WaterDebugRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void emitGizmos(double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum, float g) {
		BlockPos blockPos = this.minecraft.player.blockPosition();
		LevelReader levelReader = this.minecraft.player.level();

		for (BlockPos blockPos2 : BlockPos.betweenClosed(blockPos.offset(-10, -10, -10), blockPos.offset(10, 10, 10))) {
			FluidState fluidState = levelReader.getFluidState(blockPos2);
			if (fluidState.is(FluidTags.WATER)) {
				double h = blockPos2.getY() + fluidState.getHeight(levelReader, blockPos2);
				Gizmos.cuboid(
					new AABB(blockPos2.getX() + 0.01F, blockPos2.getY() + 0.01F, blockPos2.getZ() + 0.01F, blockPos2.getX() + 0.99F, h, blockPos2.getZ() + 0.99F),
					GizmoStyle.fill(ARGB.colorFromFloat(0.15F, 0.0F, 1.0F, 0.0F))
				);
			}
		}

		for (BlockPos blockPos2 : BlockPos.betweenClosed(blockPos.offset(-10, -10, -10), blockPos.offset(10, 10, 10))) {
			FluidState fluidState = levelReader.getFluidState(blockPos2);
			if (fluidState.is(FluidTags.WATER)) {
				Gizmos.billboardText(
					String.valueOf(fluidState.getAmount()),
					Vec3.atLowerCornerWithOffset(blockPos2, 0.5, fluidState.getHeight(levelReader, blockPos2), 0.5),
					TextGizmo.Style.forColorAndCentered(-16777216)
				);
			}
		}
	}
}
