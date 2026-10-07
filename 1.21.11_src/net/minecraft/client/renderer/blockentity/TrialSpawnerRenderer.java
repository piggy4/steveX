package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.SpawnerRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerStateData;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class TrialSpawnerRenderer implements BlockEntityRenderer<TrialSpawnerBlockEntity, SpawnerRenderState> {
	private final EntityRenderDispatcher entityRenderer;

	public TrialSpawnerRenderer(BlockEntityRendererProvider.Context context) {
		this.entityRenderer = context.entityRenderer();
	}

	public SpawnerRenderState createRenderState() {
		return new SpawnerRenderState();
	}

	public void extractRenderState(
		TrialSpawnerBlockEntity trialSpawnerBlockEntity,
		SpawnerRenderState spawnerRenderState,
		float f,
		Vec3 vec3,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer.super.extractRenderState(trialSpawnerBlockEntity, spawnerRenderState, f, vec3, crumblingOverlay);
		if (trialSpawnerBlockEntity.getLevel() != null) {
			TrialSpawner trialSpawner = trialSpawnerBlockEntity.getTrialSpawner();
			TrialSpawnerStateData trialSpawnerStateData = trialSpawner.getStateData();
			Entity entity = trialSpawnerStateData.getOrCreateDisplayEntity(trialSpawner, trialSpawnerBlockEntity.getLevel(), trialSpawner.getState());
			extractSpawnerData(spawnerRenderState, f, entity, this.entityRenderer, trialSpawnerStateData.getOSpin(), trialSpawnerStateData.getSpin());
		}
	}

	static void extractSpawnerData(
		SpawnerRenderState spawnerRenderState, float f, @Nullable Entity entity, EntityRenderDispatcher entityRenderDispatcher, double d, double e
	) {
		if (entity != null) {
			spawnerRenderState.displayEntity = entityRenderDispatcher.extractEntity(entity, f);
			spawnerRenderState.displayEntity.lightCoords = spawnerRenderState.lightCoords;
			spawnerRenderState.spin = (float)Mth.lerp(f, d, e) * 10.0F;
			spawnerRenderState.scale = 0.53125F;
			float g = Math.max(entity.getBbWidth(), entity.getBbHeight());
			if (g > 1.0) {
				spawnerRenderState.scale /= g;
			}
		}
	}

	public void submit(SpawnerRenderState spawnerRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		if (spawnerRenderState.displayEntity != null) {
			SpawnerRenderer.submitEntityInSpawner(
				poseStack, submitNodeCollector, spawnerRenderState.displayEntity, this.entityRenderer, spawnerRenderState.spin, spawnerRenderState.scale, cameraRenderState
			);
		}
	}
}
