package com.mojang.blaze3d.systems;

import com.mojang.blaze3d.DontObfuscate;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.TracyFrameCapture;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.util.ArrayListDeque;
import net.minecraft.util.Mth;
import net.minecraft.util.TimeSource;
import net.minecraft.util.Util;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallbackI;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
@DontObfuscate
public class RenderSystem {
	static final Logger LOGGER = LogUtils.getLogger();
	public static final int MINIMUM_ATLAS_TEXTURE_SIZE = 1024;
	public static final int PROJECTION_MATRIX_UBO_SIZE = new Std140SizeCalculator().putMat4f().get();
	private static @Nullable Thread renderThread;
	private static @Nullable GpuDevice DEVICE;
	private static double lastDrawTime = Double.MIN_VALUE;
	private static final RenderSystem.AutoStorageIndexBuffer sharedSequential = new RenderSystem.AutoStorageIndexBuffer(1, 1, IntConsumer::accept);
	private static final RenderSystem.AutoStorageIndexBuffer sharedSequentialQuad = new RenderSystem.AutoStorageIndexBuffer(4, 6, (intConsumer, i) -> {
		intConsumer.accept(i);
		intConsumer.accept(i + 1);
		intConsumer.accept(i + 2);
		intConsumer.accept(i + 2);
		intConsumer.accept(i + 3);
		intConsumer.accept(i);
	});
	private static final RenderSystem.AutoStorageIndexBuffer sharedSequentialLines = new RenderSystem.AutoStorageIndexBuffer(4, 6, (intConsumer, i) -> {
		intConsumer.accept(i);
		intConsumer.accept(i + 1);
		intConsumer.accept(i + 2);
		intConsumer.accept(i + 3);
		intConsumer.accept(i + 2);
		intConsumer.accept(i + 1);
	});
	private static ProjectionType projectionType = ProjectionType.PERSPECTIVE;
	private static ProjectionType savedProjectionType = ProjectionType.PERSPECTIVE;
	private static final Matrix4fStack modelViewStack = new Matrix4fStack(16);
	private static @Nullable GpuBufferSlice shaderFog = null;
	private static @Nullable GpuBufferSlice shaderLightDirections;
	private static @Nullable GpuBufferSlice projectionMatrixBuffer;
	private static @Nullable GpuBufferSlice savedProjectionMatrixBuffer;
	private static String apiDescription = "Unknown";
	private static final AtomicLong pollEventsWaitStart = new AtomicLong();
	private static final AtomicBoolean pollingEvents = new AtomicBoolean(false);
	private static final ArrayListDeque<RenderSystem.GpuAsyncTask> PENDING_FENCES = new ArrayListDeque<>();
	public static @Nullable GpuTextureView outputColorTextureOverride;
	public static @Nullable GpuTextureView outputDepthTextureOverride;
	private static @Nullable GpuBuffer globalSettingsUniform;
	private static @Nullable DynamicUniforms dynamicUniforms;
	private static final ScissorState scissorStateForRenderTypeDraws = new ScissorState();
	private static SamplerCache samplerCache = new SamplerCache();

	public static SamplerCache getSamplerCache() {
		return samplerCache;
	}

	public static void initRenderThread() {
		if (renderThread != null) {
			throw new IllegalStateException("Could not initialize render thread");
		}

		renderThread = Thread.currentThread();
	}

	public static boolean isOnRenderThread() {
		return Thread.currentThread() == renderThread;
	}

	public static void assertOnRenderThread() {
		if (!isOnRenderThread()) {
			throw constructThreadException();
		}
	}

	private static IllegalStateException constructThreadException() {
		return new IllegalStateException("Rendersystem called from wrong thread");
	}

	private static void pollEvents() {
		pollEventsWaitStart.set(Util.getMillis());
		pollingEvents.set(true);
		GLFW.glfwPollEvents();
		pollingEvents.set(false);
	}

	public static boolean isFrozenAtPollEvents() {
		return pollingEvents.get() && Util.getMillis() - pollEventsWaitStart.get() > 200L;
	}

	public static void flipFrame(Window window, @Nullable TracyFrameCapture tracyFrameCapture) {
		pollEvents();
		Tesselator.getInstance().clear();
		GLFW.glfwSwapBuffers(window.handle());
		if (tracyFrameCapture != null) {
			tracyFrameCapture.endFrame();
		}

		dynamicUniforms.reset();
		Minecraft.getInstance().levelRenderer.endFrame();
		pollEvents();
	}

