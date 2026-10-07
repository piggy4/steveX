package net.minecraft.client.renderer.blockentity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.CrashReportCategory;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState} injected by mod fabric-rendering-v1</p>
 */
@Environment(EnvType.CLIENT)
public class BlockEntityRenderState implements FabricRenderState {
	public BlockPos blockPos = BlockPos.ZERO;
	public BlockState blockState = Blocks.AIR.defaultBlockState();
	public BlockEntityType<?> blockEntityType = BlockEntityType.TEST_BLOCK;
	public int lightCoords;
	public ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress;

	public static void extractBase(
		BlockEntity blockEntity, BlockEntityRenderState blockEntityRenderState, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		blockEntityRenderState.blockPos = blockEntity.getBlockPos();
		blockEntityRenderState.blockState = blockEntity.getBlockState();
		blockEntityRenderState.blockEntityType = blockEntity.getType();
		blockEntityRenderState.lightCoords = blockEntity.getLevel() != null
			? LevelRenderer.getLightColor(blockEntity.getLevel(), blockEntity.getBlockPos())
			: 15728880;
		blockEntityRenderState.breakProgress = crumblingOverlay;
	}

	public void fillCrashReportCategory(CrashReportCategory crashReportCategory) {
		crashReportCategory.setDetail("BlockEntityRenderState", this.getClass().getCanonicalName());
		crashReportCategory.setDetail("Position", this.blockPos);
		crashReportCategory.setDetail("Block state", this.blockState::toString);
	}
}
