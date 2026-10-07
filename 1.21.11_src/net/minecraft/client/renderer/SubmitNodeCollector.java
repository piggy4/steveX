package net.minecraft.client.renderer;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.feature.ParticleFeatureRenderer;
import net.minecraft.client.renderer.state.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureManager;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface SubmitNodeCollector extends OrderedSubmitNodeCollector {
	OrderedSubmitNodeCollector order(int i);

	@Environment(EnvType.CLIENT)
	interface CustomGeometryRenderer {
		void render(PoseStack.Pose pose, VertexConsumer vertexConsumer);
	}

	@Environment(EnvType.CLIENT)
	interface ParticleGroupRenderer {
		QuadParticleRenderState.@Nullable PreparedBuffers prepare(ParticleFeatureRenderer.ParticleBufferCache particleBufferCache);

		void render(
			QuadParticleRenderState.PreparedBuffers preparedBuffers,
			ParticleFeatureRenderer.ParticleBufferCache particleBufferCache,
			RenderPass renderPass,
			TextureManager textureManager,
			boolean bl
		);
	}
}
