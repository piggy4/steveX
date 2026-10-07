package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.EnumSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.EndPortalRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class AbstractEndPortalRenderer<T extends TheEndPortalBlockEntity, S extends EndPortalRenderState> implements BlockEntityRenderer<T, S> {
	public static final Identifier END_SKY_LOCATION = Identifier.withDefaultNamespace("textures/environment/end_sky.png");
	public static final Identifier END_PORTAL_LOCATION = Identifier.withDefaultNamespace("textures/entity/end_portal.png");

	public void extractRenderState(
		T theEndPortalBlockEntity, S endPortalRenderState, float f, Vec3 vec3, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer.super.extractRenderState(theEndPortalBlockEntity, endPortalRenderState, f, vec3, crumblingOverlay);
		endPortalRenderState.facesToShow.clear();

		for (Direction direction : Direction.values()) {
			if (theEndPortalBlockEntity.shouldRenderFace(direction)) {
				endPortalRenderState.facesToShow.add(direction);
			}
		}
	}

	public void submit(S endPortalRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		submitNodeCollector.submitCustomGeometry(
			poseStack, this.renderType(), (pose, vertexConsumer) -> this.renderCube(endPortalRenderState.facesToShow, pose.pose(), vertexConsumer)
		);
	}

	private void renderCube(EnumSet<Direction> enumSet, Matrix4f matrix4f, VertexConsumer vertexConsumer) {
		float f = this.getOffsetDown();
		float g = this.getOffsetUp();
		this.renderFace(enumSet, matrix4f, vertexConsumer, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, Direction.SOUTH);
		this.renderFace(enumSet, matrix4f, vertexConsumer, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, Direction.NORTH);
		this.renderFace(enumSet, matrix4f, vertexConsumer, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, Direction.EAST);
		this.renderFace(enumSet, matrix4f, vertexConsumer, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, Direction.WEST);
		this.renderFace(enumSet, matrix4f, vertexConsumer, 0.0F, 1.0F, f, f, 0.0F, 0.0F, 1.0F, 1.0F, Direction.DOWN);
		this.renderFace(enumSet, matrix4f, vertexConsumer, 0.0F, 1.0F, g, g, 1.0F, 1.0F, 0.0F, 0.0F, Direction.UP);
	}

	private void renderFace(
		EnumSet<Direction> enumSet,
		Matrix4f matrix4f,
		VertexConsumer vertexConsumer,
		float f,
		float g,
		float h,
		float i,
		float j,
		float k,
		float l,
		float m,
		Direction direction
	) {
		if (enumSet.contains(direction)) {
			vertexConsumer.addVertex(matrix4f, f, h, j);
			vertexConsumer.addVertex(matrix4f, g, h, k);
			vertexConsumer.addVertex(matrix4f, g, i, l);
			vertexConsumer.addVertex(matrix4f, f, i, m);
		}
	}

	protected float getOffsetUp() {
		return 0.75F;
	}

	protected float getOffsetDown() {
		return 0.375F;
	}

	protected RenderType renderType() {
		return RenderTypes.endPortal();
	}
}
