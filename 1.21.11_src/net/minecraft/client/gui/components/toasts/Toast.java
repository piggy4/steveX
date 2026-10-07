package net.minecraft.client.gui.components.toasts;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface Toast {
	Object NO_TOKEN = new Object();
	int DEFAULT_WIDTH = 160;
	int SLOT_HEIGHT = 32;

	Toast.Visibility getWantedVisibility();

	void update(ToastManager toastManager, long l);

	default @Nullable SoundEvent getSoundEvent() {
		return null;
	}

	void render(GuiGraphics guiGraphics, Font font, long l);

	default Object getToken() {
		return NO_TOKEN;
	}

	default float xPos(int i, float f) {
		return i - this.width() * f;
	}

	default float yPos(int i) {
		return i * this.height();
	}

	default int width() {
		return 160;
	}

	default int height() {
		return 32;
	}

	default int occcupiedSlotCount() {
		return Mth.positiveCeilDiv(this.height(), 32);
	}

	default void onFinishedRendering() {
	}

	@Environment(EnvType.CLIENT)
	enum Visibility {
		SHOW(SoundEvents.UI_TOAST_IN),
		HIDE(SoundEvents.UI_TOAST_OUT);

		private final SoundEvent soundEvent;

		Visibility(final SoundEvent soundEvent) {
			this.soundEvent = soundEvent;
		}

		public void playSound(SoundManager soundManager) {
			soundManager.play(SimpleSoundInstance.forUI(this.soundEvent, 1.0F, 1.0F));
		}
	}
}
