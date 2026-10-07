package net.minecraft.client.renderer.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.AtlasManager;
import net.minecraft.client.resources.model.ModelBakery;
import org.joml.Quaternionf;

@Environment(EnvType.CLIENT)
public class FlameFeatureRenderer {
	public void render(SubmitNodeCollection submitNodeCollection, MultiBufferSource.BufferSource bufferSource, AtlasManager atlasManager) {
		for (SubmitNodeStorage.FlameSubmit flameSubmit : submitNodeCollection.getFlameSubmits()) {
			this.renderFlame(flameSubmit.pose(), bufferSource, flameSubmit.entityRenderState(), flameSubmit.rotation(), atlasManager);
		}
	}

	private void renderFlame(
		PoseStack.Pose pose, MultiBufferSource multiBufferSource, EntityRenderState entityRenderState, Quaternionf quaternionf, AtlasManager atlasManager
	) {
		TextureAtlasSprite textureAtlasSprite = atlasManager.get(ModelBakery.FIRE_0);
		TextureAtlasSprite textureAtlasSprite2 = atlasManager.get(ModelBakery.FIRE_1);
		float f = entityRenderState.boundingBoxWidth * 1.4F;
		pose.scale(f, f, f);
		float g = 0.5F;
		float h = 0.0F;
		float i = entityRenderState.boundingBoxHeight / f;
		float j = 0.0F;
		pose.rotate(quaternionf);
		pose.translate(0.0F, 0.0F, 0.3F - (int)i * 0.02F);
		float k = 0.0F;
		int l = 0;
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(Sheets.cutoutBlockSheet());

		while (i > 0.0F) {
			TextureAtlasSprite textureAtlasSprite3 = l % 2 == 0 ? textureAtlasSprite : textureAtlasSprite2;
			float m = textureAtlasSprite3.getU0();
			float n = textureAtlasSprite3.getV0();
			float o = textureAtlasSprite3.getU1();
			float p = textureAtlasSprite3.getV1();
			if (l / 2 % 2 == 0) {
				float q = o;
				o = m;
				m = q;
			}

			fireVertex(pose, vertexConsumer, -g - 0.0F, 0.0F - j, k, o, p);
			fireVertex(pose, vertexConsumer, g - 0.0F, 0.0F - j, k, m, p);
			fireVertex(pose, vertexConsumer, g - 0.0F, 1.4F - j, k, m, n);
			fireVertex(pose, vertexConsumer, -g - 0.0F, 1.4F - j, k, o, n);
			i -= 0.45F;
			j -= 0.45F;
			g *= 0.9F;
			k -= 0.03F;
			l++;
		}
	}

	private static void fireVertex(PoseStack.Pose pose, VertexConsumer vertexConsumer, float f, float g, float h, float i, float j) {
		vertexConsumer.addVertex(pose, f, g, h).setColor(-1).setUv(i, j).setUv1(0, 10).setLight(240).setNormal(pose, 0.0F, 1.0F, 0.0F);
	}
}
