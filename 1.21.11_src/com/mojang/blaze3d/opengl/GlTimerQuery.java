package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.systems.GpuQuery;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.OptionalLong;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.opengl.ARBTimerQuery;
import org.lwjgl.opengl.GL32C;

@Environment(EnvType.CLIENT)
public class GlTimerQuery implements GpuQuery {
	private final int queryId;
	private boolean closed;
	private OptionalLong result = OptionalLong.empty();

	GlTimerQuery(int i) {
		this.queryId = i;
	}

	@Override
	public OptionalLong getValue() {
		RenderSystem.assertOnRenderThread();
		if (this.closed) {
			throw new IllegalStateException("GlTimerQuery is closed");
		} else if (this.result.isPresent()) {
			return this.result;
		} else if (GL32C.glGetQueryObjecti(this.queryId, 34919) == 1) {
			this.result = OptionalLong.of(ARBTimerQuery.glGetQueryObjecti64(this.queryId, 34918));
			return this.result;
		} else {
			return OptionalLong.empty();
		}
	}

	@Override
	public void close() {
		RenderSystem.assertOnRenderThread();
		if (!this.closed) {
			this.closed = true;
			GL32C.glDeleteQueries(this.queryId);
		}
	}
}
