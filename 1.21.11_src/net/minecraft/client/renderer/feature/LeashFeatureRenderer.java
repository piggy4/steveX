package net.minecraft.client.renderer.feature;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class LeashFeatureRenderer {
	private static final int LEASH_RENDER_STEPS = 24;
	private static final float LEASH_WIDTH = 0.05F;

	public void render(SubmitNodeCollection submitNodeCollection, MultiBufferSource.BufferSource bufferSource) {
		for (SubmitNodeStorage.LeashSubmit leashSubmit : submitNodeCollection.getLeashSubmits()) {
			renderLeash(leashSubmit.pose(), bufferSource, leashSubmit.leashState());
		}
	}

	private static void renderLeash(Matrix4f matrix4f, MultiBufferSource multiBufferSource, EntityRenderState.LeashState leashState) {
		float f = (float)(leashState.end.x - leashState.start.x);
		float g = (float)(leashState.end.y - leashState.start.y);
		float h = (float)(leashState.end.z - leashState.start.z);
		float i = Mth.invSqrt(f * f + h * h) * 0.05F / 2.0F;
		float j = h * i;
		float k = f * i;
		matrix4f.translate((float)leashState.offset.x, (float)leashState.offset.y, (float)leashState.offset.z);
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderTypes.leash());

		for (int l = 0; l <= 24; l++) {
			addVertexPair(vertexConsumer, matrix4f, f, g, h, 0.05F, j, k, l, false, leashState);
		}

		for (int l = 24; l >= 0; l--) {
			addVertexPair(vertexConsumer, matrix4f, f, g, h, 0.0F, j, k, l, true, leashState);
		}
	}

	private static void addVertexPair(
		VertexConsumer vertexConsumer,
		Matrix4f matrix4f,
		float f,
		float g,
		float h,
		float i,
		float j,
		float k,
		int l,
		boolean bl,
		EntityRenderState.LeashState leashState
	) {
		float m = l / 24.0F;
		int n = (int)Mth.lerp(m, leashState.startBlockLight, leashState.endBlockLight);
		int o = (int)Mth.lerp(m, leashState.startSkyLight, leashState.endSkyLight);
		int p = LightTexture.pack(n, o);
		float q = l % 2 == (bl ? 1 : 0) ? 0.7F : 1.0F;
		float r = 0.5F * q;
		float s = 0.4F * q;
		float t = 0.3F * q;
		float u = f * m;
		float v;
		if (leashState.slack) {
			v = g > 0.0F ? g * m * m : g - g * (1.0F - m) * (1.0F - m);
		} else {
			v = g * m;
		}

		float w = h * m;
		vertexConsumer.addVertex(matrix4f, u - j, v + i, w + k).setColor(r, s, t, 1.0F).setLight(p);
		vertexConsumer.addVertex(matrix4f, u + j, v + 0.05F - i, w - k).setColor(r, s, t, 1.0F).setLight(p);
	}
}
