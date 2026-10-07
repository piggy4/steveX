package net.minecraft.client.renderer.blockentity;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.skull.DragonHeadModel;
import net.minecraft.client.model.object.skull.PiglinHeadModel;
import net.minecraft.client.model.object.skull.SkullModel;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.SkullBlockRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class SkullBlockRenderer implements BlockEntityRenderer<SkullBlockEntity, SkullBlockRenderState> {
	private final Function<SkullBlock.Type, SkullModelBase> modelByType;
	private static final Map<SkullBlock.Type, Identifier> SKIN_BY_TYPE = Util.make(Maps.newHashMap(), hashMap -> {
		hashMap.put(SkullBlock.Types.SKELETON, Identifier.withDefaultNamespace("textures/entity/skeleton/skeleton.png"));
		hashMap.put(SkullBlock.Types.WITHER_SKELETON, Identifier.withDefaultNamespace("textures/entity/skeleton/wither_skeleton.png"));
		hashMap.put(SkullBlock.Types.ZOMBIE, Identifier.withDefaultNamespace("textures/entity/zombie/zombie.png"));
		hashMap.put(SkullBlock.Types.CREEPER, Identifier.withDefaultNamespace("textures/entity/creeper/creeper.png"));
		hashMap.put(SkullBlock.Types.DRAGON, Identifier.withDefaultNamespace("textures/entity/enderdragon/dragon.png"));
		hashMap.put(SkullBlock.Types.PIGLIN, Identifier.withDefaultNamespace("textures/entity/piglin/piglin.png"));
		hashMap.put(SkullBlock.Types.PLAYER, DefaultPlayerSkin.getDefaultTexture());
	});
	private final PlayerSkinRenderCache playerSkinRenderCache;

	public static @Nullable SkullModelBase createModel(EntityModelSet entityModelSet, SkullBlock.Type type) {
		if (type instanceof SkullBlock.Types types) {
			return switch (types) {
				case SKELETON -> new SkullModel(entityModelSet.bakeLayer(ModelLayers.SKELETON_SKULL));
				case WITHER_SKELETON -> new SkullModel(entityModelSet.bakeLayer(ModelLayers.WITHER_SKELETON_SKULL));
				case PLAYER -> new SkullModel(entityModelSet.bakeLayer(ModelLayers.PLAYER_HEAD));
				case ZOMBIE -> new SkullModel(entityModelSet.bakeLayer(ModelLayers.ZOMBIE_HEAD));
				case CREEPER -> new SkullModel(entityModelSet.bakeLayer(ModelLayers.CREEPER_HEAD));
				case DRAGON -> new DragonHeadModel(entityModelSet.bakeLayer(ModelLayers.DRAGON_SKULL));
				case PIGLIN -> new PiglinHeadModel(entityModelSet.bakeLayer(ModelLayers.PIGLIN_HEAD));
			};
		} else {
			return null;
		}
	}

	public SkullBlockRenderer(BlockEntityRendererProvider.Context context) {
		EntityModelSet entityModelSet = context.entityModelSet();
		this.playerSkinRenderCache = context.playerSkinRenderCache();
		this.modelByType = Util.memoize(type -> createModel(entityModelSet, type));
	}

	public SkullBlockRenderState createRenderState() {
		return new SkullBlockRenderState();
	}

	public void extractRenderState(
		SkullBlockEntity skullBlockEntity,
		SkullBlockRenderState skullBlockRenderState,
		float f,
		Vec3 vec3,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer.super.extractRenderState(skullBlockEntity, skullBlockRenderState, f, vec3, crumblingOverlay);
		skullBlockRenderState.animationProgress = skullBlockEntity.getAnimation(f);
		BlockState blockState = skullBlockEntity.getBlockState();
		boolean bl = blockState.getBlock() instanceof WallSkullBlock;
		skullBlockRenderState.direction = bl ? blockState.getValue(WallSkullBlock.FACING) : null;
		int i = bl ? RotationSegment.convertToSegment(skullBlockRenderState.direction.getOpposite()) : blockState.getValue(SkullBlock.ROTATION);
		skullBlockRenderState.rotationDegrees = RotationSegment.convertToDegrees(i);
		skullBlockRenderState.skullType = ((AbstractSkullBlock)blockState.getBlock()).getType();
		skullBlockRenderState.renderType = this.resolveSkullRenderType(skullBlockRenderState.skullType, skullBlockEntity);
	}

	public void submit(
		SkullBlockRenderState skullBlockRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		SkullModelBase skullModelBase = this.modelByType.apply(skullBlockRenderState.skullType);
		submitSkull(
			skullBlockRenderState.direction,
			skullBlockRenderState.rotationDegrees,
			skullBlockRenderState.animationProgress,
			poseStack,
			submitNodeCollector,
			skullBlockRenderState.lightCoords,
			skullModelBase,
			skullBlockRenderState.renderType,
			0,
			skullBlockRenderState.breakProgress
		);
	}

	public static void submitSkull(
		@Nullable Direction direction,
		float f,
		float g,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int i,
		SkullModelBase skullModelBase,
		RenderType renderType,
		int j,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		poseStack.pushPose();
		if (direction == null) {
			poseStack.translate(0.5F, 0.0F, 0.5F);
		} else {
			float h = 0.25F;
			poseStack.translate(0.5F - direction.getStepX() * 0.25F, 0.25F, 0.5F - direction.getStepZ() * 0.25F);
		}

		poseStack.scale(-1.0F, -1.0F, 1.0F);
		SkullModelBase.State state = new SkullModelBase.State();
		state.animationPos = g;
		state.yRot = f;
		submitNodeCollector.submitModel(skullModelBase, state, poseStack, renderType, i, OverlayTexture.NO_OVERLAY, j, crumblingOverlay);
		poseStack.popPose();
	}

	private RenderType resolveSkullRenderType(SkullBlock.Type type, SkullBlockEntity skullBlockEntity) {
		if (type == SkullBlock.Types.PLAYER) {
			ResolvableProfile resolvableProfile = skullBlockEntity.getOwnerProfile();
			if (resolvableProfile != null) {
				return this.playerSkinRenderCache.getOrDefault(resolvableProfile).renderType();
			}
		}

		return getSkullRenderType(type, null);
	}

	public static RenderType getSkullRenderType(SkullBlock.Type type, @Nullable Identifier identifier) {
		return RenderTypes.entityCutoutNoCullZOffset(identifier != null ? identifier : SKIN_BY_TYPE.get(type));
	}

	public static RenderType getPlayerSkinRenderType(Identifier identifier) {
		return RenderTypes.entityTranslucent(identifier);
	}
}
