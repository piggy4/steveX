package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.buffers.GpuFence;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class GlFence implements GpuFence {
	private long handle = GlStateManager._glFenceSync(37143, 0);

	@Override
	public void close() {
		if (this.handle != 0L) {
			GlStateManager._glDeleteSync(this.handle);
			this.handle = 0L;
		}
	}

	@Override
	public boolean awaitCompletion(long l) {
		if (this.handle == 0L) {
			return true;
		} else {
			int i = GlStateManager._glClientWaitSync(this.handle, 0, l);
			if (i == 37147) {
				return false;
			} else if (i == 37149) {
				throw new IllegalStateException("Failed to complete GPU fence: " + GlStateManager._getError());
			} else {
				return true;
			}
		}
	}
}
