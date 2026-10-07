package net.minecraft.client.renderer.chunk;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;

@Environment(EnvType.CLIENT)
public enum ChunkSectionLayer {
	SOLID(RenderPipelines.SOLID_TERRAIN, 4194304, false),
	CUTOUT(RenderPipelines.CUTOUT_TERRAIN, 4194304, false),
	TRANSLUCENT(RenderPipelines.TRANSLUCENT_TERRAIN, 786432, true),
	TRIPWIRE(RenderPipelines.TRIPWIRE_TERRAIN, 1536, true);

	private final RenderPipeline pipeline;
	private final int bufferSize;
	private final boolean sortOnUpload;
	private final String label;

	ChunkSectionLayer(final RenderPipeline renderPipeline, final int j, final boolean bl) {
		this.pipeline = renderPipeline;
		this.bufferSize = j;
		this.sortOnUpload = bl;
		this.label = this.toString().toLowerCase(Locale.ROOT);
	}

	public RenderPipeline pipeline() {
		return this.pipeline;
	}

	public int bufferSize() {
		return this.bufferSize;
	}

	public String label() {
		return this.label;
	}

	public boolean sortOnUpload() {
		return this.sortOnUpload;
	}
}