	public static void limitDisplayFPS(int i) {
		double d = lastDrawTime + 1.0 / i;

		double e;
		for (e = GLFW.glfwGetTime(); e < d; e = GLFW.glfwGetTime()) {
			GLFW.glfwWaitEventsTimeout(d - e);
		}

		lastDrawTime = e;
	}

	public static void setShaderFog(GpuBufferSlice gpuBufferSlice) {
		shaderFog = gpuBufferSlice;
	}

	public static @Nullable GpuBufferSlice getShaderFog() {
		return shaderFog;
	}

	public static void setShaderLights(GpuBufferSlice gpuBufferSlice) {
		shaderLightDirections = gpuBufferSlice;
	}

	public static @Nullable GpuBufferSlice getShaderLights() {
		return shaderLightDirections;
	}

	public static void enableScissorForRenderTypeDraws(int i, int j, int k, int l) {
		scissorStateForRenderTypeDraws.enable(i, j, k, l);
	}

	public static void disableScissorForRenderTypeDraws() {
		scissorStateForRenderTypeDraws.disable();
	}

	public static ScissorState getScissorStateForRenderTypeDraws() {
		return scissorStateForRenderTypeDraws;
	}

	public static String getBackendDescription() {
		return String.format(Locale.ROOT, "LWJGL version %s", GLX._getLWJGLVersion());
	}

	public static String getApiDescription() {
		return apiDescription;
	}

	public static TimeSource.NanoTimeSource initBackendSystem() {
		return GLX._initGlfw()::getAsLong;
	}

	public static void initRenderer(long l, int i, boolean bl, ShaderSource shaderSource, boolean bl2) {
		DEVICE = new GlDevice(l, i, bl, shaderSource, bl2);
		apiDescription = getDevice().getImplementationInformation();
		dynamicUniforms = new DynamicUniforms();
		samplerCache.initialize();
	}

	public static void setErrorCallback(GLFWErrorCallbackI gLFWErrorCallbackI) {
		GLX._setGlfwErrorCallback(gLFWErrorCallbackI);
	}

	public static void setupDefaultState() {
		modelViewStack.clear();
	}

	public static void setProjectionMatrix(GpuBufferSlice gpuBufferSlice, ProjectionType projectionType) {
		assertOnRenderThread();
		projectionMatrixBuffer = gpuBufferSlice;
		RenderSystem.projectionType = projectionType;
	}

	public static void backupProjectionMatrix() {
		assertOnRenderThread();
		savedProjectionMatrixBuffer = projectionMatrixBuffer;
		savedProjectionType = projectionType;
	}

	public static void restoreProjectionMatrix() {
		assertOnRenderThread();
		projectionMatrixBuffer = savedProjectionMatrixBuffer;
		projectionType = savedProjectionType;
	}

	public static @Nullable GpuBufferSlice getProjectionMatrixBuffer() {
		assertOnRenderThread();
		return projectionMatrixBuffer;
	}

	public static Matrix4f getModelViewMatrix() {
		assertOnRenderThread();
		return modelViewStack;
	}

	public static Matrix4fStack getModelViewStack() {
		assertOnRenderThread();
		return modelViewStack;
	}

	public static RenderSystem.AutoStorageIndexBuffer getSequentialBuffer(VertexFormat.Mode mode) {
		assertOnRenderThread();

		return switch (mode) {
			case QUADS -> sharedSequentialQuad;
			case LINES -> sharedSequentialLines;
			default -> sharedSequential;
		};
	}

	public static void setGlobalSettingsUniform(GpuBuffer gpuBuffer) {
		globalSettingsUniform = gpuBuffer;
	}

	public static @Nullable GpuBuffer getGlobalSettingsUniform() {
		return globalSettingsUniform;
	}

	public static ProjectionType getProjectionType() {
		assertOnRenderThread();
		return projectionType;
	}

	public static void queueFencedTask(Runnable runnable) {
		PENDING_FENCES.addLast(new RenderSystem.GpuAsyncTask(runnable, getDevice().createCommandEncoder().createFence()));
	}

