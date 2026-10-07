package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Util;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.SkullBlock;

@Environment(EnvType.CLIENT)
public class CustomHeadLayer<S extends LivingEntityRenderState, M extends EntityModel<S> & HeadedModel> extends RenderLayer<S, M> {
	private static final float ITEM_SCALE = 0.625F;
	private static final float SKULL_SCALE = 1.1875F;
	private final CustomHeadLayer.Transforms transforms;
	private final Function<SkullBlock.Type, SkullModelBase> skullModels;
	private final PlayerSkinRenderCache playerSkinRenderCache;

	public CustomHeadLayer(RenderLayerParent<S, M> renderLayerParent, EntityModelSet entityModelSet, PlayerSkinRenderCache playerSkinRenderCache) {
		this(renderLayerParent, entityModelSet, playerSkinRenderCache, CustomHeadLayer.Transforms.DEFAULT);
	}

	public CustomHeadLayer(
		RenderLayerParent<S, M> renderLayerParent, EntityModelSet entityModelSet, PlayerSkinRenderCache playerSkinRenderCache, CustomHeadLayer.Transforms transforms
	) {
		super(renderLayerParent);
		this.transforms = transforms;
		this.skullModels = Util.memoize(type -> SkullBlockRenderer.createModel(entityModelSet, type));
		this.playerSkinRenderCache = playerSkinRenderCache;
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, S livingEntityRenderState, float f, float g) {
		if (!livingEntityRenderState.headItem.isEmpty() || livingEntityRenderState.wornHeadType != null) {
			poseStack.pushPose();
			poseStack.scale(this.transforms.horizontalScale(), 1.0F, this.transforms.horizontalScale());
			M entityModel = this.getParentModel();
			entityModel.root().translateAndRotate(poseStack);
			entityModel.translateToHead(poseStack);
			if (livingEntityRenderState.wornHeadType != null) {
				poseStack.translate(0.0F, this.transforms.skullYOffset(), 0.0F);
				poseStack.scale(1.1875F, -1.1875F, -1.1875F);
				poseStack.translate(-0.5, 0.0, -0.5);
				SkullBlock.Type type = livingEntityRenderState.wornHeadType;
				SkullModelBase skullModelBase = this.skullModels.apply(type);
				RenderType renderType = this.resolveSkullRenderType(livingEntityRenderState, type);
				SkullBlockRenderer.submitSkull(
					null,
					180.0F,
					livingEntityRenderState.wornHeadAnimationPos,
					poseStack,
					submitNodeCollector,
					i,
					skullModelBase,
					renderType,
					livingEntityRenderState.outlineColor,
					null
				);
			} else {
				translateToHead(poseStack, this.transforms);
				livingEntityRenderState.headItem.submit(poseStack, submitNodeCollector, i, OverlayTexture.NO_OVERLAY, livingEntityRenderState.outlineColor);
			}

			poseStack.popPose();
		}
	}

	private RenderType resolveSkullRenderType(LivingEntityRenderState livingEntityRenderState, SkullBlock.Type type) {
		if (type == SkullBlock.Types.PLAYER) {
			ResolvableProfile resolvableProfile = livingEntityRenderState.wornHeadProfile;
			if (resolvableProfile != null) {
				return this.playerSkinRenderCache.getOrDefault(resolvableProfile).renderType();
			}
		}

		return SkullBlockRenderer.getSkullRenderType(type, null);
	}

	public static void translateToHead(PoseStack poseStack, CustomHeadLayer.Transforms transforms) {
		poseStack.translate(0.0F, -0.25F + transforms.yOffset(), 0.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
		poseStack.scale(0.625F, -0.625F, -0.625F);
	}

	@Environment(EnvType.CLIENT)
	public record Transforms(float yOffset, float skullYOffset, float horizontalScale) {
		public static final CustomHeadLayer.Transforms DEFAULT = new CustomHeadLayer.Transforms(0.0F, 0.0F, 1.0F);
	}
}
