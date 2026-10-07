package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.animal.cow.CowModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.MushroomCowRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.level.block.state.BlockState;

@Environment(EnvType.CLIENT)
public class MushroomCowMushroomLayer extends RenderLayer<MushroomCowRenderState, CowModel> {
	private final BlockRenderDispatcher blockRenderer;

	public MushroomCowMushroomLayer(RenderLayerParent<MushroomCowRenderState, CowModel> renderLayerParent, BlockRenderDispatcher blockRenderDispatcher) {
		super(renderLayerParent);
		this.blockRenderer = blockRenderDispatcher;
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, MushroomCowRenderState mushroomCowRenderState, float f, float g) {
		if (!mushroomCowRenderState.isBaby) {
			boolean bl = mushroomCowRenderState.appearsGlowing() && mushroomCowRenderState.isInvisible;
			if (!mushroomCowRenderState.isInvisible || bl) {
				BlockState blockState = mushroomCowRenderState.variant.getBlockState();
				int j = LivingEntityRenderer.getOverlayCoords(mushroomCowRenderState, 0.0F);
				BlockStateModel blockStateModel = this.blockRenderer.getBlockModel(blockState);
				poseStack.pushPose();
				poseStack.translate(0.2F, -0.35F, 0.5F);
				poseStack.mulPose(Axis.YP.rotationDegrees(-48.0F));
				poseStack.scale(-1.0F, -1.0F, 1.0F);
				poseStack.translate(-0.5F, -0.5F, -0.5F);
				this.submitMushroomBlock(poseStack, submitNodeCollector, i, bl, mushroomCowRenderState.outlineColor, blockState, j, blockStateModel);
				poseStack.popPose();
				poseStack.pushPose();
				poseStack.translate(0.2F, -0.35F, 0.5F);
				poseStack.mulPose(Axis.YP.rotationDegrees(42.0F));
				poseStack.translate(0.1F, 0.0F, -0.6F);
				poseStack.mulPose(Axis.YP.rotationDegrees(-48.0F));
				poseStack.scale(-1.0F, -1.0F, 1.0F);
				poseStack.translate(-0.5F, -0.5F, -0.5F);
				this.submitMushroomBlock(poseStack, submitNodeCollector, i, bl, mushroomCowRenderState.outlineColor, blockState, j, blockStateModel);
				poseStack.popPose();
				poseStack.pushPose();
				this.getParentModel().getHead().translateAndRotate(poseStack);
				poseStack.translate(0.0F, -0.7F, -0.2F);
				poseStack.mulPose(Axis.YP.rotationDegrees(-78.0F));
				poseStack.scale(-1.0F, -1.0F, 1.0F);
				poseStack.translate(-0.5F, -0.5F, -0.5F);
				this.submitMushroomBlock(poseStack, submitNodeCollector, i, bl, mushroomCowRenderState.outlineColor, blockState, j, blockStateModel);
				poseStack.popPose();
			}
		}
	}

	private void submitMushroomBlock(
		PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, boolean bl, int j, BlockState blockState, int k, BlockStateModel blockStateModel
	) {
		if (bl) {
			submitNodeCollector.submitBlockModel(poseStack, RenderTypes.outline(TextureAtlas.LOCATION_BLOCKS), blockStateModel, 0.0F, 0.0F, 0.0F, i, k, j);
		} else {
			submitNodeCollector.submitBlock(poseStack, blockState, i, k, j);
		}
	}
}
