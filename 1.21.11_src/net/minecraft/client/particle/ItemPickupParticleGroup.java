package net.minecraft.client.particle;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.state.ParticleGroupRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class ItemPickupParticleGroup extends ParticleGroup<ItemPickupParticle> {
	public ItemPickupParticleGroup(ParticleEngine particleEngine) {
		super(particleEngine);
	}

	@Override
	public ParticleGroupRenderState extractRenderState(Frustum frustum, Camera camera, float f) {
		return new ItemPickupParticleGroup.State(
			this.particles.stream().map(itemPickupParticle -> ItemPickupParticleGroup.ParticleInstance.fromParticle(itemPickupParticle, camera, f)).toList()
		);
	}

	@Environment(EnvType.CLIENT)
	record ParticleInstance(EntityRenderState itemRenderState, double xOffset, double yOffset, double zOffset) {

		public static ItemPickupParticleGroup.ParticleInstance fromParticle(ItemPickupParticle itemPickupParticle, Camera camera, float f) {
			float g = (itemPickupParticle.life + f) / 3.0F;
			g *= g;
			double d = Mth.lerp(f, itemPickupParticle.targetXOld, itemPickupParticle.targetX);
			double e = Mth.lerp(f, itemPickupParticle.targetYOld, itemPickupParticle.targetY);
			double h = Mth.lerp(f, itemPickupParticle.targetZOld, itemPickupParticle.targetZ);
			double i = Mth.lerp(g, itemPickupParticle.itemRenderState.x, d);
			double j = Mth.lerp(g, itemPickupParticle.itemRenderState.y, e);
			double k = Mth.lerp(g, itemPickupParticle.itemRenderState.z, h);
			Vec3 vec3 = camera.position();
			return new ItemPickupParticleGroup.ParticleInstance(itemPickupParticle.itemRenderState, i - vec3.x(), j - vec3.y(), k - vec3.z());
		}
	}

	@Environment(EnvType.CLIENT)
	record State(List<ItemPickupParticleGroup.ParticleInstance> instances) implements ParticleGroupRenderState {
		@Override
		public void submit(SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
			PoseStack poseStack = new PoseStack();
			EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();

			for (ItemPickupParticleGroup.ParticleInstance particleInstance : this.instances) {
				entityRenderDispatcher.submit(
					particleInstance.itemRenderState,
					cameraRenderState,
					particleInstance.xOffset,
					particleInstance.yOffset,
					particleInstance.zOffset,
					poseStack,
					submitNodeCollector
				);
			}
		}
	}
}
