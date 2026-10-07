package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.FallingBlockRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

@Environment(EnvType.CLIENT)
public class FallingBlockRenderer extends EntityRenderer<FallingBlockEntity, FallingBlockRenderState> {
	public FallingBlockRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.5F;
	}

	public boolean shouldRender(FallingBlockEntity fallingBlockEntity, Frustum frustum, double d, double e, double f) {
		return !super.shouldRender(fallingBlockEntity, frustum, d, e, f)
			? false
			: fallingBlockEntity.getBlockState() != fallingBlockEntity.level().getBlockState(fallingBlockEntity.blockPosition());
	}

	public void submit(
		FallingBlockRenderState fallingBlockRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		BlockState blockState = fallingBlockRenderState.movingBlockRenderState.blockState;
		if (blockState.getRenderShape() == RenderShape.MODEL) {
			poseStack.pushPose();
			poseStack.translate(-0.5, 0.0, -0.5);
			submitNodeCollector.submitMovingBlock(poseStack, fallingBlockRenderState.movingBlockRenderState);
			poseStack.popPose();
			super.submit(fallingBlockRenderState, poseStack, submitNodeCollector, cameraRenderState);
		}
	}

	public FallingBlockRenderState createRenderState() {
		return new FallingBlockRenderState();
	}

	public void extractRenderState(FallingBlockEntity fallingBlockEntity, FallingBlockRenderState fallingBlockRenderState, float f) {
		super.extractRenderState(fallingBlockEntity, fallingBlockRenderState, f);
		BlockPos blockPos = BlockPos.containing(fallingBlockEntity.getX(), fallingBlockEntity.getBoundingBox().maxY, fallingBlockEntity.getZ());
		fallingBlockRenderState.movingBlockRenderState.randomSeedPos = fallingBlockEntity.getStartPos();
		fallingBlockRenderState.movingBlockRenderState.blockPos = blockPos;
		fallingBlockRenderState.movingBlockRenderState.blockState = fallingBlockEntity.getBlockState();
		fallingBlockRenderState.movingBlockRenderState.biome = fallingBlockEntity.level().getBiome(blockPos);
		fallingBlockRenderState.movingBlockRenderState.level = fallingBlockEntity.level();
	}
}
