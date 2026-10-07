package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.DontObfuscate;
import com.mojang.blaze3d.platform.MacosUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.jtracy.Plot;
import com.mojang.jtracy.TracyClient;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

@Environment(EnvType.CLIENT)
@DontObfuscate
public class GlStateManager {
	private static final Plot PLOT_TEXTURES = TracyClient.createPlot("GPU Textures");
	private static int numTextures = 0;
	private static final Plot PLOT_BUFFERS = TracyClient.createPlot("GPU Buffers");
	private static int numBuffers = 0;
	private static final GlStateManager.BlendState BLEND = new GlStateManager.BlendState();
	private static final GlStateManager.DepthState DEPTH = new GlStateManager.DepthState();
	private static final GlStateManager.CullState CULL = new GlStateManager.CullState();
	private static final GlStateManager.PolygonOffsetState POLY_OFFSET = new GlStateManager.PolygonOffsetState();
	private static final GlStateManager.ColorLogicState COLOR_LOGIC = new GlStateManager.ColorLogicState();
	private static final GlStateManager.ScissorState SCISSOR = new GlStateManager.ScissorState();
	private static int activeTexture;
	private static final int TEXTURE_COUNT = 12;
	private static final GlStateManager.TextureState[] TEXTURES = IntStream.range(0, 12)
		.mapToObj(i -> new GlStateManager.TextureState())
		.toArray(GlStateManager.TextureState[]::new);
	private static final GlStateManager.ColorMask COLOR_MASK = new GlStateManager.ColorMask();
	private static int readFbo;
	private static int writeFbo;

	public static void _disableScissorTest() {
		RenderSystem.assertOnRenderThread();
		SCISSOR.mode.disable();
	}

	public static void _enableScissorTest() {
		RenderSystem.assertOnRenderThread();
		SCISSOR.mode.enable();
	}

	public static void _scissorBox(int i, int j, int k, int l) {
		RenderSystem.assertOnRenderThread();
		GL20.glScissor(i, j, k, l);
	}

	public static void _disableDepthTest() {
		RenderSystem.assertOnRenderThread();
		DEPTH.mode.disable();
	}

	public static void _enableDepthTest() {
		RenderSystem.assertOnRenderThread();
		DEPTH.mode.enable();
	}

	public static void _depthFunc(int i) {
		RenderSystem.assertOnRenderThread();
		if (i != DEPTH.func) {
			DEPTH.func = i;
			GL11.glDepthFunc(i);
		}
	}

	public static void _depthMask(boolean bl) {
		RenderSystem.assertOnRenderThread();
		if (bl != DEPTH.mask) {
			DEPTH.mask = bl;
			GL11.glDepthMask(bl);
		}
	}

	public static void _disableBlend() {
		RenderSystem.assertOnRenderThread();
		BLEND.mode.disable();
	}

	public static void _enableBlend() {
		RenderSystem.assertOnRenderThread();
		BLEND.mode.enable();
	}

	public static void _blendFuncSeparate(int i, int j, int k, int l) {
		RenderSystem.assertOnRenderThread();
		if (i != BLEND.srcRgb || j != BLEND.dstRgb || k != BLEND.srcAlpha || l != BLEND.dstAlpha) {
			BLEND.srcRgb = i;
			BLEND.dstRgb = j;
			BLEND.srcAlpha = k;
			BLEND.dstAlpha = l;
			glBlendFuncSeparate(i, j, k, l);
		}
	}

	public static int glGetProgrami(int i, int j) {
		RenderSystem.assertOnRenderThread();
		return GL20.glGetProgrami(i, j);
	}

	public static void glAttachShader(int i, int j) {
		RenderSystem.assertOnRenderThread();
		GL20.glAttachShader(i, j);
	}

	public static void glDeleteShader(int i) {
		RenderSystem.assertOnRenderThread();
		GL20.glDeleteShader(i);
	}

	public static int glCreateShader(int i) {
		RenderSystem.assertOnRenderThread();
		return GL20.glCreateShader(i);
	}

	public static void glShaderSource(int i, String string) {
		RenderSystem.assertOnRenderThread();
		byte[] bs = string.getBytes(StandardCharsets.UTF_8);
		ByteBuffer byteBuffer = MemoryUtil.memAlloc(bs.length + 1);
		byteBuffer.put(bs);
		byteBuffer.put((byte)0);
		byteBuffer.flip();

		try (MemoryStack memoryStack = MemoryStack.stackPush()) {
			PointerBuffer pointerBuffer = memoryStack.mallocPointer(1);
			pointerBuffer.put(byteBuffer);
			GL20C.nglShaderSource(i, 1, pointerBuffer.address0(), 0L);
		} finally {
			MemoryUtil.memFree(byteBuffer);
		}
	}