	public static void executePendingTasks() {
		for (RenderSystem.GpuAsyncTask gpuAsyncTask = PENDING_FENCES.peekFirst(); gpuAsyncTask != null; gpuAsyncTask = PENDING_FENCES.peekFirst()) {
			if (!gpuAsyncTask.fence.awaitCompletion(0L)) {
				return;
			}

			try {
				gpuAsyncTask.callback.run();
			} finally {
				gpuAsyncTask.fence.close();
			}

			PENDING_FENCES.removeFirst();
		}
	}

	public static GpuDevice getDevice() {
		if (DEVICE == null) {
			throw new IllegalStateException("Can't getDevice() before it was initialized");
		} else {
			return DEVICE;
		}
	}

	public static @Nullable GpuDevice tryGetDevice() {
		return DEVICE;
	}

	public static DynamicUniforms getDynamicUniforms() {
		if (dynamicUniforms == null) {
			throw new IllegalStateException("Can't getDynamicUniforms() before device was initialized");
		} else {
			return dynamicUniforms;
		}
	}

	public static void bindDefaultUniforms(RenderPass renderPass) {
		GpuBufferSlice gpuBufferSlice = getProjectionMatrixBuffer();
		if (gpuBufferSlice != null) {
			renderPass.setUniform("Projection", gpuBufferSlice);
		}

		GpuBufferSlice gpuBufferSlice2 = getShaderFog();
		if (gpuBufferSlice2 != null) {
			renderPass.setUniform("Fog", gpuBufferSlice2);
		}

		GpuBuffer gpuBuffer = getGlobalSettingsUniform();
		if (gpuBuffer != null) {
			renderPass.setUniform("Globals", gpuBuffer);
		}

		GpuBufferSlice gpuBufferSlice3 = getShaderLights();
		if (gpuBufferSlice3 != null) {
			renderPass.setUniform("Lighting", gpuBufferSlice3);
		}
	}

	@Environment(EnvType.CLIENT)
	public static final class AutoStorageIndexBuffer {
		private final int vertexStride;
		private final int indexStride;
		private final RenderSystem.AutoStorageIndexBuffer.IndexGenerator generator;
		private @Nullable GpuBuffer buffer;
		private VertexFormat.IndexType type = VertexFormat.IndexType.SHORT;
		private int indexCount;

		AutoStorageIndexBuffer(int i, int j, RenderSystem.AutoStorageIndexBuffer.IndexGenerator indexGenerator) {
			this.vertexStride = i;
			this.indexStride = j;
			this.generator = indexGenerator;
		}

		public boolean hasStorage(int i) {
			return i <= this.indexCount;
		}

		public GpuBuffer getBuffer(int i) {
			this.ensureStorage(i);
			return this.buffer;
		}

		private void ensureStorage(int i) {
			if (!this.hasStorage(i)) {
				i = Mth.roundToward(i * 2, this.indexStride);
				RenderSystem.LOGGER.debug("Growing IndexBuffer: Old limit {}, new limit {}.", this.indexCount, i);
				int j = i / this.indexStride;
				int k = j * this.vertexStride;
				VertexFormat.IndexType indexType = VertexFormat.IndexType.least(k);
				int l = Mth.roundToward(i * indexType.bytes, 4);
				ByteBuffer byteBuffer = MemoryUtil.memAlloc(l);

				try {
					this.type = indexType;
					it.unimi.dsi.fastutil.ints.IntConsumer intConsumer = this.intConsumer(byteBuffer);

					for (int m = 0; m < i; m += this.indexStride) {
						this.generator.accept(intConsumer, m * this.vertexStride / this.indexStride);
					}

					byteBuffer.flip();
					if (this.buffer != null) {
						this.buffer.close();
					}

					this.buffer = RenderSystem.getDevice().createBuffer(() -> "Auto Storage index buffer", 64, byteBuffer);
				} finally {
					MemoryUtil.memFree(byteBuffer);
				}

				this.indexCount = i;
			}
		}

		private it.unimi.dsi.fastutil.ints.IntConsumer intConsumer(ByteBuffer byteBuffer) {
			switch (this.type) {
				case SHORT:
					return i -> byteBuffer.putShort((short)i);
				case INT:
				default:
					return byteBuffer::putInt;
			}
		}

		public VertexFormat.IndexType type() {
			return this.type;
		}

		@Environment(EnvType.CLIENT)
		interface IndexGenerator {
			void accept(it.unimi.dsi.fastutil.ints.IntConsumer intConsumer, int i);
		}
	}

	@Environment(EnvType.CLIENT)
	record GpuAsyncTask(Runnable callback, GpuFence fence) {
	}
}
