package com.mojang.blaze3d.platform;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.StringDecomposer;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWErrorCallbackI;
import org.lwjgl.system.MemoryUtil;

@Environment(EnvType.CLIENT)
public class ClipboardManager {
	public static final int FORMAT_UNAVAILABLE = 65545;
	private final ByteBuffer clipboardScratchBuffer = BufferUtils.createByteBuffer(8192);

	public String getClipboard(Window window, GLFWErrorCallbackI gLFWErrorCallbackI) {
		GLFWErrorCallback gLFWErrorCallback = GLFW.glfwSetErrorCallback(gLFWErrorCallbackI);
		String string = GLFW.glfwGetClipboardString(window.handle());
		string = string != null ? StringDecomposer.filterBrokenSurrogates(string) : "";
		GLFWErrorCallback gLFWErrorCallback2 = GLFW.glfwSetErrorCallback(gLFWErrorCallback);
		if (gLFWErrorCallback2 != null) {
			gLFWErrorCallback2.free();
		}

		return string;
	}

	private static void pushClipboard(Window window, ByteBuffer byteBuffer, byte[] bs) {
		byteBuffer.clear();
		byteBuffer.put(bs);
		byteBuffer.put((byte)0);
		byteBuffer.flip();
		GLFW.glfwSetClipboardString(window.handle(), byteBuffer);
	}

	public void setClipboard(Window window, String string) {
		byte[] bs = string.getBytes(StandardCharsets.UTF_8);
		int i = bs.length + 1;
		if (i < this.clipboardScratchBuffer.capacity()) {
			pushClipboard(window, this.clipboardScratchBuffer, bs);
		} else {
			ByteBuffer byteBuffer = MemoryUtil.memAlloc(i);

			try {
				pushClipboard(window, byteBuffer, bs);
			} finally {
				MemoryUtil.memFree(byteBuffer);
			}
		}
	}
}
