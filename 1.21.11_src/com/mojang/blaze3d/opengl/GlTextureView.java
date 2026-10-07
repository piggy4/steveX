package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class GlTextureView extends GpuTextureView {
	private static final int EMPTY = -1;
	private boolean closed;
	private int firstFboId = -1;
	private int firstFboDepthId = -1;
	private @Nullable Int2IntMap fboCache;

	protected GlTextureView(GlTexture glTexture, int i, int j) {
		super(glTexture, i, j);
		glTexture.addViews();
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}

	@Override
	public void close() {
		if (!this.closed) {
			this.closed = true;
			this.texture().removeViews();
			if (this.firstFboId != -1) {
				GlStateManager._glDeleteFramebuffers(this.firstFboId);
			}

			if (this.fboCache != null) {
				for (int i : this.fboCache.values()) {
					GlStateManager._glDeleteFramebuffers(i);
				}
			}
		}
	}

	public int getFbo(DirectStateAccess directStateAccess, @Nullable GpuTexture gpuTexture) {
		int i = gpuTexture == null ? 0 : ((GlTexture)gpuTexture).id;
		if (this.firstFboDepthId == i) {
			return this.firstFboId;
		}

		if (this.firstFboId == -1) {
			this.firstFboId = this.createFbo(directStateAccess, i);
			this.firstFboDepthId = i;
			return this.firstFboId;
		}

		if (this.fboCache == null) {
			this.fboCache = new Int2IntArrayMap();
		}

		return this.fboCache.computeIfAbsent(i, ix -> this.createFbo(directStateAccess, ix));
	}

	private int createFbo(DirectStateAccess directStateAccess, int i) {
		int j = directStateAccess.createFrameBufferObject();
		directStateAccess.bindFrameBufferTextures(j, this.texture().id, i, this.baseMipLevel(), 0);
		return j;
	}

	public GlTexture texture() {
		return (GlTexture)super.texture();
	}
}
