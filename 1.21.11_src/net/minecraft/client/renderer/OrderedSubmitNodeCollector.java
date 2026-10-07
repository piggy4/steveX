package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.renderer.v1.render.FabricRenderCommandQueue;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.renderer.v1.render.FabricRenderCommandQueue} injected by mod fabric-renderer-api-v1</p>
 */
@Environment(EnvType.CLIENT)
public interface OrderedSubmitNodeCollector extends FabricRenderCommandQueue {
	void submitShadow(PoseStack poseStack, float f, List<EntityRenderState.ShadowPiece> list);

	void submitNameTag(PoseStack poseStack, @Nullable Vec3 vec3, int i, Component component, boolean bl, int j, double d, CameraRenderState cameraRenderState);

	void submitText(
		PoseStack poseStack, float f, float g, FormattedCharSequence formattedCharSequence, boolean bl, Font.DisplayMode displayMode, int i, int j, int k, int l
	);

	void submitFlame(PoseStack poseStack, EntityRenderState entityRenderState, Quaternionf quaternionf);

	void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState);

	<S> void submitModel(
		Model<? super S> model,
		S object,
		PoseStack poseStack,
		RenderType renderType,
		int i,
		int j,
		int k,
		@Nullable TextureAtlasSprite textureAtlasSprite,
		int l,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	);

	default <S> void submitModel(
		Model<? super S> model,
		S object,
		PoseStack poseStack,
		RenderType renderType,
		int i,
		int j,
		int k,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		this.submitModel(model, object, poseStack, renderType, i, j, -1, null, k, crumblingOverlay);
	}

	default void submitModelPart(ModelPart modelPart, PoseStack poseStack, RenderType renderType, int i, int j, @Nullable TextureAtlasSprite textureAtlasSprite) {
		this.submitModelPart(modelPart, poseStack, renderType, i, j, textureAtlasSprite, false, false, -1, null, 0);
	}

	default void submitModelPart(
		ModelPart modelPart,
		PoseStack poseStack,
		RenderType renderType,
		int i,
		int j,
		@Nullable TextureAtlasSprite textureAtlasSprite,
		int k,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		this.submitModelPart(modelPart, poseStack, renderType, i, j, textureAtlasSprite, false, false, k, crumblingOverlay, 0);
	}

	default void submitModelPart(
		ModelPart modelPart, PoseStack poseStack, RenderType renderType, int i, int j, @Nullable TextureAtlasSprite textureAtlasSprite, boolean bl, boolean bl2
	) {
		this.submitModelPart(modelPart, poseStack, renderType, i, j, textureAtlasSprite, bl, bl2, -1, null, 0);
	}

	void submitModelPart(
		ModelPart modelPart,
		PoseStack poseStack,
		RenderType renderType,
		int i,
		int j,
		@Nullable TextureAtlasSprite textureAtlasSprite,
		boolean bl,
		boolean bl2,
		int k,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay,
		int l
	);

	void submitBlock(PoseStack poseStack, BlockState blockState, int i, int j, int k);

	void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState);

	void submitBlockModel(PoseStack poseStack, RenderType renderType, BlockStateModel blockStateModel, float f, float g, float h, int i, int j, int k);

	void submitItem(
		PoseStack poseStack,
		ItemDisplayContext itemDisplayContext,
		int i,
		int j,
		int k,
		int[] is,
		List<BakedQuad> list,
		RenderType renderType,
		ItemStackRenderState.FoilType foilType
	);

	void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer);

	void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer particleGroupRenderer);
}
