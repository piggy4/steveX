package net.minecraft.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public class PanoramaRenderer {
	public static final Identifier PANORAMA_OVERLAY = Identifier.withDefaultNamespace("textures/gui/title/background/panorama_overlay.png");
	private final Minecraft minecraft;
	private final CubeMap cubeMap;
	private float spin;

	public PanoramaRenderer(CubeMap cubeMap) {
		this.cubeMap = cubeMap;
		this.minecraft = Minecraft.getInstance();
	}

	public void render(GuiGraphics guiGraphics, int i, int j, boolean bl) {
		if (bl) {
			float f = this.minecraft.getDeltaTracker().getRealtimeDeltaTicks();
			float g = (float)(f * this.minecraft.options.panoramaSpeed().get());
			this.spin = wrap(this.spin + g * 0.1F, 360.0F);
		}

		this.cubeMap.render(this.minecraft, 10.0F, -this.spin);
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, PANORAMA_OVERLAY, 0, 0, 0.0F, 0.0F, i, j, 16, 128, 16, 128);
	}

	private static float wrap(float f, float g) {
		return f > g ? f - g : f;
	}

	public void registerTextures(TextureManager textureManager) {
		this.cubeMap.registerTextures(textureManager);
	}
}