	public static void glCompileShader(int i) {
		RenderSystem.assertOnRenderThread();
		GL20.glCompileShader(i);
	}

	public static int glGetShaderi(int i, int j) {
		RenderSystem.assertOnRenderThread();
		return GL20.glGetShaderi(i, j);
	}

	public static void _glUseProgram(int i) {
		RenderSystem.assertOnRenderThread();
		GL20.glUseProgram(i);
	}

	public static int glCreateProgram() {
		RenderSystem.assertOnRenderThread();
		return GL20.glCreateProgram();
	}

	public static void glDeleteProgram(int i) {
		RenderSystem.assertOnRenderThread();
		GL20.glDeleteProgram(i);
	}

	public static void glLinkProgram(int i) {
		RenderSystem.assertOnRenderThread();
		GL20.glLinkProgram(i);
	}

	public static int _glGetUniformLocation(int i, CharSequence charSequence) {
		RenderSystem.assertOnRenderThread();
		return GL20.glGetUniformLocation(i, charSequence);
	}

	public static void _glUniform1i(int i, int j) {
		RenderSystem.assertOnRenderThread();
		GL20.glUniform1i(i, j);
	}

	public static void _glBindAttribLocation(int i, int j, CharSequence charSequence) {
		RenderSystem.assertOnRenderThread();
		GL20.glBindAttribLocation(i, j, charSequence);
	}

	public static void incrementTrackedBuffers() {
		numBuffers++;
		PLOT_BUFFERS.setValue(numBuffers);
	}

	public static int _glGenBuffers() {
		RenderSystem.assertOnRenderThread();
		incrementTrackedBuffers();
		return GL15.glGenBuffers();
	}

	public static int _glGenVertexArrays() {
		RenderSystem.assertOnRenderThread();
		return GL30.glGenVertexArrays();
	}

	public static void _glBindBuffer(int i, int j) {
		RenderSystem.assertOnRenderThread();
		GL15.glBindBuffer(i, j);
	}

	public static void _glBindVertexArray(int i) {
		RenderSystem.assertOnRenderThread();
		GL30.glBindVertexArray(i);
	}

	public static void _glBufferData(int i, ByteBuffer byteBuffer, int j) {
		RenderSystem.assertOnRenderThread();
		GL15.glBufferData(i, byteBuffer, j);
	}

	public static void _glBufferSubData(int i, long l, ByteBuffer byteBuffer) {
		RenderSystem.assertOnRenderThread();
		GL15.glBufferSubData(i, l, byteBuffer);
	}

	public static void _glBufferData(int i, long l, int j) {
		RenderSystem.assertOnRenderThread();
		GL15.glBufferData(i, l, j);
	}

	public static @Nullable ByteBuffer _glMapBufferRange(int i, long l, long m, int j) {
		RenderSystem.assertOnRenderThread();
		return GL30.glMapBufferRange(i, l, m, j);
	}

	public static void _glUnmapBuffer(int i) {
		RenderSystem.assertOnRenderThread();
		GL15.glUnmapBuffer(i);
	}

	public static void _glDeleteBuffers(int i) {
		RenderSystem.assertOnRenderThread();
		numBuffers--;
		PLOT_BUFFERS.setValue(numBuffers);
		GL15.glDeleteBuffers(i);
	}

	public static void _glBindFramebuffer(int i, int j) {
		if ((i == 36008 || i == 36160) && readFbo != j) {
			GL30.glBindFramebuffer(36008, j);
			readFbo = j;
		}

		if ((i == 36009 || i == 36160) && writeFbo != j) {
			GL30.glBindFramebuffer(36009, j);
			writeFbo = j;
		}
	}

	public static int getFrameBuffer(int i) {
		if (i == 36008) {
			return readFbo;
		} else {
			return i == 36009 ? writeFbo : 0;
		}
	}

	public static void _glBlitFrameBuffer(int i, int j, int k, int l, int m, int n, int o, int p, int q, int r) {
		RenderSystem.assertOnRenderThread();
		GL30.glBlitFramebuffer(i, j, k, l, m, n, o, p, q, r);
	}

	public static void _glDeleteFramebuffers(int i) {
		RenderSystem.assertOnRenderThread();
		GL30.glDeleteFramebuffers(i);
		if (readFbo == i) {
			readFbo = 0;
		}

		if (writeFbo == i) {
			writeFbo = 0;
		}
	}

	public static int glGenFramebuffers() {
		RenderSystem.assertOnRenderThread();
		return GL30.glGenFramebuffers();
	}

	public static void _glFramebufferTexture2D(int i, int j, int k, int l, int m) {
		RenderSystem.assertOnRenderThread();
		GL30.glFramebufferTexture2D(i, j, k, l, m);
	}

