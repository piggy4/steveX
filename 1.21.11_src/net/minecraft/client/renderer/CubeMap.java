package net.minecraft.client.renderer;

import com.mojang.blaze3d.ProjectionType;
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
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.CubeMapTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public class CubeMap implements AutoCloseable {
	private static final int SIDES = 6;
	private final GpuBuffer vertexBuffer;
	private final CachedPerspectiveProjectionMatrixBuffer projectionMatrixUbo;
	private final Identifier location;

	public CubeMap(Identifier identifier) {
		this.location = identifier;
		this.projectionMatrixUbo = new CachedPerspectiveProjectionMatrixBuffer("cubemap", 0.05F, 10.0F);
		this.vertexBuffer = initializeVertices();
	}

	public void render(Minecraft minecraft, float f, float g) {
		RenderSystem.setProjectionMatrix(
			this.projectionMatrixUbo.getBuffer(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight(), 85.0F), ProjectionType.PERSPECTIVE
		);
		RenderPipeline renderPipeline = RenderPipelines.PANORAMA;
		RenderTarget renderTarget = Minecraft.getInstance().getMainRenderTarget();
		GpuTextureView gpuTextureView = renderTarget.getColorTextureView();
		GpuTextureView gpuTextureView2 = renderTarget.getDepthTextureView();
		RenderSystem.AutoStorageIndexBuffer autoStorageIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
		GpuBuffer gpuBuffer = autoStorageIndexBuffer.getBuffer(36);
		Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
		matrix4fStack.pushMatrix();
		matrix4fStack.rotationX((float) Math.PI);
		matrix4fStack.rotateX(f * (float) (Math.PI / 180.0));
		matrix4fStack.rotateY(g * (float) (Math.PI / 180.0));
		GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms()
			.writeTransform(new Matrix4f(matrix4fStack), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f());
		matrix4fStack.popMatrix();

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "Cubemap", gpuTextureView, OptionalInt.empty(), gpuTextureView2, OptionalDouble.empty())) {
			renderPass.setPipeline(renderPipeline);
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setVertexBuffer(0, this.vertexBuffer);
			renderPass.setIndexBuffer(gpuBuffer, autoStorageIndexBuffer.type());
			renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
			AbstractTexture abstractTexture = minecraft.getTextureManager().getTexture(this.location);
			renderPass.bindTexture("Sampler0", abstractTexture.getTextureView(), abstractTexture.getSampler());
			renderPass.drawIndexed(0, 0, 36, 1);
		}
	}

	private static GpuBuffer initializeVertices() {
		try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION.getVertexSize() * 4 * 6)) {
			BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
			bufferBuilder.addVertex(-1.0F, -1.0F, 1.0F);
			bufferBuilder.addVertex(-1.0F, 1.0F, 1.0F);
			bufferBuilder.addVertex(1.0F, 1.0F, 1.0F);
			bufferBuilder.addVertex(1.0F, -1.0F, 1.0F);
			bufferBuilder.addVertex(1.0F, -1.0F, 1.0F);
			bufferBuilder.addVertex(1.0F, 1.0F, 1.0F);
			bufferBuilder.addVertex(1.0F, 1.0F, -1.0F);
			bufferBuilder.addVertex(1.0F, -1.0F, -1.0F);
			bufferBuilder.addVertex(1.0F, -1.0F, -1.0F);
			bufferBuilder.addVertex(1.0F, 1.0F, -1.0F);
			bufferBuilder.addVertex(-1.0F, 1.0F, -1.0F);
			bufferBuilder.addVertex(-1.0F, -1.0F, -1.0F);
			bufferBuilder.addVertex(-1.0F, -1.0F, -1.0F);
			bufferBuilder.addVertex(-1.0F, 1.0F, -1.0F);
			bufferBuilder.addVertex(-1.0F, 1.0F, 1.0F);
			bufferBuilder.addVertex(-1.0F, -1.0F, 1.0F);
			bufferBuilder.addVertex(-1.0F, -1.0F, -1.0F);
			bufferBuilder.addVertex(-1.0F, -1.0F, 1.0F);
			bufferBuilder.addVertex(1.0F, -1.0F, 1.0F);
			bufferBuilder.addVertex(1.0F, -1.0F, -1.0F);
			bufferBuilder.addVertex(-1.0F, 1.0F, 1.0F);
			bufferBuilder.addVertex(-1.0F, 1.0F, -1.0F);
			bufferBuilder.addVertex(1.0F, 1.0F, -1.0F);
			bufferBuilder.addVertex(1.0F, 1.0F, 1.0F);

			try (MeshData meshData = bufferBuilder.buildOrThrow()) {
				return RenderSystem.getDevice().createBuffer(() -> "Cube map vertex buffer", 32, meshData.vertexBuffer());
			}
		}
	}

	public void registerTextures(TextureManager textureManager) {
		textureManager.register(this.location, new CubeMapTexture(this.location));
	}

	@Override
	public void close() {
		this.vertexBuffer.close();
		this.projectionMatrixUbo.close();
	}
}
