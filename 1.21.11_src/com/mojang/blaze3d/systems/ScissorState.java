package com.mojang.blaze3d.systems;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ScissorState {
	private boolean enabled;
	private int x;
	private int y;
	private int width;
	private int height;

	public void enable(int i, int j, int k, int l) {
		this.enabled = true;
		this.x = i;
		this.y = j;
		this.width = k;
		this.height = l;
	}

	public void disable() {
		this.enabled = false;
	}

	public boolean enabled() {
		return this.enabled;
	}

	public int x() {
		return this.x;
	}

	public int y() {
		return this.y;
	}

	public int width() {
		return this.width;
	}

	public int height() {
		return this.height;
	}
}
