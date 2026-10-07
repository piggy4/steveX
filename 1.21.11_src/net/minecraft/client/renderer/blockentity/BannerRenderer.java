package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.client.model.object.banner.BannerModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BannerRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Unit;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BannerRenderer implements BlockEntityRenderer<BannerBlockEntity, BannerRenderState> {
	private static final int MAX_PATTERNS = 16;
	private static final float SIZE = 0.6666667F;
	private final MaterialSet materials;
	private final BannerModel standingModel;
	private final BannerModel wallModel;
	private final BannerFlagModel standingFlagModel;
	private final BannerFlagModel wallFlagModel;

	public BannerRenderer(BlockEntityRendererProvider.Context context) {
		this(context.entityModelSet(), context.materials());
	}

	public BannerRenderer(SpecialModelRenderer.BakingContext bakingContext) {
		this(bakingContext.entityModelSet(), bakingContext.materials());
	}

	public BannerRenderer(EntityModelSet entityModelSet, MaterialSet materialSet) {
		this.materials = materialSet;
		this.standingModel = new BannerModel(entityModelSet.bakeLayer(ModelLayers.STANDING_BANNER));
		this.wallModel = new BannerModel(entityModelSet.bakeLayer(ModelLayers.WALL_BANNER));
		this.standingFlagModel = new BannerFlagModel(entityModelSet.bakeLayer(ModelLayers.STANDING_BANNER_FLAG));
		this.wallFlagModel = new BannerFlagModel(entityModelSet.bakeLayer(ModelLayers.WALL_BANNER_FLAG));
	}

	public BannerRenderState createRenderState() {
		return new BannerRenderState();
	}

	public void extractRenderState(
		BannerBlockEntity bannerBlockEntity,
		BannerRenderState bannerRenderState,
		float f,
		Vec3 vec3,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer.super.extractRenderState(bannerBlockEntity, bannerRenderState, f, vec3, crumblingOverlay);
		bannerRenderState.baseColor = bannerBlockEntity.getBaseColor();
		bannerRenderState.patterns = bannerBlockEntity.getPatterns();
		BlockState blockState = bannerBlockEntity.getBlockState();
		if (blockState.getBlock() instanceof BannerBlock) {
			bannerRenderState.angle = -RotationSegment.convertToDegrees(blockState.getValue(BannerBlock.ROTATION));
			bannerRenderState.standing = true;
		} else {
			bannerRenderState.angle = -blockState.getValue(WallBannerBlock.FACING).toYRot();
			bannerRenderState.standing = false;
		}

		long l = bannerBlockEntity.getLevel() != null ? bannerBlockEntity.getLevel().getGameTime() : 0L;
		BlockPos blockPos = bannerBlockEntity.getBlockPos();
		bannerRenderState.phase = ((float)Math.floorMod(blockPos.getX() * 7 + blockPos.getY() * 9 + blockPos.getZ() * 13 + l, 100L) + f) / 100.0F;
	}

	public void submit(BannerRenderState bannerRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		BannerModel bannerModel;
		BannerFlagModel bannerFlagModel;
		if (bannerRenderState.standing) {
			bannerModel = this.standingModel;
			bannerFlagModel = this.standingFlagModel;
		} else {
			bannerModel = this.wallModel;
			bannerFlagModel = this.wallFlagModel;
		}

		submitBanner(
			this.materials,
			poseStack,
			submitNodeCollector,
			bannerRenderState.lightCoords,
			OverlayTexture.NO_OVERLAY,
			bannerRenderState.angle,
			bannerModel,
			bannerFlagModel,
			bannerRenderState.phase,
			bannerRenderState.baseColor,
			bannerRenderState.patterns,
			bannerRenderState.breakProgress,
			0
		);
	}

	public void submitSpecial(
		PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, DyeColor dyeColor, BannerPatternLayers bannerPatternLayers, int k
	) {
		submitBanner(
			this.materials, poseStack, submitNodeCollector, i, j, 0.0F, this.standingModel, this.standingFlagModel, 0.0F, dyeColor, bannerPatternLayers, null, k
		);
	}

	private static void submitBanner(
		MaterialSet materialSet,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int i,
		int j,
		float f,
		BannerModel bannerModel,
		BannerFlagModel bannerFlagModel,
		float g,
		DyeColor dyeColor,
		BannerPatternLayers bannerPatternLayers,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay,
		int k
	) {
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.0F, 0.5F);
		poseStack.mulPose(Axis.YP.rotationDegrees(f));
		poseStack.scale(0.6666667F, -0.6666667F, -0.6666667F);
		Material material = ModelBakery.BANNER_BASE;
		submitNodeCollector.submitModel(
			bannerModel, Unit.INSTANCE, poseStack, material.renderType(RenderTypes::entitySolid), i, j, -1, materialSet.get(material), k, crumblingOverlay
		);
		submitPatterns(
			materialSet, poseStack, submitNodeCollector, i, j, bannerFlagModel, g, material, true, dyeColor, bannerPatternLayers, false, crumblingOverlay, k
		);
		poseStack.popPose();
	}

	public static <S> void submitPatterns(
		MaterialSet materialSet,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int i,
		int j,
		Model<S> model,
		S object,
		Material material,
		boolean bl,
		DyeColor dyeColor,
		BannerPatternLayers bannerPatternLayers,
		boolean bl2,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay,
		int k
	) {
		submitNodeCollector.submitModel(
			model, object, poseStack, material.renderType(RenderTypes::entitySolid), i, j, -1, materialSet.get(material), k, crumblingOverlay
		);
		if (bl2) {
			submitNodeCollector.submitModel(model, object, poseStack, RenderTypes.entityGlint(), i, j, -1, materialSet.get(material), 0, crumblingOverlay);
		}

		submitPatternLayer(materialSet, poseStack, submitNodeCollector, i, j, model, object, bl ? Sheets.BANNER_BASE : Sheets.SHIELD_BASE, dyeColor, crumblingOverlay);

		for (int l = 0; l < 16 && l < bannerPatternLayers.layers().size(); l++) {
			BannerPatternLayers.Layer layer = bannerPatternLayers.layers().get(l);
			Material material2 = bl ? Sheets.getBannerMaterial(layer.pattern()) : Sheets.getShieldMaterial(layer.pattern());
			submitPatternLayer(materialSet, poseStack, submitNodeCollector, i, j, model, object, material2, layer.color(), null);
		}
	}

	private static <S> void submitPatternLayer(
		MaterialSet materialSet,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int i,
		int j,
		Model<S> model,
		S object,
		Material material,
		DyeColor dyeColor,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		int k = dyeColor.getTextureDiffuseColor();
		submitNodeCollector.submitModel(
			model, object, poseStack, material.renderType(RenderTypes::entityNoOutline), i, j, k, materialSet.get(material), 0, crumblingOverlay
		);
	}

	public void getExtents(Consumer<Vector3fc> consumer) {
		PoseStack poseStack = new PoseStack();
		poseStack.translate(0.5F, 0.0F, 0.5F);
		poseStack.scale(0.6666667F, -0.6666667F, -0.6666667F);
		this.standingModel.root().getExtentsForGui(poseStack, consumer);
		this.standingFlagModel.setupAnim(0.0F);
		this.standingFlagModel.root().getExtentsForGui(poseStack, consumer);
	}
}
