package net.minecraft.client.renderer.gizmos;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public class DrawableGizmoPrimitives implements GizmoPrimitives {
	private final DrawableGizmoPrimitives.Group opaque = new DrawableGizmoPrimitives.Group(true);
	private final DrawableGizmoPrimitives.Group translucent = new DrawableGizmoPrimitives.Group(false);
	private boolean isEmpty = true;

	private DrawableGizmoPrimitives.Group getGroup(int i) {
		return ARGB.alpha(i) < 255 ? this.translucent : this.opaque;
	}

	@Override
	public void addPoint(Vec3 vec3, int i, float f) {
		this.getGroup(i).points.add(new DrawableGizmoPrimitives.Point(vec3, i, f));
		this.isEmpty = false;
	}

	@Override
	public void addLine(Vec3 vec3, Vec3 vec32, int i, float f) {
		this.getGroup(i).lines.add(new DrawableGizmoPrimitives.Line(vec3, vec32, i, f));
		this.isEmpty = false;
	}

	@Override
	public void addTriangleFan(Vec3[] vec3s, int i) {
		this.getGroup(i).triangleFans.add(new DrawableGizmoPrimitives.TriangleFan(vec3s, i));
		this.isEmpty = false;
	}

	@Override
	public void addQuad(Vec3 vec3, Vec3 vec32, Vec3 vec33, Vec3 vec34, int i) {
		this.getGroup(i).quads.add(new DrawableGizmoPrimitives.Quad(vec3, vec32, vec33, vec34, i));
		this.isEmpty = false;
	}

	@Override
	public void addText(Vec3 vec3, String string, TextGizmo.Style style) {
		this.getGroup(style.color()).texts.add(new DrawableGizmoPrimitives.Text(vec3, string, style));
		this.isEmpty = false;
	}

	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, CameraRenderState cameraRenderState, Matrix4f matrix4f) {
		this.opaque.render(poseStack, multiBufferSource, cameraRenderState, matrix4f);
		this.translucent.render(poseStack, multiBufferSource, cameraRenderState, matrix4f);
	}

	public boolean isEmpty() {
		return this.isEmpty;
	}

	@Environment(EnvType.CLIENT)
	record Group(
		boolean opaque,
		List<DrawableGizmoPrimitives.Line> lines,
		List<DrawableGizmoPrimitives.Quad> quads,
		List<DrawableGizmoPrimitives.TriangleFan> triangleFans,
		List<DrawableGizmoPrimitives.Text> texts,
		List<DrawableGizmoPrimitives.Point> points
	) {

		Group(boolean bl) {
			this(bl, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
		}

		public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, CameraRenderState cameraRenderState, Matrix4f matrix4f) {
			this.renderQuads(poseStack, multiBufferSource, cameraRenderState);
			this.renderTriangleFans(poseStack, multiBufferSource, cameraRenderState);
			this.renderLines(poseStack, multiBufferSource, cameraRenderState, matrix4f);
			this.renderTexts(poseStack, multiBufferSource, cameraRenderState);
			this.renderPoints(poseStack, multiBufferSource, cameraRenderState);
		}

		private void renderTexts(PoseStack poseStack, MultiBufferSource multiBufferSource, CameraRenderState cameraRenderState) {
			Minecraft minecraft = Minecraft.getInstance();
			Font font = minecraft.font;
			if (cameraRenderState.initialized) {
				double d = cameraRenderState.pos.x();
				double e = cameraRenderState.pos.y();
				double f = cameraRenderState.pos.z();

				for (DrawableGizmoPrimitives.Text text : this.texts) {
					poseStack.pushPose();
					poseStack.translate((float)(text.pos().x() - d), (float)(text.pos().y() - e), (float)(text.pos().z() - f));
					poseStack.mulPose(cameraRenderState.orientation);
					poseStack.scale(text.style.scale() / 16.0F, -text.style.scale() / 16.0F, text.style.scale() / 16.0F);
					float g;
					if (text.style.adjustLeft().isEmpty()) {
						g = -font.width(text.text) / 2.0F;
					} else {
						g = (float)(-text.style.adjustLeft().getAsDouble()) / text.style.scale();
					}

					font.drawInBatch(text.text, g, 0.0F, text.style.color(), false, poseStack.last().pose(), multiBufferSource, Font.DisplayMode.NORMAL, 0, 15728880);
					poseStack.popPose();
				}
			}
		}

		private void renderLines(PoseStack poseStack, MultiBufferSource multiBufferSource, CameraRenderState cameraRenderState, Matrix4f matrix4f) {
			VertexConsumer vertexConsumer = multiBufferSource.getBuffer(this.opaque ? RenderTypes.lines() : RenderTypes.linesTranslucent());
			PoseStack.Pose pose = poseStack.last();
			Vector4f vector4f = new Vector4f();
			Vector4f vector4f2 = new Vector4f();
			Vector4f vector4f3 = new Vector4f();
			Vector4f vector4f4 = new Vector4f();
			Vector4f vector4f5 = new Vector4f();
			double d = cameraRenderState.pos.x();
			double e = cameraRenderState.pos.y();
			double f = cameraRenderState.pos.z();

			for (DrawableGizmoPrimitives.Line line : this.lines) {
				vector4f.set(line.start().x() - d, line.start().y() - e, line.start().z() - f, 1.0);
				vector4f2.set(line.end().x() - d, line.end().y() - e, line.end().z() - f, 1.0);
				vector4f.mul(matrix4f, vector4f3);
				vector4f2.mul(matrix4f, vector4f4);
				boolean bl = vector4f3.z > -0.05F;
				boolean bl2 = vector4f4.z > -0.05F;
				if (!bl || !bl2) {
					if (bl || bl2) {
						float g = vector4f4.z - vector4f3.z;
						if (Math.abs(g) < 1.0E-9F) {
							continue;
						}

						float h = Mth.clamp((-0.05F - vector4f3.z) / g, 0.0F, 1.0F);
						vector4f.lerp(vector4f2, h, vector4f5);
						if (bl) {
							vector4f.set(vector4f5);
						} else {
							vector4f2.set(vector4f5);
						}
					}

					vertexConsumer.addVertex(pose, vector4f.x, vector4f.y, vector4f.z)
						.setNormal(pose, vector4f2.x - vector4f.x, vector4f2.y - vector4f.y, vector4f2.z - vector4f.z)
						.setColor(line.color())
						.setLineWidth(line.width());
					vertexConsumer.addVertex(pose, vector4f2.x, vector4f2.y, vector4f2.z)
						.setNormal(pose, vector4f2.x - vector4f.x, vector4f2.y - vector4f.y, vector4f2.z - vector4f.z)
						.setColor(line.color())
						.setLineWidth(line.width());
				}
			}
		}

		private void renderTriangleFans(PoseStack poseStack, MultiBufferSource multiBufferSource, CameraRenderState cameraRenderState) {
			PoseStack.Pose pose = poseStack.last();
			double d = cameraRenderState.pos.x();
			double e = cameraRenderState.pos.y();
			double f = cameraRenderState.pos.z();

			for (DrawableGizmoPrimitives.TriangleFan triangleFan : this.triangleFans) {
				VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderTypes.debugTriangleFan());

				for (Vec3 vec3 : triangleFan.points()) {
					vertexConsumer.addVertex(pose, (float)(vec3.x() - d), (float)(vec3.y() - e), (float)(vec3.z() - f)).setColor(triangleFan.color());
				}
			}
		}

		private void renderQuads(PoseStack poseStack, MultiBufferSource multiBufferSource, CameraRenderState cameraRenderState) {
			VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderTypes.debugFilledBox());
			PoseStack.Pose pose = poseStack.last();
			double d = cameraRenderState.pos.x();
			double e = cameraRenderState.pos.y();
			double f = cameraRenderState.pos.z();

			for (DrawableGizmoPrimitives.Quad quad : this.quads) {
				vertexConsumer.addVertex(pose, (float)(quad.a().x() - d), (float)(quad.a().y() - e), (float)(quad.a().z() - f)).setColor(quad.color());
				vertexConsumer.addVertex(pose, (float)(quad.b().x() - d), (float)(quad.b().y() - e), (float)(quad.b().z() - f)).setColor(quad.color());
				vertexConsumer.addVertex(pose, (float)(quad.c().x() - d), (float)(quad.c().y() - e), (float)(quad.c().z() - f)).setColor(quad.color());
				vertexConsumer.addVertex(pose, (float)(quad.d().x() - d), (float)(quad.d().y() - e), (float)(quad.d().z() - f)).setColor(quad.color());
			}
		}

		private void renderPoints(PoseStack poseStack, MultiBufferSource multiBufferSource, CameraRenderState cameraRenderState) {
			VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderTypes.debugPoint());
			PoseStack.Pose pose = poseStack.last();
			double d = cameraRenderState.pos.x();
			double e = cameraRenderState.pos.y();
			double f = cameraRenderState.pos.z();

			for (DrawableGizmoPrimitives.Point point : this.points) {
				vertexConsumer.addVertex(pose, (float)(point.pos.x() - d), (float)(point.pos.y() - e), (float)(point.pos.z() - f))
					.setColor(point.color())
					.setLineWidth(point.size());
			}
		}
	}

	@Environment(EnvType.CLIENT)
	record Line(Vec3 start, Vec3 end, int color, float width) {
	}

	@Environment(EnvType.CLIENT)
	record Point(Vec3 pos, int color, float size) {
	}

	@Environment(EnvType.CLIENT)
	record Quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
	}

	@Environment(EnvType.CLIENT)
	record Text(Vec3 pos, String text, TextGizmo.Style style) {
	}

	@Environment(EnvType.CLIENT)
	record TriangleFan(Vec3[] points, int color) {
	}
}
