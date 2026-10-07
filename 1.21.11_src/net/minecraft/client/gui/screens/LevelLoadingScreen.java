package net.minecraft.client.gui.screens;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.blockentity.AbstractEndPortalRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class LevelLoadingScreen extends Screen {
	private static final Component DOWNLOADING_TERRAIN_TEXT = Component.translatable("multiplayer.downloadingTerrain");
	private static final Component READY_TO_PLAY_TEXT = Component.translatable("narrator.ready_to_play");
	private static final long NARRATION_DELAY_MS = 2000L;
	private static final int PROGRESS_BAR_WIDTH = 200;
	private LevelLoadTracker loadTracker;
	private float smoothedProgress;
	private long lastNarration = -1L;
	private LevelLoadingScreen.Reason reason;
	private @Nullable TextureAtlasSprite cachedNetherPortalSprite;
	private static final Object2IntMap<ChunkStatus> COLORS = Util.make(new Object2IntOpenHashMap<>(), object2IntOpenHashMap -> {
		object2IntOpenHashMap.defaultReturnValue(0);
		object2IntOpenHashMap.put(ChunkStatus.EMPTY, 5526612);
		object2IntOpenHashMap.put(ChunkStatus.STRUCTURE_STARTS, 10066329);
		object2IntOpenHashMap.put(ChunkStatus.STRUCTURE_REFERENCES, 6250897);
		object2IntOpenHashMap.put(ChunkStatus.BIOMES, 8434258);
		object2IntOpenHashMap.put(ChunkStatus.NOISE, 13750737);
		object2IntOpenHashMap.put(ChunkStatus.SURFACE, 7497737);
		object2IntOpenHashMap.put(ChunkStatus.CARVERS, 3159410);
		object2IntOpenHashMap.put(ChunkStatus.FEATURES, 2213376);
		object2IntOpenHashMap.put(ChunkStatus.INITIALIZE_LIGHT, 13421772);
		object2IntOpenHashMap.put(ChunkStatus.LIGHT, 16769184);
		object2IntOpenHashMap.put(ChunkStatus.SPAWN, 15884384);
		object2IntOpenHashMap.put(ChunkStatus.FULL, 16777215);
	});

	public LevelLoadingScreen(LevelLoadTracker levelLoadTracker, LevelLoadingScreen.Reason reason) {
		super(GameNarrator.NO_TITLE);
		this.loadTracker = levelLoadTracker;
		this.reason = reason;
	}

	public void update(LevelLoadTracker levelLoadTracker, LevelLoadingScreen.Reason reason) {
		this.loadTracker = levelLoadTracker;
		this.reason = reason;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	protected boolean shouldNarrateNavigation() {
		return false;
	}

	@Override
	protected void updateNarratedWidget(NarrationElementOutput narrationElementOutput) {
		if (this.loadTracker.hasProgress()) {
			narrationElementOutput.add(NarratedElementType.TITLE, Component.translatable("loading.progress", Mth.floor(this.loadTracker.serverProgress() * 100.0F)));
		}
	}

	@Override
	public void tick() {
		super.tick();
		this.smoothedProgress = this.smoothedProgress + (this.loadTracker.serverProgress() - this.smoothedProgress) * 0.2F;
		if (this.loadTracker.isLevelReady()) {
			this.onClose();
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		long l = Util.getMillis();
		if (l - this.lastNarration > 2000L) {
			this.lastNarration = l;
			this.triggerImmediateNarration(true);
		}

		int k = this.width / 2;
		int m = this.height / 2;
		ChunkLoadStatusView chunkLoadStatusView = this.loadTracker.statusView();
		int o;
		if (chunkLoadStatusView != null) {
			int n = 2;
			renderChunks(guiGraphics, k, m, 2, 0, chunkLoadStatusView);
			o = m - chunkLoadStatusView.radius() * 2 - 9 * 3;
		} else {
			o = m - 50;
		}

		guiGraphics.drawCenteredString(this.font, DOWNLOADING_TERRAIN_TEXT, k, o, -1);
		if (this.loadTracker.hasProgress()) {
			this.drawProgressBar(guiGraphics, k - 100, o + 9 + 3, 200, 2, this.smoothedProgress);
		}
	}

	private void drawProgressBar(GuiGraphics guiGraphics, int i, int j, int k, int l, float f) {
		guiGraphics.fill(i, j, i + k, j + l, -16777216);
		guiGraphics.fill(i, j, i + Math.round(f * k), j + l, -16711936);
	}

	public static void renderChunks(GuiGraphics guiGraphics, int i, int j, int k, int l, ChunkLoadStatusView chunkLoadStatusView) {
		int m = k + l;
		int n = chunkLoadStatusView.radius() * 2 + 1;
		int o = n * m - l;
		int p = i - o / 2;
		int q = j - o / 2;
		if (Minecraft.getInstance().debugEntries.isCurrentlyEnabled(DebugScreenEntries.VISUALIZE_CHUNKS_ON_SERVER)) {
			int r = m / 2 + 1;
			guiGraphics.fill(i - r, j - r, i + r, j + r, -65536);
		}

		for (int r = 0; r < n; r++) {
			for (int s = 0; s < n; s++) {
				ChunkStatus chunkStatus = chunkLoadStatusView.get(r, s);
				int t = p + r * m;
				int u = q + s * m;
				guiGraphics.fill(t, u, t + k, u + k, ARGB.opaque(COLORS.getInt(chunkStatus)));
			}
		}
	}

	@Override
	public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
		switch (this.reason) {
			case NETHER_PORTAL:
				guiGraphics.blitSprite(RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND, this.getNetherPortalSprite(), 0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight());
				break;
			case END_PORTAL:
				TextureManager textureManager = Minecraft.getInstance().getTextureManager();
				AbstractTexture abstractTexture = textureManager.getTexture(AbstractEndPortalRenderer.END_SKY_LOCATION);
				AbstractTexture abstractTexture2 = textureManager.getTexture(AbstractEndPortalRenderer.END_PORTAL_LOCATION);
				TextureSetup textureSetup = TextureSetup.doubleTexture(
					abstractTexture.getTextureView(), abstractTexture.getSampler(), abstractTexture2.getTextureView(), abstractTexture2.getSampler()
				);
				guiGraphics.fill(RenderPipelines.END_PORTAL, textureSetup, 0, 0, this.width, this.height);
				break;
			case OTHER:
				this.renderPanorama(guiGraphics, f);
				this.renderBlurredBackground(guiGraphics);
				this.renderMenuBackground(guiGraphics);
		}
	}

	private TextureAtlasSprite getNetherPortalSprite() {
		if (this.cachedNetherPortalSprite != null) {
			return this.cachedNetherPortalSprite;
		}

		this.cachedNetherPortalSprite = this.minecraft.getBlockRenderer().getBlockModelShaper().getParticleIcon(Blocks.NETHER_PORTAL.defaultBlockState());
		return this.cachedNetherPortalSprite;
	}

	@Override
	public void onClose() {
		this.minecraft.getNarrator().saySystemNow(READY_TO_PLAY_TEXT);
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Environment(EnvType.CLIENT)
	public enum Reason {
		NETHER_PORTAL,
		END_PORTAL,
		OTHER;
	}
}
