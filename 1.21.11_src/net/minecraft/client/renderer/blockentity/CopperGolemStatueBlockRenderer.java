package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.statue.CopperGolemStatueModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.CopperGolemStatueRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.animal.golem.CopperGolemOxidationLevels;
import net.minecraft.world.level.block.CopperGolemStatueBlock;
import net.minecraft.world.level.block.entity.CopperGolemStatueBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class CopperGolemStatueBlockRenderer implements BlockEntityRenderer<CopperGolemStatueBlockEntity, CopperGolemStatueRenderState> {
	private final Map<CopperGolemStatueBlock.Pose, CopperGolemStatueModel> models = new HashMap<>();

	public CopperGolemStatueBlockRenderer(BlockEntityRendererProvider.Context context) {
		EntityModelSet entityModelSet = context.entityModelSet();
		this.models.put(CopperGolemStatueBlock.Pose.STANDING, new CopperGolemStatueModel(entityModelSet.bakeLayer(ModelLayers.COPPER_GOLEM)));
		this.models.put(CopperGolemStatueBlock.Pose.RUNNING, new CopperGolemStatueModel(entityModelSet.bakeLayer(ModelLayers.COPPER_GOLEM_RUNNING)));
		this.models.put(CopperGolemStatueBlock.Pose.SITTING, new CopperGolemStatueModel(entityModelSet.bakeLayer(ModelLayers.COPPER_GOLEM_SITTING)));
		this.models.put(CopperGolemStatueBlock.Pose.STAR, new CopperGolemStatueModel(entityModelSet.bakeLayer(ModelLayers.COPPER_GOLEM_STAR)));
	}

	public CopperGolemStatueRenderState createRenderState() {
		return new CopperGolemStatueRenderState();
	}

	public void extractRenderState(
		CopperGolemStatueBlockEntity copperGolemStatueBlockEntity,
		CopperGolemStatueRenderState copperGolemStatueRenderState,
		float f,
		Vec3 vec3,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer.super.extractRenderState(copperGolemStatueBlockEntity, copperGolemStatueRenderState, f, vec3, crumblingOverlay);
		copperGolemStatueRenderState.direction = copperGolemStatueBlockEntity.getBlockState().getValue(CopperGolemStatueBlock.FACING);
		copperGolemStatueRenderState.pose = copperGolemStatueBlockEntity.getBlockState().getValue(BlockStateProperties.COPPER_GOLEM_POSE);
	}

	public void submit(
		CopperGolemStatueRenderState copperGolemStatueRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		if (copperGolemStatueRenderState.blockState.getBlock() instanceof CopperGolemStatueBlock copperGolemStatueBlock) {
			poseStack.pushPose();
			poseStack.translate(0.5F, 0.0F, 0.5F);
			CopperGolemStatueModel copperGolemStatueModel = this.models.get(copperGolemStatueRenderState.pose);
			Direction direction = copperGolemStatueRenderState.direction;
			RenderType renderType = RenderTypes.entityCutoutNoCull(CopperGolemOxidationLevels.getOxidationLevel(copperGolemStatueBlock.getWeatheringState()).texture());
			submitNodeCollector.submitModel(
				copperGolemStatueModel,
				direction,
				poseStack,
				renderType,
				copperGolemStatueRenderState.lightCoords,
				OverlayTexture.NO_OVERLAY,
				0,
				copperGolemStatueRenderState.breakProgress
			);
			poseStack.popPose();
		}
	}
}
