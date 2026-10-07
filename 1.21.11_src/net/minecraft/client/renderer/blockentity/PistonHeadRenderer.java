package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.PistonHeadRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class PistonHeadRenderer implements BlockEntityRenderer<PistonMovingBlockEntity, PistonHeadRenderState> {
	public PistonHeadRenderState createRenderState() {
		return new PistonHeadRenderState();
	}

	public void extractRenderState(
		PistonMovingBlockEntity pistonMovingBlockEntity,
		PistonHeadRenderState pistonHeadRenderState,
		float f,
		Vec3 vec3,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer.super.extractRenderState(pistonMovingBlockEntity, pistonHeadRenderState, f, vec3, crumblingOverlay);
		pistonHeadRenderState.xOffset = pistonMovingBlockEntity.getXOff(f);
		pistonHeadRenderState.yOffset = pistonMovingBlockEntity.getYOff(f);
		pistonHeadRenderState.zOffset = pistonMovingBlockEntity.getZOff(f);
		pistonHeadRenderState.block = null;
		pistonHeadRenderState.base = null;
		BlockState blockState = pistonMovingBlockEntity.getMovedState();
		Level level = pistonMovingBlockEntity.getLevel();
		if (level != null && !blockState.isAir()) {
			BlockPos blockPos = pistonMovingBlockEntity.getBlockPos().relative(pistonMovingBlockEntity.getMovementDirection().getOpposite());
			Holder<Biome> holder = level.getBiome(blockPos);
			if (blockState.is(Blocks.PISTON_HEAD) && pistonMovingBlockEntity.getProgress(f) <= 4.0F) {
				blockState = blockState.setValue(PistonHeadBlock.SHORT, pistonMovingBlockEntity.getProgress(f) <= 0.5F);
				pistonHeadRenderState.block = createMovingBlock(blockPos, blockState, holder, level);
			} else if (pistonMovingBlockEntity.isSourcePiston() && !pistonMovingBlockEntity.isExtending()) {
				PistonType pistonType = blockState.is(Blocks.STICKY_PISTON) ? PistonType.STICKY : PistonType.DEFAULT;
				BlockState blockState2 = Blocks.PISTON_HEAD
					.defaultBlockState()
					.setValue(PistonHeadBlock.TYPE, pistonType)
					.setValue(PistonHeadBlock.FACING, blockState.getValue(PistonBaseBlock.FACING));
				blockState2 = blockState2.setValue(PistonHeadBlock.SHORT, pistonMovingBlockEntity.getProgress(f) >= 0.5F);
				pistonHeadRenderState.block = createMovingBlock(blockPos, blockState2, holder, level);
				BlockPos blockPos2 = blockPos.relative(pistonMovingBlockEntity.getMovementDirection());
				blockState = blockState.setValue(PistonBaseBlock.EXTENDED, true);
				pistonHeadRenderState.base = createMovingBlock(blockPos2, blockState, holder, level);
			} else {
				pistonHeadRenderState.block = createMovingBlock(blockPos, blockState, holder, level);
			}
		}
	}

	public void submit(
		PistonHeadRenderState pistonHeadRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		if (pistonHeadRenderState.block != null) {
			poseStack.pushPose();
			poseStack.translate(pistonHeadRenderState.xOffset, pistonHeadRenderState.yOffset, pistonHeadRenderState.zOffset);
			submitNodeCollector.submitMovingBlock(poseStack, pistonHeadRenderState.block);
			poseStack.popPose();
			if (pistonHeadRenderState.base != null) {
				submitNodeCollector.submitMovingBlock(poseStack, pistonHeadRenderState.base);
			}
		}
	}

	private static MovingBlockRenderState createMovingBlock(BlockPos blockPos, BlockState blockState, Holder<Biome> holder, Level level) {
		MovingBlockRenderState movingBlockRenderState = new MovingBlockRenderState();
		movingBlockRenderState.randomSeedPos = blockPos;
		movingBlockRenderState.blockPos = blockPos;
		movingBlockRenderState.blockState = blockState;
		movingBlockRenderState.biome = holder;
		movingBlockRenderState.level = level;
		return movingBlockRenderState;
	}

	@Override
	public int getViewDistance() {
		return 68;
	}
}
