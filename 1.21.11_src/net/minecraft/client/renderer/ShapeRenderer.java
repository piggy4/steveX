package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class ShapeRenderer {
	public static void renderShape(PoseStack poseStack, VertexConsumer vertexConsumer, VoxelShape voxelShape, double d, double e, double f, int i, float g) {
		PoseStack.Pose pose = poseStack.last();
		voxelShape.forAllEdges((h, j, k, l, m, n) -> {
			Vector3f vector3f = new Vector3f((float)(l - h), (float)(m - j), (float)(n - k)).normalize();
			vertexConsumer.addVertex(pose, (float)(h + d), (float)(j + e), (float)(k + f)).setColor(i).setNormal(pose, vector3f).setLineWidth(g);
			vertexConsumer.addVertex(pose, (float)(l + d), (float)(m + e), (float)(n + f)).setColor(i).setNormal(pose, vector3f).setLineWidth(g);
		});
	}
}
