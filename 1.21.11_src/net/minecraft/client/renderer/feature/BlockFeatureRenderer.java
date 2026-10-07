package net.minecraft.client.renderer.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

@Environment(EnvType.CLIENT)
public class BlockFeatureRenderer {
	private final PoseStack poseStack = new PoseStack();

	public void render(
		SubmitNodeCollection submitNodeCollection,
		MultiBufferSource.BufferSource bufferSource,
		BlockRenderDispatcher blockRenderDispatcher,
		OutlineBufferSource outlineBufferSource
	) {
		for (SubmitNodeStorage.MovingBlockSubmit movingBlockSubmit : submitNodeCollection.getMovingBlockSubmits()) {
			MovingBlockRenderState movingBlockRenderState = movingBlockSubmit.movingBlockRenderState();
			BlockState blockState = movingBlockRenderState.blockState;
			List<BlockModelPart> list = blockRenderDispatcher.getBlockModel(blockState)
				.collectParts(RandomSource.create(blockState.getSeed(movingBlockRenderState.randomSeedPos)));
			PoseStack poseStack = new PoseStack();
			poseStack.mulPose(movingBlockSubmit.pose());
			blockRenderDispatcher.getModelRenderer()
				.tesselateBlock(
					movingBlockRenderState,
					list,
					blockState,
					movingBlockRenderState.blockPos,
					poseStack,
					bufferSource.getBuffer(ItemBlockRenderTypes.getMovingBlockRenderType(blockState)),
					false,
					OverlayTexture.NO_OVERLAY
				);
		}

		for (SubmitNodeStorage.BlockSubmit blockSubmit : submitNodeCollection.getBlockSubmits()) {
			this.poseStack.pushPose();
			this.poseStack.last().set(blockSubmit.pose());
			blockRenderDispatcher.renderSingleBlock(blockSubmit.state(), this.poseStack, bufferSource, blockSubmit.lightCoords(), blockSubmit.overlayCoords());
			if (blockSubmit.outlineColor() != 0) {
				outlineBufferSource.setColor(blockSubmit.outlineColor());
				blockRenderDispatcher.renderSingleBlock(blockSubmit.state(), this.poseStack, outlineBufferSource, blockSubmit.lightCoords(), blockSubmit.overlayCoords());
			}

			this.poseStack.popPose();
		}

		for (SubmitNodeStorage.BlockModelSubmit blockModelSubmit : submitNodeCollection.getBlockModelSubmits()) {
			ModelBlockRenderer.renderModel(
				blockModelSubmit.pose(),
				bufferSource.getBuffer(blockModelSubmit.renderType()),
				blockModelSubmit.model(),
				blockModelSubmit.r(),
				blockModelSubmit.g(),
				blockModelSubmit.b(),
				blockModelSubmit.lightCoords(),
				blockModelSubmit.overlayCoords()
			);
			if (blockModelSubmit.outlineColor() != 0) {
				outlineBufferSource.setColor(blockModelSubmit.outlineColor());
				ModelBlockRenderer.renderModel(
					blockModelSubmit.pose(),
					outlineBufferSource.getBuffer(blockModelSubmit.renderType()),
					blockModelSubmit.model(),
					blockModelSubmit.r(),
					blockModelSubmit.g(),
					blockModelSubmit.b(),
					blockModelSubmit.lightCoords(),
					blockModelSubmit.overlayCoords()
				);
			}
		}
	}
}
