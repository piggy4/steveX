package net.minecraft.client.renderer.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;

@Environment(EnvType.CLIENT)
public interface ParticleGroupRenderState {
	void submit(SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState);

	default void clear() {
	}
}
