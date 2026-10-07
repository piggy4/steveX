package net.minecraft.client.renderer.feature;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class ShadowFeatureRenderer {
	private static final RenderType SHADOW_RENDER_TYPE = RenderTypes.entityShadow(Identifier.withDefaultNamespace("textures/misc/shadow.png"));

	public void render(SubmitNodeCollection submitNodeCollection, MultiBufferSource.BufferSource bufferSource) {
		VertexConsumer vertexConsumer = bufferSource.getBuffer(SHADOW_RENDER_TYPE);

		for (SubmitNodeStorage.ShadowSubmit shadowSubmit : submitNodeCollection.getShadowSubmits()) {
			for (EntityRenderState.ShadowPiece shadowPiece : shadowSubmit.pieces()) {
				AABB aABB = shadowPiece.shapeBelow().bounds();
				float f = shadowPiece.relativeX() + (float)aABB.minX;
				float g = shadowPiece.relativeX() + (float)aABB.maxX;
				float h = shadowPiece.relativeY() + (float)aABB.minY;
				float i = shadowPiece.relativeZ() + (float)aABB.minZ;
				float j = shadowPiece.relativeZ() + (float)aABB.maxZ;
				float k = shadowSubmit.radius();
				float l = -f / 2.0F / k + 0.5F;
				float m = -g / 2.0F / k + 0.5F;
				float n = -i / 2.0F / k + 0.5F;
				float o = -j / 2.0F / k + 0.5F;
				int p = ARGB.white(shadowPiece.alpha());
				shadowVertex(shadowSubmit.pose(), vertexConsumer, p, f, h, i, l, n);
				shadowVertex(shadowSubmit.pose(), vertexConsumer, p, f, h, j, l, o);
				shadowVertex(shadowSubmit.pose(), vertexConsumer, p, g, h, j, m, o);
				shadowVertex(shadowSubmit.pose(), vertexConsumer, p, g, h, i, m, n);
			}
		}
	}

	private static void shadowVertex(Matrix4f matrix4f, VertexConsumer vertexConsumer, int i, float f, float g, float h, float j, float k) {
		Vector3f vector3f = matrix4f.transformPosition(f, g, h, new Vector3f());
		vertexConsumer.addVertex(vector3f.x(), vector3f.y(), vector3f.z(), i, j, k, OverlayTexture.NO_OVERLAY, 15728880, 0.0F, 1.0F, 0.0F);
	}
}
