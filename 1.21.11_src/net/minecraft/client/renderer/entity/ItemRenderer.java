package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import com.mojang.math.MatrixUtil;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemDisplayContext;

@Environment(EnvType.CLIENT)
public class ItemRenderer {
	public static final Identifier ENCHANTED_GLINT_ARMOR = Identifier.withDefaultNamespace("textures/misc/enchanted_glint_armor.png");
	public static final Identifier ENCHANTED_GLINT_ITEM = Identifier.withDefaultNamespace("textures/misc/enchanted_glint_item.png");
	public static final float SPECIAL_FOIL_UI_SCALE = 0.5F;
	public static final float SPECIAL_FOIL_FIRST_PERSON_SCALE = 0.75F;
	public static final float SPECIAL_FOIL_TEXTURE_SCALE = 0.0078125F;
	public static final int NO_TINT = -1;

	public static void renderItem(
		ItemDisplayContext itemDisplayContext,
		PoseStack poseStack,
		MultiBufferSource multiBufferSource,
		int i,
		int j,
		int[] is,
		List<BakedQuad> list,
		RenderType renderType,
		ItemStackRenderState.FoilType foilType
	) {
		VertexConsumer vertexConsumer;
		if (foilType == ItemStackRenderState.FoilType.SPECIAL) {
			PoseStack.Pose pose = poseStack.last().copy();
			if (itemDisplayContext == ItemDisplayContext.GUI) {
				MatrixUtil.mulComponentWise(pose.pose(), 0.5F);
			} else if (itemDisplayContext.firstPerson()) {
				MatrixUtil.mulComponentWise(pose.pose(), 0.75F);
			}

			vertexConsumer = getSpecialFoilBuffer(multiBufferSource, renderType, pose);
		} else {
			vertexConsumer = getFoilBuffer(multiBufferSource, renderType, true, foilType != ItemStackRenderState.FoilType.NONE);
		}

		renderQuadList(poseStack, vertexConsumer, list, is, i, j);
	}

	private static VertexConsumer getSpecialFoilBuffer(MultiBufferSource multiBufferSource, RenderType renderType, PoseStack.Pose pose) {
		return VertexMultiConsumer.create(
			new SheetedDecalTextureGenerator(
				multiBufferSource.getBuffer(useTransparentGlint(renderType) ? RenderTypes.glintTranslucent() : RenderTypes.glint()), pose, 0.0078125F
			),
			multiBufferSource.getBuffer(renderType)
		);
	}

	public static VertexConsumer getFoilBuffer(MultiBufferSource multiBufferSource, RenderType renderType, boolean bl, boolean bl2) {
		if (bl2) {
			return useTransparentGlint(renderType)
				? VertexMultiConsumer.create(multiBufferSource.getBuffer(RenderTypes.glintTranslucent()), multiBufferSource.getBuffer(renderType))
				: VertexMultiConsumer.create(multiBufferSource.getBuffer(bl ? RenderTypes.glint() : RenderTypes.entityGlint()), multiBufferSource.getBuffer(renderType));
		} else {
			return multiBufferSource.getBuffer(renderType);
		}
	}

	public static List<RenderType> getFoilRenderTypes(RenderType renderType, boolean bl, boolean bl2) {
		if (bl2) {
			return useTransparentGlint(renderType)
				? List.of(renderType, RenderTypes.glintTranslucent())
				: List.of(renderType, bl ? RenderTypes.glint() : RenderTypes.entityGlint());
		} else {
			return List.of(renderType);
		}
	}

	private static boolean useTransparentGlint(RenderType renderType) {
		return Minecraft.useShaderTransparency() && (renderType == Sheets.translucentItemSheet() || renderType == Sheets.translucentBlockItemSheet());
	}

	private static int getLayerColorSafe(int[] is, int i) {
		return i >= 0 && i < is.length ? is[i] : -1;
	}

	private static void renderQuadList(PoseStack poseStack, VertexConsumer vertexConsumer, List<BakedQuad> list, int[] is, int i, int j) {
		PoseStack.Pose pose = poseStack.last();

		for (BakedQuad bakedQuad : list) {
			float f;
			float g;
			float h;
			float l;
			if (bakedQuad.isTinted()) {
				int k = getLayerColorSafe(is, bakedQuad.tintIndex());
				f = ARGB.alpha(k) / 255.0F;
				g = ARGB.red(k) / 255.0F;
				h = ARGB.green(k) / 255.0F;
				l = ARGB.blue(k) / 255.0F;
			} else {
				f = 1.0F;
				g = 1.0F;
				h = 1.0F;
				l = 1.0F;
			}

			vertexConsumer.putBulkData(pose, bakedQuad, g, h, l, f, i, j);
		}
	}
}
