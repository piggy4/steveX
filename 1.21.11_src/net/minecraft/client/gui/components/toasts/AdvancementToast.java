package net.minecraft.client.gui.components.toasts;

import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class AdvancementToast implements Toast {
	private static final Identifier BACKGROUND_SPRITE = Identifier.withDefaultNamespace("toast/advancement");
	public static final int DISPLAY_TIME = 5000;
	private final AdvancementHolder advancement;
	private Toast.Visibility wantedVisibility = Toast.Visibility.HIDE;

	public AdvancementToast(AdvancementHolder advancementHolder) {
		this.advancement = advancementHolder;
	}

	@Override
	public Toast.Visibility getWantedVisibility() {
		return this.wantedVisibility;
	}

	@Override
	public void update(ToastManager toastManager, long l) {
		DisplayInfo displayInfo = this.advancement.value().display().orElse(null);
		if (displayInfo == null) {
			this.wantedVisibility = Toast.Visibility.HIDE;
		} else {
			this.wantedVisibility = l >= 5000.0 * toastManager.getNotificationDisplayTimeMultiplier() ? Toast.Visibility.HIDE : Toast.Visibility.SHOW;
		}
	}

	@Override
	public @Nullable SoundEvent getSoundEvent() {
		return this.isChallengeAdvancement() ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : null;
	}

	private boolean isChallengeAdvancement() {
		Optional<DisplayInfo> optional = this.advancement.value().display();
		return optional.isPresent() && optional.get().getType().equals(AdvancementType.CHALLENGE);
	}

	@Override
	public void render(GuiGraphics guiGraphics, Font font, long l) {
		DisplayInfo displayInfo = this.advancement.value().display().orElse(null);
		guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_SPRITE, 0, 0, this.width(), this.height());
		if (displayInfo != null) {
			List<FormattedCharSequence> list = font.split(displayInfo.getTitle(), 125);
			int i = displayInfo.getType() == AdvancementType.CHALLENGE ? -30465 : -256;
			if (list.size() == 1) {
				guiGraphics.drawString(font, displayInfo.getType().getDisplayName(), 30, 7, i, false);
				guiGraphics.drawString(font, list.get(0), 30, 18, -1, false);
			} else {
				int j = 1500;
				float f = 300.0F;
				if (l < 1500L) {
					int k = Mth.floor(Mth.clamp((float)(1500L - l) / 300.0F, 0.0F, 1.0F) * 255.0F);
					guiGraphics.drawString(font, displayInfo.getType().getDisplayName(), 30, 11, ARGB.color(k, i), false);
				} else {
					int k = Mth.floor(Mth.clamp((float)(l - 1500L) / 300.0F, 0.0F, 1.0F) * 252.0F);
					int m = this.height() / 2 - list.size() * 9 / 2;

					for (FormattedCharSequence formattedCharSequence : list) {
						guiGraphics.drawString(font, formattedCharSequence, 30, m, ARGB.white(k), false);
						m += 9;
					}
				}
			}

			guiGraphics.renderFakeItem(displayInfo.getIcon(), 8, 8);
		}
	}
}
