package com.mojang.blaze3d.platform.cursor;

import com.mojang.blaze3d.platform.Window;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class CursorType {
	public static final CursorType DEFAULT = new CursorType("default", 0L);
	private final String name;
	private final long handle;

	private CursorType(String string, long l) {
		this.name = string;
		this.handle = l;
	}

	public void select(Window window) {
		GLFW.glfwSetCursor(window.handle(), this.handle);
	}

	@Override
	public String toString() {
		return this.name;
	}

	public static CursorType createStandardCursor(int i, String string, CursorType cursorType) {
		long l = GLFW.glfwCreateStandardCursor(i);
		return l == 0L ? cursorType : new CursorType(string, l);
	}
}
