package com.mojang.blaze3d.pipeline;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class TextureTarget extends RenderTarget {
	public TextureTarget(@Nullable String string, int i, int j, boolean bl) {
		super(string, bl);
		RenderSystem.assertOnRenderThread();
		this.resize(i, j);
	}
}
