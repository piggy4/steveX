package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
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
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class SubmitNodeStorage implements SubmitNodeCollector {
	private final Int2ObjectAVLTreeMap<SubmitNodeCollection> submitsPerOrder = new Int2ObjectAVLTreeMap<>();

	public SubmitNodeCollection order(int i) {
		return this.submitsPerOrder.computeIfAbsent(i, ix -> new SubmitNodeCollection(this));
	}

	@Override
	public void submitShadow(PoseStack poseStack, float f, List<EntityRenderState.ShadowPiece> list) {
		this.order(0).submitShadow(poseStack, f, list);
	}

	@Override
	public void submitNameTag(
		PoseStack poseStack, @Nullable Vec3 vec3, int i, Component component, boolean bl, int j, double d, CameraRenderState cameraRenderState
	) {
		this.order(0).submitNameTag(poseStack, vec3, i, component, bl, j, d, cameraRenderState);
	}

	@Override
	public void submitText(
		PoseStack poseStack, float f, float g, FormattedCharSequence formattedCharSequence, boolean bl, Font.DisplayMode displayMode, int i, int j, int k, int l
	) {
		this.order(0).submitText(poseStack, f, g, formattedCharSequence, bl, displayMode, i, j, k, l);
	}

	@Override
	public void submitFlame(PoseStack poseStack, EntityRenderState entityRenderState, Quaternionf quaternionf) {
		this.order(0).submitFlame(poseStack, entityRenderState, quaternionf);
	}

	@Override
	public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
		this.order(0).submitLeash(poseStack, leashState);
	}

	@Override
	public <S> void submitModel(
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
	) {
		this.order(0).submitModel(model, object, poseStack, renderType, i, j, k, textureAtlasSprite, l, crumblingOverlay);
	}

	@Override
	public void submitModelPart(
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
	) {
		this.order(0).submitModelPart(modelPart, poseStack, renderType, i, j, textureAtlasSprite, bl, bl2, k, crumblingOverlay, l);
	}

	@Override
	public void submitBlock(PoseStack poseStack, BlockState blockState, int i, int j, int k) {
		this.order(0).submitBlock(poseStack, blockState, i, j, k);
	}

	@Override
	public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState) {
		this.order(0).submitMovingBlock(poseStack, movingBlockRenderState);
	}

	@Override
	public void submitBlockModel(PoseStack poseStack, RenderType renderType, BlockStateModel blockStateModel, float f, float g, float h, int i, int j, int k) {
		this.order(0).submitBlockModel(poseStack, renderType, blockStateModel, f, g, h, i, j, k);
	}

	@Override
	public void submitItem(
		PoseStack poseStack,
		ItemDisplayContext itemDisplayContext,
		int i,
		int j,
		int k,
		int[] is,
		List<BakedQuad> list,
		RenderType renderType,
		ItemStackRenderState.FoilType foilType
	) {
		this.order(0).submitItem(poseStack, itemDisplayContext, i, j, k, is, list, renderType, foilType);
	}

	@Override
	public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
		this.order(0).submitCustomGeometry(poseStack, renderType, customGeometryRenderer);
	}

	@Override
	public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer particleGroupRenderer) {
		this.order(0).submitParticleGroup(particleGroupRenderer);
	}

	public void clear() {
		this.submitsPerOrder.values().forEach(SubmitNodeCollection::clear);
	}

	public void endFrame() {
		this.submitsPerOrder.values().removeIf(submitNodeCollection -> !submitNodeCollection.wasUsed());
		this.submitsPerOrder.values().forEach(SubmitNodeCollection::endFrame);
	}

	public Int2ObjectAVLTreeMap<SubmitNodeCollection> getSubmitsPerOrder() {
		return this.submitsPerOrder;
	}

	@Environment(EnvType.CLIENT)
	public record BlockModelSubmit(
		PoseStack.Pose pose, RenderType renderType, BlockStateModel model, float r, float g, float b, int lightCoords, int overlayCoords, int outlineColor
	) {
	}

	@Environment(EnvType.CLIENT)
	public record BlockSubmit(PoseStack.Pose pose, BlockState state, int lightCoords, int overlayCoords, int outlineColor) {
	}

	@Environment(EnvType.CLIENT)
	public record CustomGeometrySubmit(PoseStack.Pose pose, SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
	}

	@Environment(EnvType.CLIENT)
	public record FlameSubmit(PoseStack.Pose pose, EntityRenderState entityRenderState, Quaternionf rotation) {
	}

	@Environment(EnvType.CLIENT)
	public record ItemSubmit(
		PoseStack.Pose pose,
		ItemDisplayContext displayContext,
		int lightCoords,
		int overlayCoords,
		int outlineColor,
		int[] tintLayers,
		List<BakedQuad> quads,
		RenderType renderType,
		ItemStackRenderState.FoilType foilType
	) {
	}

	@Environment(EnvType.CLIENT)
	public record LeashSubmit(Matrix4f pose, EntityRenderState.LeashState leashState) {
	}

	@Environment(EnvType.CLIENT)
	public record ModelPartSubmit(
		PoseStack.Pose pose,
		ModelPart modelPart,
		int lightCoords,
		int overlayCoords,
		@Nullable TextureAtlasSprite sprite,
		boolean sheeted,
		boolean hasFoil,
		int tintedColor,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay,
		int outlineColor
	) {
	}

	@Environment(EnvType.CLIENT)
	public record ModelSubmit<S>(
		PoseStack.Pose pose,
		Model<? super S> model,
		S state,
		int lightCoords,
		int overlayCoords,
		int tintedColor,
		@Nullable TextureAtlasSprite sprite,
		int outlineColor,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
	}

	@Environment(EnvType.CLIENT)
	public record MovingBlockSubmit(Matrix4f pose, MovingBlockRenderState movingBlockRenderState) {
	}

	@Environment(EnvType.CLIENT)
	public record NameTagSubmit(Matrix4f pose, float x, float y, Component text, int lightCoords, int color, int backgroundColor, double distanceToCameraSq) {
	}

	@Environment(EnvType.CLIENT)
	public record ShadowSubmit(Matrix4f pose, float radius, List<EntityRenderState.ShadowPiece> pieces) {
	}

	@Environment(EnvType.CLIENT)
	public record TextSubmit(
		Matrix4f pose,
		float x,
		float y,
		FormattedCharSequence string,
		boolean dropShadow,
		Font.DisplayMode displayMode,
		int lightCoords,
		int color,
		int backgroundColor,
		int outlineColor
	) {
	}

	@Environment(EnvType.CLIENT)
	public record TranslucentModelSubmit<S>(SubmitNodeStorage.ModelSubmit<S> modelSubmit, RenderType renderType, Vector3f position) {
	}
}
