package net.minecraft.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.WorldBorderRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public class WorldBorderRenderer {
	public static final Identifier FORCEFIELD_LOCATION = Identifier.withDefaultNamespace("textures/misc/forcefield.png");
	private boolean needsRebuild = true;
	private double lastMinX;
	private double lastMinZ;
	private double lastBorderMinX;
	private double lastBorderMaxX;
	private double lastBorderMinZ;
	private double lastBorderMaxZ;
	private final GpuBuffer worldBorderBuffer = RenderSystem.getDevice()
		.createBuffer(() -> "World border vertex buffer", 40, 16L * DefaultVertexFormat.POSITION_TEX.getVertexSize());
	private final RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);

	private void rebuildWorldBorderBuffer(WorldBorderRenderState worldBorderRenderState, double d, double e, double f, float g, float h, float i) {
		try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION_TEX.getVertexSize() * 4 * 4)) {
			double j = worldBorderRenderState.minX;
			double k = worldBorderRenderState.maxX;
			double l = worldBorderRenderState.minZ;
			double m = worldBorderRenderState.maxZ;
			double n = Math.max(Mth.floor(e - d), l);
			double o = Math.min(Mth.ceil(e + d), m);
			float p = (Mth.floor(n) & 1) * 0.5F;
			float q = (float)(o - n) / 2.0F;
			double r = Math.max(Mth.floor(f - d), j);
			double s = Math.min(Mth.ceil(f + d), k);
			float t = (Mth.floor(r) & 1) * 0.5F;
			float u = (float)(s - r) / 2.0F;
			BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
			bufferBuilder.addVertex(0.0F, -g, (float)(m - n)).setUv(t, h);
			bufferBuilder.addVertex((float)(s - r), -g, (float)(m - n)).setUv(u + t, h);
			bufferBuilder.addVertex((float)(s - r), g, (float)(m - n)).setUv(u + t, i);
			bufferBuilder.addVertex(0.0F, g, (float)(m - n)).setUv(t, i);
			bufferBuilder.addVertex(0.0F, -g, 0.0F).setUv(p, h);
			bufferBuilder.addVertex(0.0F, -g, (float)(o - n)).setUv(q + p, h);
			bufferBuilder.addVertex(0.0F, g, (float)(o - n)).setUv(q + p, i);
			bufferBuilder.addVertex(0.0F, g, 0.0F).setUv(p, i);
			bufferBuilder.addVertex((float)(s - r), -g, 0.0F).setUv(t, h);
			bufferBuilder.addVertex(0.0F, -g, 0.0F).setUv(u + t, h);
			bufferBuilder.addVertex(0.0F, g, 0.0F).setUv(u + t, i);
			bufferBuilder.addVertex((float)(s - r), g, 0.0F).setUv(t, i);
			bufferBuilder.addVertex((float)(k - r), -g, (float)(o - n)).setUv(p, h);
			bufferBuilder.addVertex((float)(k - r), -g, 0.0F).setUv(q + p, h);
			bufferBuilder.addVertex((float)(k - r), g, 0.0F).setUv(q + p, i);
			bufferBuilder.addVertex((float)(k - r), g, (float)(o - n)).setUv(p, i);

			try (MeshData meshData = bufferBuilder.buildOrThrow()) {
				RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.worldBorderBuffer.slice(), meshData.vertexBuffer());
			}

			this.lastBorderMinX = j;
			this.lastBorderMaxX = k;
			this.lastBorderMinZ = l;
			this.lastBorderMaxZ = m;
			this.lastMinX = r;
			this.lastMinZ = n;
			this.needsRebuild = false;
		}
	}

	public void extract(WorldBorder worldBorder, float f, Vec3 vec3, double d, WorldBorderRenderState worldBorderRenderState) {
		worldBorderRenderState.minX = worldBorder.getMinX(f);
		worldBorderRenderState.maxX = worldBorder.getMaxX(f);
		worldBorderRenderState.minZ = worldBorder.getMinZ(f);
		worldBorderRenderState.maxZ = worldBorder.getMaxZ(f);
		if ((
				!(vec3.x < worldBorderRenderState.maxX - d)
					|| !(vec3.x > worldBorderRenderState.minX + d)
					|| !(vec3.z < worldBorderRenderState.maxZ - d)
					|| !(vec3.z > worldBorderRenderState.minZ + d)
			)
			&& !(vec3.x < worldBorderRenderState.minX - d)
			&& !(vec3.x > worldBorderRenderState.maxX + d)
			&& !(vec3.z < worldBorderRenderState.minZ - d)
			&& !(vec3.z > worldBorderRenderState.maxZ + d)) {
			worldBorderRenderState.alpha = 1.0 - worldBorder.getDistanceToBorder(vec3.x, vec3.z) / d;
			worldBorderRenderState.alpha = Math.pow(worldBorderRenderState.alpha, 4.0);
			worldBorderRenderState.alpha = Mth.clamp(worldBorderRenderState.alpha, 0.0, 1.0);
			worldBorderRenderState.tint = worldBorder.getStatus().getColor();
		} else {
			worldBorderRenderState.alpha = 0.0;
		}
	}

	public void render(WorldBorderRenderState worldBorderRenderState, Vec3 vec3, double d, double e) {
		if (!(worldBorderRenderState.alpha <= 0.0)) {
			double f = vec3.x;
			double g = vec3.z;
			float h = (float)e;
			float i = ARGB.red(worldBorderRenderState.tint) / 255.0F;
			float j = ARGB.green(worldBorderRenderState.tint) / 255.0F;
			float k = ARGB.blue(worldBorderRenderState.tint) / 255.0F;
			float l = (float)(Util.getMillis() % 3000L) / 3000.0F;
			float m = (float)(-Mth.frac(vec3.y * 0.5));
			float n = m + h;
			if (this.shouldRebuildWorldBorderBuffer(worldBorderRenderState)) {
				this.rebuildWorldBorderBuffer(worldBorderRenderState, d, g, f, h, n, m);
			}

			TextureManager textureManager = Minecraft.getInstance().getTextureManager();
			AbstractTexture abstractTexture = textureManager.getTexture(FORCEFIELD_LOCATION);
			RenderPipeline renderPipeline = RenderPipelines.WORLD_BORDER;
			RenderTarget renderTarget = Minecraft.getInstance().getMainRenderTarget();
			RenderTarget renderTarget2 = Minecraft.getInstance().levelRenderer.getWeatherTarget();
			GpuTextureView gpuTextureView;
			GpuTextureView gpuTextureView2;
			if (renderTarget2 != null) {
				gpuTextureView = renderTarget2.getColorTextureView();
				gpuTextureView2 = renderTarget2.getDepthTextureView();
			} else {
				gpuTextureView = renderTarget.getColorTextureView();
				gpuTextureView2 = renderTarget.getDepthTextureView();
			}

			GpuBuffer gpuBuffer = this.indices.getBuffer(6);
			GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms()
				.writeTransform(
					RenderSystem.getModelViewMatrix(),
					new Vector4f(i, j, k, (float)worldBorderRenderState.alpha),
					new Vector3f((float)(this.lastMinX - f), (float)(-vec3.y), (float)(this.lastMinZ - g)),
					new Matrix4f().translation(l, l, 0.0F)
				);

			try (RenderPass renderPass = RenderSystem.getDevice()
					.createCommandEncoder()
					.createRenderPass(() -> "World border", gpuTextureView, OptionalInt.empty(), gpuTextureView2, OptionalDouble.empty())) {
				renderPass.setPipeline(renderPipeline);
				RenderSystem.bindDefaultUniforms(renderPass);
				renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
				renderPass.setIndexBuffer(gpuBuffer, this.indices.type());
				renderPass.bindTexture("Sampler0", abstractTexture.getTextureView(), abstractTexture.getSampler());
				renderPass.setVertexBuffer(0, this.worldBorderBuffer);
				ArrayList<RenderPass.Draw<WorldBorderRenderer>> arrayList = new ArrayList<>();

				for (WorldBorderRenderState.DistancePerDirection distancePerDirection : worldBorderRenderState.closestBorder(f, g)) {
					if (distancePerDirection.distance() < d) {
						int o = distancePerDirection.direction().get2DDataValue();
						arrayList.add(new RenderPass.Draw<>(0, this.worldBorderBuffer, gpuBuffer, this.indices.type(), 6 * o, 6));
					}
				}

				renderPass.drawMultipleIndexed(arrayList, null, null, Collections.emptyList(), this);
			}
		}
	}

	public void invalidate() {
		this.needsRebuild = true;
	}

	private boolean shouldRebuildWorldBorderBuffer(WorldBorderRenderState worldBorderRenderState) {
		return this.needsRebuild
			|| worldBorderRenderState.minX != this.lastBorderMinX
			|| worldBorderRenderState.minZ != this.lastBorderMinZ
			|| worldBorderRenderState.maxX != this.lastBorderMaxX
			|| worldBorderRenderState.maxZ != this.lastBorderMaxZ;
	}
}
