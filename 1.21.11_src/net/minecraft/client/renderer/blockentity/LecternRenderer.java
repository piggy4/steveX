package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.LecternRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class LecternRenderer implements BlockEntityRenderer<LecternBlockEntity, LecternRenderState> {
	private final MaterialSet materials;
	private final BookModel bookModel;
	private final BookModel.State bookState = new BookModel.State(0.0F, 0.1F, 0.9F, 1.2F);

	public LecternRenderer(BlockEntityRendererProvider.Context context) {
		this.materials = context.materials();
		this.bookModel = new BookModel(context.bakeLayer(ModelLayers.BOOK));
	}

	public LecternRenderState createRenderState() {
		return new LecternRenderState();
	}

	public void extractRenderState(
		LecternBlockEntity lecternBlockEntity,
		LecternRenderState lecternRenderState,
		float f,
		Vec3 vec3,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer.super.extractRenderState(lecternBlockEntity, lecternRenderState, f, vec3, crumblingOverlay);
		lecternRenderState.hasBook = lecternBlockEntity.getBlockState().getValue(LecternBlock.HAS_BOOK);
		lecternRenderState.yRot = lecternBlockEntity.getBlockState().getValue(LecternBlock.FACING).getClockWise().toYRot();
	}

	public void submit(LecternRenderState lecternRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		if (lecternRenderState.hasBook) {
			poseStack.pushPose();
			poseStack.translate(0.5F, 1.0625F, 0.5F);
			poseStack.mulPose(Axis.YP.rotationDegrees(-lecternRenderState.yRot));
			poseStack.mulPose(Axis.ZP.rotationDegrees(67.5F));
			poseStack.translate(0.0F, -0.125F, 0.0F);
			submitNodeCollector.submitModel(
				this.bookModel,
				this.bookState,
				poseStack,
				EnchantTableRenderer.BOOK_TEXTURE.renderType(RenderTypes::entitySolid),
				lecternRenderState.lightCoords,
				OverlayTexture.NO_OVERLAY,
				-1,
				this.materials.get(EnchantTableRenderer.BOOK_TEXTURE),
				0,
				lecternRenderState.breakProgress
			);
			poseStack.popPose();
		}
	}
}
