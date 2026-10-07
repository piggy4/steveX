package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.SpawnerRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class SpawnerRenderer implements BlockEntityRenderer<SpawnerBlockEntity, SpawnerRenderState> {
	private final EntityRenderDispatcher entityRenderer;

	public SpawnerRenderer(BlockEntityRendererProvider.Context context) {
		this.entityRenderer = context.entityRenderer();
	}

	public SpawnerRenderState createRenderState() {
		return new SpawnerRenderState();
	}

	public void extractRenderState(
		SpawnerBlockEntity spawnerBlockEntity,
		SpawnerRenderState spawnerRenderState,
		float f,
		Vec3 vec3,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer.super.extractRenderState(spawnerBlockEntity, spawnerRenderState, f, vec3, crumblingOverlay);
		if (spawnerBlockEntity.getLevel() != null) {
			BaseSpawner baseSpawner = spawnerBlockEntity.getSpawner();
			Entity entity = baseSpawner.getOrCreateDisplayEntity(spawnerBlockEntity.getLevel(), spawnerBlockEntity.getBlockPos());
			TrialSpawnerRenderer.extractSpawnerData(spawnerRenderState, f, entity, this.entityRenderer, baseSpawner.getOSpin(), baseSpawner.getSpin());
		}
	}

	public void submit(SpawnerRenderState spawnerRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		if (spawnerRenderState.displayEntity != null) {
			submitEntityInSpawner(
				poseStack, submitNodeCollector, spawnerRenderState.displayEntity, this.entityRenderer, spawnerRenderState.spin, spawnerRenderState.scale, cameraRenderState
			);
		}
	}

	public static void submitEntityInSpawner(
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		EntityRenderState entityRenderState,
		EntityRenderDispatcher entityRenderDispatcher,
		float f,
		float g,
		CameraRenderState cameraRenderState
	) {
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.4F, 0.5F);
		poseStack.mulPose(Axis.YP.rotationDegrees(f));
		poseStack.translate(0.0F, -0.2F, 0.0F);
		poseStack.mulPose(Axis.XP.rotationDegrees(-30.0F));
		poseStack.scale(g, g, g);
		entityRenderDispatcher.submit(entityRenderState, cameraRenderState, 0.0, 0.0, 0.0, poseStack, submitNodeCollector);
		poseStack.popPose();
	}
}
