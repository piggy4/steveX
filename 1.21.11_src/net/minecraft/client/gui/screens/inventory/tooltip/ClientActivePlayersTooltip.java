package net.minecraft.client.gui.screens.inventory.tooltip;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

@Environment(EnvType.CLIENT)
public class ClientActivePlayersTooltip implements ClientTooltipComponent {
	private static final int SKIN_SIZE = 10;
	private static final int PADDING = 2;
	private final List<PlayerSkinRenderCache.RenderInfo> activePlayers;

	public ClientActivePlayersTooltip(ClientActivePlayersTooltip.ActivePlayersTooltip activePlayersTooltip) {
		this.activePlayers = activePlayersTooltip.profiles();
	}

	@Override
	public int getHeight(Font font) {
		return this.activePlayers.size() * 12 + 2;
	}

	private static String getName(PlayerSkinRenderCache.RenderInfo renderInfo) {
		return renderInfo.gameProfile().name();
	}

	@Override
	public int getWidth(Font font) {
		int i = 0;

		for (PlayerSkinRenderCache.RenderInfo renderInfo : this.activePlayers) {
			int j = font.width(getName(renderInfo));
			if (j > i) {
				i = j;
			}
		}

		return i + 10 + 6;
	}

	@Override
	public void renderImage(Font font, int i, int j, int k, int l, GuiGraphics guiGraphics) {
		for (int m = 0; m < this.activePlayers.size(); m++) {
			PlayerSkinRenderCache.RenderInfo renderInfo = this.activePlayers.get(m);
			int n = j + 2 + m * 12;
			PlayerFaceRenderer.draw(guiGraphics, renderInfo.playerSkin(), i + 2, n, 10);
			guiGraphics.drawString(font, getName(renderInfo), i + 10 + 4, n + 2, -1);
		}
	}

	@Environment(EnvType.CLIENT)
	public record ActivePlayersTooltip(List<PlayerSkinRenderCache.RenderInfo> profiles) implements TooltipComponent {
	}
}
