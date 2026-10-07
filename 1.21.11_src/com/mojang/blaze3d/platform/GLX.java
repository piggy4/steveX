package com.mojang.blaze3d.platform;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.DontObfuscate;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;
import org.lwjgl.Version;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWErrorCallbackI;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

@Environment(EnvType.CLIENT)
@DontObfuscate
public class GLX {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static @Nullable String cpuInfo;

	public static int _getRefreshRate(Window window) {
		RenderSystem.assertOnRenderThread();
		long l = GLFW.glfwGetWindowMonitor(window.handle());
		if (l == 0L) {
			l = GLFW.glfwGetPrimaryMonitor();
		}

		GLFWVidMode gLFWVidMode = l == 0L ? null : GLFW.glfwGetVideoMode(l);
		return gLFWVidMode == null ? 0 : gLFWVidMode.refreshRate();
	}

	public static String _getLWJGLVersion() {
		return Version.getVersion();
	}

	public static LongSupplier _initGlfw() {
		Window.checkGlfwError((integer, stringx) -> {
			throw new IllegalStateException(String.format(Locale.ROOT, "GLFW error before init: [0x%X]%s", integer, stringx));
		});
		List<String> list = Lists.newArrayList();
		GLFWErrorCallback gLFWErrorCallback = GLFW.glfwSetErrorCallback((i, l) -> {
			String stringx = l == 0L ? "" : MemoryUtil.memUTF8(l);
			list.add(String.format(Locale.ROOT, "GLFW error during init: [0x%X]%s", i, stringx));
		});
		if (!GLFW.glfwInit()) {
			throw new IllegalStateException("Failed to initialize GLFW, errors: " + Joiner.on(",").join(list));
		}

		LongSupplier longSupplier = () -> (long)(GLFW.glfwGetTime() * 1.0E9);

		for (String string : list) {
			LOGGER.error("GLFW error collected during initialization: {}", string);
		}

		RenderSystem.setErrorCallback(gLFWErrorCallback);
		return longSupplier;
	}

	public static void _setGlfwErrorCallback(GLFWErrorCallbackI gLFWErrorCallbackI) {
		GLFWErrorCallback gLFWErrorCallback = GLFW.glfwSetErrorCallback(gLFWErrorCallbackI);
		if (gLFWErrorCallback != null) {
			gLFWErrorCallback.free();
		}
	}

	public static boolean _shouldClose(Window window) {
		return GLFW.glfwWindowShouldClose(window.handle());
	}

	public static String _getCpuInfo() {
		if (cpuInfo == null) {
			cpuInfo = "<unknown>";

			try {
				CentralProcessor centralProcessor = new SystemInfo().getHardware().getProcessor();
				cpuInfo = String.format(Locale.ROOT, "%dx %s", centralProcessor.getLogicalProcessorCount(), centralProcessor.getProcessorIdentifier().getName())
					.replaceAll("\\s+", " ");
			} catch (Throwable var1) {
			}
		}

		return cpuInfo;
	}

	public static <T> T make(Supplier<T> supplier) {
		return supplier.get();
	}

	public static <T> T make(T object, Consumer<T> consumer) {
		consumer.accept(object);
		return object;
	}
}
