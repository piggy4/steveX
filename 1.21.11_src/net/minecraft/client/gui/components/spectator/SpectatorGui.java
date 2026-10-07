package net.minecraft.client.gui.components.spectator;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.spectator.SpectatorMenu;
import net.minecraft.client.gui.spectator.SpectatorMenuItem;
import net.minecraft.client.gui.spectator.SpectatorMenuListener;
import net.minecraft.client.gui.spectator.categories.SpectatorPage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class SpectatorGui implements SpectatorMenuListener {
	private static final Identifier HOTBAR_SPRITE = Identifier.withDefaultNamespace("hud/hotbar");
	private static final Identifier HOTBAR_SELECTION_SPRITE = Identifier.withDefaultNamespace("hud/hotbar_selection");
	private static final long FADE_OUT_DELAY = 5000L;
	private static final long FADE_OUT_TIME = 2000L;
	private final Minecraft minecraft;
	private long lastSelectionTime;
	private @Nullable SpectatorMenu menu;

	public SpectatorGui(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	public void onHotbarSelected(int i) {
		this.lastSelectionTime = Util.getMillis();
		if (this.menu != null) {
			this.menu.selectSlot(i);
		} else {
			this.menu = new SpectatorMenu(this);
		}
	}

	private float getHotbarAlpha() {
		long l = this.lastSelectionTime - Util.getMillis() + 5000L;
		return Mth.clamp((float)l / 2000.0F, 0.0F, 1.0F);
	}

	public void renderHotbar(GuiGraphics guiGraphics) {
		if (this.menu != null) {
			float f = this.getHotbarAlpha();
			if (f <= 0.0F) {
				this.menu.exit();
			} else {
				int i = guiGraphics.guiWidth() / 2;
				int j = Mth.floor(guiGraphics.guiHeight() - 22.0F * f);
				SpectatorPage spectatorPage = this.menu.getCurrentPage();
				this.renderPage(guiGraphics, f, i, j, spectatorPage);
			}
		}
	}

	protected void renderPage(GuiGraphics guiGraphics, float f, int i, int j, SpectatorPage spectatorPage) {
		int k = ARGB.white(f);
		guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, i - 91, j, 182, 22, k);
		if (spectatorPage.getSelectedSlot() >= 0) {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SELECTION_SPRITE, i - 91 - 1 + spectatorPage.getSelectedSlot() * 20, j - 1, 24, 23, k);
		}

		for (int l = 0; l < 9; l++) {
			this.renderSlot(guiGraphics, l, guiGraphics.guiWidth() / 2 - 90 + l * 20 + 2, j + 3, f, spectatorPage.getItem(l));
		}
	}

	private void renderSlot(GuiGraphics guiGraphics, int i, int j, float f, float g, SpectatorMenuItem spectatorMenuItem) {
		if (spectatorMenuItem != SpectatorMenu.EMPTY_SLOT) {
			guiGraphics.pose().pushMatrix();
			guiGraphics.pose().translate(j, f);
			float h = spectatorMenuItem.isEnabled() ? 1.0F : 0.25F;
			spectatorMenuItem.renderIcon(guiGraphics, h, g);
			guiGraphics.pose().popMatrix();
			if (g > 0.0F && spectatorMenuItem.isEnabled()) {
				Component component = this.minecraft.options.keyHotbarSlots[i].getTranslatedKeyMessage();
				guiGraphics.drawString(this.minecraft.font, component, j + 19 - 2 - this.minecraft.font.width(component), (int)f + 6 + 3, ARGB.white(g));
			}
		}
	}

	public void renderAction(GuiGraphics guiGraphics) {
		float f = this.getHotbarAlpha();
		if (f > 0.0F && this.menu != null) {
			SpectatorMenuItem spectatorMenuItem = this.menu.getSelectedItem();
			Component component = spectatorMenuItem == SpectatorMenu.EMPTY_SLOT ? this.menu.getSelectedCategory().getPrompt() : spectatorMenuItem.getName();
			int i = this.minecraft.font.width(component);
			int j = (guiGraphics.guiWidth() - i) / 2;
			int k = guiGraphics.guiHeight() - 35;
			guiGraphics.drawStringWithBackdrop(this.minecraft.font, component, j, k, i, ARGB.white(f));
		}
	}

	@Override
	public void onSpectatorMenuClosed(SpectatorMenu spectatorMenu) {
		this.menu = null;
		this.lastSelectionTime = 0L;
	}

	public boolean isMenuActive() {
		return this.menu != null;
	}

	public void onMouseScrolled(int i) {
		int j = this.menu.getSelectedSlot() + i;

		while (j >= 0 && j <= 8 && (this.menu.getItem(j) == SpectatorMenu.EMPTY_SLOT || !this.menu.getItem(j).isEnabled())) {
			j += i;
		}

		if (j >= 0 && j <= 8) {
			this.menu.selectSlot(j);
			this.lastSelectionTime = Util.getMillis();
		}
	}

	public void onHotbarActionKeyPressed() {
		this.lastSelectionTime = Util.getMillis();
		if (this.isMenuActive()) {
			int i = this.menu.getSelectedSlot();
			if (i != -1) {
				this.menu.selectSlot(i);
			}
		} else {
			this.menu = new SpectatorMenu(this);
		}
	}
}
