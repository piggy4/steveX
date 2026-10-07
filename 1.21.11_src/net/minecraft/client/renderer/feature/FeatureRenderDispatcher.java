package net.minecraft.client.renderer.feature;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.AtlasManager;

@Environment(EnvType.CLIENT)
public class FeatureRenderDispatcher implements AutoCloseable {
	private final SubmitNodeStorage submitNodeStorage;
	private final BlockRenderDispatcher blockRenderDispatcher;
	private final MultiBufferSource.BufferSource bufferSource;
	private final AtlasManager atlasManager;
	private final OutlineBufferSource outlineBufferSource;
	private final MultiBufferSource.BufferSource crumblingBufferSource;
	private final Font font;
	private final ShadowFeatureRenderer shadowFeatureRenderer = new ShadowFeatureRenderer();
	private final FlameFeatureRenderer flameFeatureRenderer = new FlameFeatureRenderer();
	private final ModelFeatureRenderer modelFeatureRenderer = new ModelFeatureRenderer();
	private final ModelPartFeatureRenderer modelPartFeatureRenderer = new ModelPartFeatureRenderer();
	private final NameTagFeatureRenderer nameTagFeatureRenderer = new NameTagFeatureRenderer();
	private final TextFeatureRenderer textFeatureRenderer = new TextFeatureRenderer();
	private final LeashFeatureRenderer leashFeatureRenderer = new LeashFeatureRenderer();
	private final ItemFeatureRenderer itemFeatureRenderer = new ItemFeatureRenderer();
	private final CustomFeatureRenderer customFeatureRenderer = new CustomFeatureRenderer();
	private final BlockFeatureRenderer blockFeatureRenderer = new BlockFeatureRenderer();
	private final ParticleFeatureRenderer particleFeatureRenderer = new ParticleFeatureRenderer();

	public FeatureRenderDispatcher(
		SubmitNodeStorage submitNodeStorage,
		BlockRenderDispatcher blockRenderDispatcher,
		MultiBufferSource.BufferSource bufferSource,
		AtlasManager atlasManager,
		OutlineBufferSource outlineBufferSource,
		MultiBufferSource.BufferSource bufferSource2,
		Font font
	) {
		this.submitNodeStorage = submitNodeStorage;
		this.blockRenderDispatcher = blockRenderDispatcher;
		this.bufferSource = bufferSource;
		this.atlasManager = atlasManager;
		this.outlineBufferSource = outlineBufferSource;
		this.crumblingBufferSource = bufferSource2;
		this.font = font;
	}

	public void renderAllFeatures() {
		for (SubmitNodeCollection submitNodeCollection : this.submitNodeStorage.getSubmitsPerOrder().values()) {
			this.shadowFeatureRenderer.render(submitNodeCollection, this.bufferSource);
			this.modelFeatureRenderer.render(submitNodeCollection, this.bufferSource, this.outlineBufferSource, this.crumblingBufferSource);
			this.modelPartFeatureRenderer.render(submitNodeCollection, this.bufferSource, this.outlineBufferSource, this.crumblingBufferSource);
			this.flameFeatureRenderer.render(submitNodeCollection, this.bufferSource, this.atlasManager);
			this.nameTagFeatureRenderer.render(submitNodeCollection, this.bufferSource, this.font);
			this.textFeatureRenderer.render(submitNodeCollection, this.bufferSource);
			this.leashFeatureRenderer.render(submitNodeCollection, this.bufferSource);
			this.itemFeatureRenderer.render(submitNodeCollection, this.bufferSource, this.outlineBufferSource);
			this.blockFeatureRenderer.render(submitNodeCollection, this.bufferSource, this.blockRenderDispatcher, this.outlineBufferSource);
			this.customFeatureRenderer.render(submitNodeCollection, this.bufferSource);
			this.particleFeatureRenderer.render(submitNodeCollection);
		}

		this.submitNodeStorage.clear();
	}

	public void endFrame() {
		this.particleFeatureRenderer.endFrame();
	}

	public SubmitNodeStorage getSubmitNodeStorage() {
		return this.submitNodeStorage;
	}

	@Override
	public void close() {
		this.particleFeatureRenderer.close();
	}
}
