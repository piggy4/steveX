package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelPartFeatureRenderer;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
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
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class SubmitNodeCollection implements OrderedSubmitNodeCollector {
	private final List<SubmitNodeStorage.ShadowSubmit> shadowSubmits = new ArrayList<>();
	private final List<SubmitNodeStorage.FlameSubmit> flameSubmits = new ArrayList<>();
	private final NameTagFeatureRenderer.Storage nameTagSubmits = new NameTagFeatureRenderer.Storage();
	private final List<SubmitNodeStorage.TextSubmit> textSubmits = new ArrayList<>();
	private final List<SubmitNodeStorage.LeashSubmit> leashSubmits = new ArrayList<>();
	private final List<SubmitNodeStorage.BlockSubmit> blockSubmits = new ArrayList<>();
	private final List<SubmitNodeStorage.MovingBlockSubmit> movingBlockSubmits = new ArrayList<>();
	private final List<SubmitNodeStorage.BlockModelSubmit> blockModelSubmits = new ArrayList<>();
	private final List<SubmitNodeStorage.ItemSubmit> itemSubmits = new ArrayList<>();
	private final List<SubmitNodeCollector.ParticleGroupRenderer> particleGroupRenderers = new ArrayList<>();
	private final ModelFeatureRenderer.Storage modelSubmits = new ModelFeatureRenderer.Storage();
	private final ModelPartFeatureRenderer.Storage modelPartSubmits = new ModelPartFeatureRenderer.Storage();
	private final CustomFeatureRenderer.Storage customGeometrySubmits = new CustomFeatureRenderer.Storage();
	private final SubmitNodeStorage submitNodeStorage;
	private boolean wasUsed = false;

	public SubmitNodeCollection(SubmitNodeStorage submitNodeStorage) {
		this.submitNodeStorage = submitNodeStorage;
	}

	@Override
	public void submitShadow(PoseStack poseStack, float f, List<EntityRenderState.ShadowPiece> list) {
		this.wasUsed = true;
		PoseStack.Pose pose = poseStack.last();
		this.shadowSubmits.add(new SubmitNodeStorage.ShadowSubmit(new Matrix4f(pose.pose()), f, list));
	}

	@Override
	public void submitNameTag(
		PoseStack poseStack, @Nullable Vec3 vec3, int i, Component component, boolean bl, int j, double d, CameraRenderState cameraRenderState
	) {
		this.wasUsed = true;
		this.nameTagSubmits.add(poseStack, vec3, i, component, bl, j, d, cameraRenderState);
	}

	@Override
	public void submitText(
		PoseStack poseStack, float f, float g, FormattedCharSequence formattedCharSequence, boolean bl, Font.DisplayMode displayMode, int i, int j, int k, int l
	) {
		this.wasUsed = true;
		this.textSubmits.add(new SubmitNodeStorage.TextSubmit(new Matrix4f(poseStack.last().pose()), f, g, formattedCharSequence, bl, displayMode, i, j, k, l));
	}

	@Override
	public void submitFlame(PoseStack poseStack, EntityRenderState entityRenderState, Quaternionf quaternionf) {
		this.wasUsed = true;
		this.flameSubmits.add(new SubmitNodeStorage.FlameSubmit(poseStack.last().copy(), entityRenderState, quaternionf));
	}

	@Override
	public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
		this.wasUsed = true;
		this.leashSubmits.add(new SubmitNodeStorage.LeashSubmit(new Matrix4f(poseStack.last().pose()), leashState));
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
		this.wasUsed = true;
		SubmitNodeStorage.ModelSubmit<S> modelSubmit = new SubmitNodeStorage.ModelSubmit<>(
			poseStack.last().copy(), model, object, i, j, k, textureAtlasSprite, l, crumblingOverlay
		);
		this.modelSubmits.add(renderType, modelSubmit);
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
		this.wasUsed = true;
		this.modelPartSubmits
			.add(renderType, new SubmitNodeStorage.ModelPartSubmit(poseStack.last().copy(), modelPart, i, j, textureAtlasSprite, bl, bl2, k, crumblingOverlay, l));
	}

	@Override
	public void submitBlock(PoseStack poseStack, BlockState blockState, int i, int j, int k) {
		this.wasUsed = true;
		this.blockSubmits.add(new SubmitNodeStorage.BlockSubmit(poseStack.last().copy(), blockState, i, j, k));
		Minecraft.getInstance()
			.getModelManager()
			.specialBlockModelRenderer()
			.renderByBlock(blockState.getBlock(), ItemDisplayContext.NONE, poseStack, this.submitNodeStorage, i, j, k);
	}

	@Override
	public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState) {
		this.wasUsed = true;
		this.movingBlockSubmits.add(new SubmitNodeStorage.MovingBlockSubmit(new Matrix4f(poseStack.last().pose()), movingBlockRenderState));
	}

	@Override
	public void submitBlockModel(PoseStack poseStack, RenderType renderType, BlockStateModel blockStateModel, float f, float g, float h, int i, int j, int k) {
		this.wasUsed = true;
		this.blockModelSubmits.add(new SubmitNodeStorage.BlockModelSubmit(poseStack.last().copy(), renderType, blockStateModel, f, g, h, i, j, k));
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
		this.wasUsed = true;
		this.itemSubmits.add(new SubmitNodeStorage.ItemSubmit(poseStack.last().copy(), itemDisplayContext, i, j, k, is, list, renderType, foilType));
	}

	@Override
	public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
		this.wasUsed = true;
		this.customGeometrySubmits.add(poseStack, renderType, customGeometryRenderer);
	}

	@Override
	public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer particleGroupRenderer) {
		this.wasUsed = true;
		this.particleGroupRenderers.add(particleGroupRenderer);
	}

	public List<SubmitNodeStorage.ShadowSubmit> getShadowSubmits() {
		return this.shadowSubmits;
	}

	public List<SubmitNodeStorage.FlameSubmit> getFlameSubmits() {
		return this.flameSubmits;
	}

	public NameTagFeatureRenderer.Storage getNameTagSubmits() {
		return this.nameTagSubmits;
	}

	public List<SubmitNodeStorage.TextSubmit> getTextSubmits() {
		return this.textSubmits;
	}

	public List<SubmitNodeStorage.LeashSubmit> getLeashSubmits() {
		return this.leashSubmits;
	}

	public List<SubmitNodeStorage.BlockSubmit> getBlockSubmits() {
		return this.blockSubmits;
	}

	public List<SubmitNodeStorage.MovingBlockSubmit> getMovingBlockSubmits() {
		return this.movingBlockSubmits;
	}

	public List<SubmitNodeStorage.BlockModelSubmit> getBlockModelSubmits() {
		return this.blockModelSubmits;
	}

	public ModelPartFeatureRenderer.Storage getModelPartSubmits() {
		return this.modelPartSubmits;
	}

	public List<SubmitNodeStorage.ItemSubmit> getItemSubmits() {
		return this.itemSubmits;
	}

	public List<SubmitNodeCollector.ParticleGroupRenderer> getParticleGroupRenderers() {
		return this.particleGroupRenderers;
	}

	public ModelFeatureRenderer.Storage getModelSubmits() {
		return this.modelSubmits;
	}

	public CustomFeatureRenderer.Storage getCustomGeometrySubmits() {
		return this.customGeometrySubmits;
	}

	public boolean wasUsed() {
		return this.wasUsed;
	}

	public void clear() {
		this.shadowSubmits.clear();
		this.flameSubmits.clear();
		this.nameTagSubmits.clear();
		this.textSubmits.clear();
		this.leashSubmits.clear();
		this.blockSubmits.clear();
		this.movingBlockSubmits.clear();
		this.blockModelSubmits.clear();
		this.itemSubmits.clear();
		this.particleGroupRenderers.clear();
		this.modelSubmits.clear();
		this.customGeometrySubmits.clear();
		this.modelPartSubmits.clear();
	}

	public void endFrame() {
		this.modelSubmits.endFrame();
		this.modelPartSubmits.endFrame();
		this.customGeometrySubmits.endFrame();
		this.wasUsed = false;
	}
}