	public static void glBlendFuncSeparate(int i, int j, int k, int l) {
		RenderSystem.assertOnRenderThread();
		GL14.glBlendFuncSeparate(i, j, k, l);
	}

	public static String glGetShaderInfoLog(int i, int j) {
		RenderSystem.assertOnRenderThread();
		return GL20.glGetShaderInfoLog(i, j);
	}

	public static String glGetProgramInfoLog(int i, int j) {
		RenderSystem.assertOnRenderThread();
		return GL20.glGetProgramInfoLog(i, j);
	}

	public static void _enableCull() {
		RenderSystem.assertOnRenderThread();
		CULL.enable.enable();
	}

	public static void _disableCull() {
		RenderSystem.assertOnRenderThread();
		CULL.enable.disable();
	}

	public static void _polygonMode(int i, int j) {
		RenderSystem.assertOnRenderThread();
		GL11.glPolygonMode(i, j);
	}

	public static void _enablePolygonOffset() {
		RenderSystem.assertOnRenderThread();
		POLY_OFFSET.fill.enable();
	}

	public static void _disablePolygonOffset() {
		RenderSystem.assertOnRenderThread();
		POLY_OFFSET.fill.disable();
	}

	public static void _polygonOffset(float f, float g) {
		RenderSystem.assertOnRenderThread();
		if (f != POLY_OFFSET.factor || g != POLY_OFFSET.units) {
			POLY_OFFSET.factor = f;
			POLY_OFFSET.units = g;
			GL11.glPolygonOffset(f, g);
		}
	}

	public static void _enableColorLogicOp() {
		RenderSystem.assertOnRenderThread();
		COLOR_LOGIC.enable.enable();
	}

	public static void _disableColorLogicOp() {
		RenderSystem.assertOnRenderThread();
		COLOR_LOGIC.enable.disable();
	}

	public static void _logicOp(int i) {
		RenderSystem.assertOnRenderThread();
		if (i != COLOR_LOGIC.op) {
			COLOR_LOGIC.op = i;
			GL11.glLogicOp(i);
		}
	}

	public static void _activeTexture(int i) {
		RenderSystem.assertOnRenderThread();
		if (activeTexture != i - 33984) {
			activeTexture = i - 33984;
			GL13.glActiveTexture(i);
		}
	}

	public static void _texParameter(int i, int j, int k) {
		RenderSystem.assertOnRenderThread();
		GL11.glTexParameteri(i, j, k);
	}

	public static int _getTexLevelParameter(int i, int j, int k) {
		return GL11.glGetTexLevelParameteri(i, j, k);
	}

	public static int _genTexture() {
		RenderSystem.assertOnRenderThread();
		numTextures++;
		PLOT_TEXTURES.setValue(numTextures);
		return GL11.glGenTextures();
	}

	public static void _deleteTexture(int i) {
		RenderSystem.assertOnRenderThread();
		GL11.glDeleteTextures(i);

		for (GlStateManager.TextureState textureState : TEXTURES) {
			if (textureState.binding == i) {
				textureState.binding = -1;
			}
		}

		numTextures--;
		PLOT_TEXTURES.setValue(numTextures);
	}

	public static void _bindTexture(int i) {
		RenderSystem.assertOnRenderThread();
		if (i != TEXTURES[activeTexture].binding) {
			TEXTURES[activeTexture].binding = i;
			GL11.glBindTexture(3553, i);
		}
	}

	public static void _texImage2D(int i, int j, int k, int l, int m, int n, int o, int p, @Nullable ByteBuffer byteBuffer) {
		RenderSystem.assertOnRenderThread();
		GL11.glTexImage2D(i, j, k, l, m, n, o, p, byteBuffer);
	}

	public static void _texSubImage2D(int i, int j, int k, int l, int m, int n, int o, int p, long q) {
		RenderSystem.assertOnRenderThread();
		GL11.glTexSubImage2D(i, j, k, l, m, n, o, p, q);
	}

	public static void _texSubImage2D(int i, int j, int k, int l, int m, int n, int o, int p, ByteBuffer byteBuffer) {
		RenderSystem.assertOnRenderThread();
		GL11.glTexSubImage2D(i, j, k, l, m, n, o, p, byteBuffer);
	}

	public static void _viewport(int i, int j, int k, int l) {
		GL11.glViewport(i, j, k, l);
	}

	public static void _colorMask(boolean bl, boolean bl2, boolean bl3, boolean bl4) {
		RenderSystem.assertOnRenderThread();
		if (bl != COLOR_MASK.red || bl2 != COLOR_MASK.green || bl3 != COLOR_MASK.blue || bl4 != COLOR_MASK.alpha) {
			COLOR_MASK.red = bl;
			COLOR_MASK.green = bl2;
			COLOR_MASK.blue = bl3;
			COLOR_MASK.alpha = bl4;
			GL11.glColorMask(bl, bl2, bl3, bl4);
		}
	}

