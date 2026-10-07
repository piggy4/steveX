package net.minecraft.client.gui.screens.inventory;

import com.google.common.collect.Ordering;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;

@Environment(EnvType.CLIENT)
public class EffectsInInventory {
	private static final Identifier EFFECT_BACKGROUND_SPRITE = Identifier.withDefaultNamespace("container/inventory/effect_background");
	private static final Identifier EFFECT_BACKGROUND_AMBIENT_SPRITE = Identifier.withDefaultNamespace("container/inventory/effect_background_ambient");
	private static final int ICON_SIZE = 18;
	public static final int SPACING = 7;
	private static final int TEXT_X_OFFSET = 32;
	public static final int SPRITE_SQUARE_SIZE = 32;
	private final AbstractContainerScreen<?> screen;
	private final Minecraft minecraft;

	public EffectsInInventory(AbstractContainerScreen<?> abstractContainerScreen) {
		this.screen = abstractContainerScreen;
		this.minecraft = Minecraft.getInstance();
	}

	public boolean canSeeEffects() {
		int i = this.screen.leftPos + this.screen.imageWidth + 2;
		int j = this.screen.width - i;
		return j >= 32;
	}

	public void render(GuiGraphics guiGraphics, int i, int j) {
		int k = this.screen.leftPos + this.screen.imageWidth + 2;
		int l = this.screen.width - k;
		Collection<MobEffectInstance> collection = this.minecraft.player.getActiveEffects();
		if (!collection.isEmpty() && l >= 32) {
			int m = l >= 120 ? l - 7 : 32;
			int n = 33;
			if (collection.size() > 5) {
				n = 132 / (collection.size() - 1);
			}

			this.renderEffects(guiGraphics, collection, k, n, i, j, m);
		}
	}

	private void renderEffects(GuiGraphics guiGraphics, Collection<MobEffectInstance> collection, int i, int j, int k, int l, int m) {
		Iterable<MobEffectInstance> iterable = Ordering.natural().sortedCopy(collection);
		int n = this.screen.topPos;
		Font font = this.screen.getFont();

		for (MobEffectInstance mobEffectInstance : iterable) {
			boolean bl = mobEffectInstance.isAmbient();
			Component component = this.getEffectName(mobEffectInstance);
			Component component2 = MobEffectUtil.formatDuration(mobEffectInstance, 1.0F, this.minecraft.level.tickRateManager().tickrate());
			int o = this.renderBackground(guiGraphics, font, component, component2, i, n, bl, m);
			this.renderText(guiGraphics, component, component2, font, i, n, o, j, k, l);
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(mobEffectInstance.getEffect()), i + 7, n + 7, 18, 18);
			n += j;
		}
	}

	private int renderBackground(GuiGraphics guiGraphics, Font font, Component component, Component component2, int i, int j, boolean bl, int k) {
		int l = 32 + font.width(component) + 7;
		int m = 32 + font.width(component2) + 7;
		int n = Math.min(k, Math.max(l, m));
		guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, bl ? EFFECT_BACKGROUND_AMBIENT_SPRITE : EFFECT_BACKGROUND_SPRITE, i, j, n, 32);
		return n;
	}

	private void renderText(GuiGraphics guiGraphics, Component component, Component component2, Font font, int i, int j, int k, int l, int m, int n) {
		int o = i + 32;
		int p = j + 7;
		int q = k - 32 - 7;
		boolean bl2;
		if (q > 0) {
			boolean bl = font.width(component) > q;
			FormattedCharSequence formattedCharSequence = bl ? StringWidget.clipText(component, font, q) : component.getVisualOrderText();
			guiGraphics.drawString(font, formattedCharSequence, o, p, -1);
			guiGraphics.drawString(font, component2, o, p + 9, -8355712);
			bl2 = bl;
		} else {
			bl2 = true;
		}

		if (bl2 && m >= i && m <= i + k && n >= j && n <= j + l) {
			guiGraphics.setTooltipForNextFrame(this.screen.getFont(), List.of(component, component2), Optional.empty(), m, n);
		}
	}

	private Component getEffectName(MobEffectInstance mobEffectInstance) {
		MutableComponent mutableComponent = mobEffectInstance.getEffect().value().getDisplayName().copy();
		if (mobEffectInstance.getAmplifier() >= 1 && mobEffectInstance.getAmplifier() <= 9) {
			mutableComponent.append(CommonComponents.SPACE).append(Component.translatable("enchantment.level." + (mobEffectInstance.getAmplifier() + 1)));
		}

		return mutableComponent;
	}
}
