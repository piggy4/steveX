package com.mojang.blaze3d.opengl;

import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.DebugMemoryUntracker;
import com.mojang.blaze3d.platform.GLX;
import com.mojang.logging.LogUtils;
import java.util.HexFormat;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.ARBDebugOutput;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GLDebugMessageARBCallback;
import org.lwjgl.opengl.GLDebugMessageCallback;
import org.lwjgl.opengl.KHRDebug;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class GlDebug {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int CIRCULAR_LOG_SIZE = 10;
	private final Queue<GlDebug.LogEntry> MESSAGE_BUFFER = EvictingQueue.create(10);
	private volatile GlDebug.@Nullable LogEntry lastEntry;
	private static final List<Integer> DEBUG_LEVELS = ImmutableList.of(37190, 37191, 37192, 33387);
	private static final List<Integer> DEBUG_LEVELS_ARB = ImmutableList.of(37190, 37191, 37192);

	private static String printUnknownToken(int i) {
		return "Unknown (0x" + HexFormat.of().withUpperCase().toHexDigits(i) + ")";
	}

	public static String sourceToString(int i) {
		switch (i) {
			case 33350:
				return "API";
			case 33351:
				return "WINDOW SYSTEM";
			case 33352:
				return "SHADER COMPILER";
			case 33353:
				return "THIRD PARTY";
			case 33354:
				return "APPLICATION";
			case 33355:
				return "OTHER";
			default:
				return printUnknownToken(i);
		}
	}

	public static String typeToString(int i) {
		switch (i) {
			case 33356:
				return "ERROR";
			case 33357:
				return "DEPRECATED BEHAVIOR";
			case 33358:
				return "UNDEFINED BEHAVIOR";
			case 33359:
				return "PORTABILITY";
			case 33360:
				return "PERFORMANCE";
			case 33361:
				return "OTHER";
			case 33384:
				return "MARKER";
			default:
				return printUnknownToken(i);
		}
	}

	public static String severityToString(int i) {
		switch (i) {
			case 33387:
				return "NOTIFICATION";
			case 37190:
				return "HIGH";
			case 37191:
				return "MEDIUM";
			case 37192:
				return "LOW";
			default:
				return printUnknownToken(i);
		}
	}

	private void printDebugLog(int i, int j, int k, int l, int m, long n, long o) {
		String string = GLDebugMessageCallback.getMessage(m, n);
		GlDebug.LogEntry logEntry;
		synchronized (this.MESSAGE_BUFFER) {
			logEntry = this.lastEntry;
			if (logEntry != null && logEntry.isSame(i, j, k, l, string)) {
				logEntry.count++;
			} else {
				logEntry = new GlDebug.LogEntry(i, j, k, l, string);
				this.MESSAGE_BUFFER.add(logEntry);
				this.lastEntry = logEntry;
			}
		}

		LOGGER.info("OpenGL debug message: {}", logEntry);
	}

	public List<String> getLastOpenGlDebugMessages() {
		synchronized (this.MESSAGE_BUFFER) {
			List<String> list = Lists.newArrayListWithCapacity(this.MESSAGE_BUFFER.size());

			for (GlDebug.LogEntry logEntry : this.MESSAGE_BUFFER) {
				list.add(logEntry + " x " + logEntry.count);
			}

			return list;
		}
	}

	public static @Nullable GlDebug enableDebugCallback(int i, boolean bl, Set<String> set) {
		if (i <= 0) {
			return null;
		}

		GLCapabilities gLCapabilities = GL.getCapabilities();
		if (gLCapabilities.GL_KHR_debug && GlDevice.USE_GL_KHR_debug) {
			GlDebug glDebug = new GlDebug();
			set.add("GL_KHR_debug");
			GL11.glEnable(37600);
			if (bl) {
				GL11.glEnable(33346);
			}

			for (int j = 0; j < DEBUG_LEVELS.size(); j++) {
				boolean bl2 = j < i;
				KHRDebug.glDebugMessageControl(4352, 4352, DEBUG_LEVELS.get(j), (int[])null, bl2);
			}

			KHRDebug.glDebugMessageCallback(GLX.make(GLDebugMessageCallback.create(glDebug::printDebugLog), DebugMemoryUntracker::untrack), 0L);
			return glDebug;
		} else if (gLCapabilities.GL_ARB_debug_output && GlDevice.USE_GL_ARB_debug_output) {
			GlDebug glDebug = new GlDebug();
			set.add("GL_ARB_debug_output");
			if (bl) {
				GL11.glEnable(33346);
			}

			for (int j = 0; j < DEBUG_LEVELS_ARB.size(); j++) {
				boolean bl2 = j < i;
				ARBDebugOutput.glDebugMessageControlARB(4352, 4352, DEBUG_LEVELS_ARB.get(j), (int[])null, bl2);
			}

			ARBDebugOutput.glDebugMessageCallbackARB(GLX.make(GLDebugMessageARBCallback.create(glDebug::printDebugLog), DebugMemoryUntracker::untrack), 0L);
			return glDebug;
		} else {
			return null;
		}
	}

	@Environment(EnvType.CLIENT)
	static class LogEntry {
		private final int id;
		private final int source;
		private final int type;
		private final int severity;
		private final String message;
		int count = 1;

		LogEntry(int i, int j, int k, int l, String string) {
			this.id = k;
			this.source = i;
			this.type = j;
			this.severity = l;
			this.message = string;
		}

		boolean isSame(int i, int j, int k, int l, String string) {
			return j == this.type && i == this.source && k == this.id && l == this.severity && string.equals(this.message);
		}

		@Override
		public String toString() {
			return "id="
				+ this.id
				+ ", source="
				+ GlDebug.sourceToString(this.source)
				+ ", type="
				+ GlDebug.typeToString(this.type)
				+ ", severity="
				+ GlDebug.severityToString(this.severity)
				+ ", message='"
				+ this.message
				+ "'";
		}
	}
}