	public static void _clear(int i) {
		RenderSystem.assertOnRenderThread();
		GL11.glClear(i);
		if (MacosUtil.IS_MACOS) {
			_getError();
		}
	}

	public static void _vertexAttribPointer(int i, int j, int k, boolean bl, int l, long m) {
		RenderSystem.assertOnRenderThread();
		GL20.glVertexAttribPointer(i, j, k, bl, l, m);
	}

	public static void _vertexAttribIPointer(int i, int j, int k, int l, long m) {
		RenderSystem.assertOnRenderThread();
		GL30.glVertexAttribIPointer(i, j, k, l, m);
	}

	public static void _enableVertexAttribArray(int i) {
		RenderSystem.assertOnRenderThread();
		GL20.glEnableVertexAttribArray(i);
	}

	public static void _drawElements(int i, int j, int k, long l) {
		RenderSystem.assertOnRenderThread();
		GL11.glDrawElements(i, j, k, l);
	}

	public static void _drawArrays(int i, int j, int k) {
		RenderSystem.assertOnRenderThread();
		GL11.glDrawArrays(i, j, k);
	}

	public static void _pixelStore(int i, int j) {
		RenderSystem.assertOnRenderThread();
		GL11.glPixelStorei(i, j);
	}

	public static void _readPixels(int i, int j, int k, int l, int m, int n, long o) {
		RenderSystem.assertOnRenderThread();
		GL11.glReadPixels(i, j, k, l, m, n, o);
	}

	public static int _getError() {
		RenderSystem.assertOnRenderThread();
		return GL11.glGetError();
	}

	public static void clearGlErrors() {
		RenderSystem.assertOnRenderThread();

		while (GL11.glGetError() != 0) {
		}
	}

	public static String _getString(int i) {
		RenderSystem.assertOnRenderThread();
		return GL11.glGetString(i);
	}

	public static int _getInteger(int i) {
		RenderSystem.assertOnRenderThread();
		return GL11.glGetInteger(i);
	}

	public static long _glFenceSync(int i, int j) {
		RenderSystem.assertOnRenderThread();
		return GL32.glFenceSync(i, j);
	}

	public static int _glClientWaitSync(long l, int i, long m) {
		RenderSystem.assertOnRenderThread();
		return GL32.glClientWaitSync(l, i, m);
	}

	public static void _glDeleteSync(long l) {
		RenderSystem.assertOnRenderThread();
		GL32.glDeleteSync(l);
	}

	@Environment(EnvType.CLIENT)
	static class BlendState {
		public final GlStateManager.BooleanState mode = new GlStateManager.BooleanState(3042);
		public int srcRgb = 1;
		public int dstRgb = 0;
		public int srcAlpha = 1;
		public int dstAlpha = 0;
	}

	@Environment(EnvType.CLIENT)
	static class BooleanState {
		private final int state;
		private boolean enabled;

		public BooleanState(int i) {
			this.state = i;
		}

		public void disable() {
			this.setEnabled(false);
		}

		public void enable() {
			this.setEnabled(true);
		}

		public void setEnabled(boolean bl) {
			RenderSystem.assertOnRenderThread();
			if (bl != this.enabled) {
				this.enabled = bl;
				if (bl) {
					GL11.glEnable(this.state);
				} else {
					GL11.glDisable(this.state);
				}
			}
		}
	}

	@Environment(EnvType.CLIENT)
	static class ColorLogicState {
		public final GlStateManager.BooleanState enable = new GlStateManager.BooleanState(3058);
		public int op = 5379;
	}

	@Environment(EnvType.CLIENT)
	static class ColorMask {
		public boolean red = true;
		public boolean green = true;
		public boolean blue = true;
		public boolean alpha = true;
	}

	@Environment(EnvType.CLIENT)
	static class CullState {
		public final GlStateManager.BooleanState enable = new GlStateManager.BooleanState(2884);
	}

	@Environment(EnvType.CLIENT)
	static class DepthState {
		public final GlStateManager.BooleanState mode = new GlStateManager.BooleanState(2929);
		public boolean mask = true;
		public int func = 513;
	}

	@Environment(EnvType.CLIENT)
	static class PolygonOffsetState {
		public final GlStateManager.BooleanState fill = new GlStateManager.BooleanState(32823);
		public float factor;
		public float units;
	}

	@Environment(EnvType.CLIENT)
	static class ScissorState {
		public final GlStateManager.BooleanState mode = new GlStateManager.BooleanState(3089);
	}

	@Environment(EnvType.CLIENT)
	static class TextureState {
		public int binding;
	}
}
