package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.EnchantTableRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class EnchantTableRenderer implements BlockEntityRenderer<EnchantingTableBlockEntity, EnchantTableRenderState> {
	public static final Material BOOK_TEXTURE = Sheets.BLOCK_ENTITIES_MAPPER.defaultNamespaceApply("enchanting_table_book");
	private final MaterialSet materials;
	private final BookModel bookModel;

	public EnchantTableRenderer(BlockEntityRendererProvider.Context context) {
		this.materials = context.materials();
		this.bookModel = new BookModel(context.bakeLayer(ModelLayers.BOOK));
	}

	public EnchantTableRenderState createRenderState() {
		return new EnchantTableRenderState();
	}

	public void extractRenderState(
		EnchantingTableBlockEntity enchantingTableBlockEntity,
		EnchantTableRenderState enchantTableRenderState,
		float f,
		Vec3 vec3,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer.super.extractRenderState(enchantingTableBlockEntity, enchantTableRenderState, f, vec3, crumblingOverlay);
		enchantTableRenderState.flip = Mth.lerp(f, enchantingTableBlockEntity.oFlip, enchantingTableBlockEntity.flip);
		enchantTableRenderState.open = Mth.lerp(f, enchantingTableBlockEntity.oOpen, enchantingTableBlockEntity.open);
		enchantTableRenderState.time = enchantingTableBlockEntity.time + f;
		float g = enchantingTableBlockEntity.rot - enchantingTableBlockEntity.oRot;

		while (g >= (float) Math.PI) {
			g -= (float) (Math.PI * 2);
		}

		while (g < (float) -Math.PI) {
			g += (float) (Math.PI * 2);
		}

		enchantTableRenderState.yRot = enchantingTableBlockEntity.oRot + g * f;
	}

	public void submit(
		EnchantTableRenderState enchantTableRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.75F, 0.5F);
		poseStack.translate(0.0F, 0.1F + Mth.sin(enchantTableRenderState.time * 0.1F) * 0.01F, 0.0F);
		float f = enchantTableRenderState.yRot;
		poseStack.mulPose(Axis.YP.rotation(-f));
		poseStack.mulPose(Axis.ZP.rotationDegrees(80.0F));
		float g = Mth.frac(enchantTableRenderState.flip + 0.25F) * 1.6F - 0.3F;
		float h = Mth.frac(enchantTableRenderState.flip + 0.75F) * 1.6F - 0.3F;
		BookModel.State state = new BookModel.State(enchantTableRenderState.time, Mth.clamp(g, 0.0F, 1.0F), Mth.clamp(h, 0.0F, 1.0F), enchantTableRenderState.open);
		submitNodeCollector.submitModel(
			this.bookModel,
			state,
			poseStack,
			BOOK_TEXTURE.renderType(RenderTypes::entitySolid),
			enchantTableRenderState.lightCoords,
			OverlayTexture.NO_OVERLAY,
			-1,
			this.materials.get(BOOK_TEXTURE),
			0,
			enchantTableRenderState.breakProgress
		);
		poseStack.popPose();
	}
}
