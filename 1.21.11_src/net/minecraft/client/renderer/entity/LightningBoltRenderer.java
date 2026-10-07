package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LightningBoltRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LightningBolt;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class LightningBoltRenderer extends EntityRenderer<LightningBolt, LightningBoltRenderState> {
	public LightningBoltRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	public void submit(
		LightningBoltRenderState lightningBoltRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		float[] fs = new float[8];
		float[] gs = new float[8];
		float f = 0.0F;
		float g = 0.0F;
		RandomSource randomSource = RandomSource.create(lightningBoltRenderState.seed);

		for (int i = 7; i >= 0; i--) {
			fs[i] = f;
			gs[i] = g;
			f += randomSource.nextInt(11) - 5;
			g += randomSource.nextInt(11) - 5;
		}

		float h = f;
		float j = g;
		submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, vertexConsumer) -> {
			Matrix4f matrix4f = pose.pose();

			for (int i = 0; i < 4; i++) {
				RandomSource randomSourcex = RandomSource.create(lightningBoltRenderState.seed);

				for (int jx = 0; jx < 3; jx++) {
					int k = 7;
					int l = 0;
					if (jx > 0) {
						k = 7 - jx;
					}

					if (jx > 0) {
						l = k - 2;
					}

					float hx = fs[k] - h;
					float m = gs[k] - j;

					for (int n = k; n >= l; n--) {
						float o = hx;
						float p = m;
						if (jx == 0) {
							hx += randomSourcex.nextInt(11) - 5;
							m += randomSourcex.nextInt(11) - 5;
						} else {
							hx += randomSourcex.nextInt(31) - 15;
							m += randomSourcex.nextInt(31) - 15;
						}

						float q = 0.5F;
						float r = 0.45F;
						float s = 0.45F;
						float t = 0.5F;
						float u = 0.1F + i * 0.2F;
						if (jx == 0) {
							u *= n * 0.1F + 1.0F;
						}

						float v = 0.1F + i * 0.2F;
						if (jx == 0) {
							v *= (n - 1.0F) * 0.1F + 1.0F;
						}

						quad(matrix4f, vertexConsumer, hx, m, n, o, p, 0.45F, 0.45F, 0.5F, u, v, false, false, true, false);
						quad(matrix4f, vertexConsumer, hx, m, n, o, p, 0.45F, 0.45F, 0.5F, u, v, true, false, true, true);
						quad(matrix4f, vertexConsumer, hx, m, n, o, p, 0.45F, 0.45F, 0.5F, u, v, true, true, false, true);
						quad(matrix4f, vertexConsumer, hx, m, n, o, p, 0.45F, 0.45F, 0.5F, u, v, false, true, false, false);
					}
				}
			}
		});
	}

	private static void quad(
		Matrix4f matrix4f,
		VertexConsumer vertexConsumer,
		float f,
		float g,
		int i,
		float h,
		float j,
		float k,
		float l,
		float m,
		float n,
		float o,
		boolean bl,
		boolean bl2,
		boolean bl3,
		boolean bl4
	) {
		vertexConsumer.addVertex(matrix4f, f + (bl ? o : -o), i * 16, g + (bl2 ? o : -o)).setColor(k, l, m, 0.3F);
		vertexConsumer.addVertex(matrix4f, h + (bl ? n : -n), (i + 1) * 16, j + (bl2 ? n : -n)).setColor(k, l, m, 0.3F);
		vertexConsumer.addVertex(matrix4f, h + (bl3 ? n : -n), (i + 1) * 16, j + (bl4 ? n : -n)).setColor(k, l, m, 0.3F);
		vertexConsumer.addVertex(matrix4f, f + (bl3 ? o : -o), i * 16, g + (bl4 ? o : -o)).setColor(k, l, m, 0.3F);
	}

	public LightningBoltRenderState createRenderState() {
		return new LightningBoltRenderState();
	}

	public void extractRenderState(LightningBolt lightningBolt, LightningBoltRenderState lightningBoltRenderState, float f) {
		super.extractRenderState(lightningBolt, lightningBoltRenderState, f);
		lightningBoltRenderState.seed = lightningBolt.seed;
	}

	protected boolean affectedByCulling(LightningBolt lightningBolt) {
		return false;
	}
}
