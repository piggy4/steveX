package net.minecraft.client.sounds;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public final class SoundPreviewHandler {
	private static @Nullable SoundInstance activePreview;
	private static @Nullable SoundSource previousCategory;

	public static void preview(SoundManager soundManager, SoundSource soundSource, float f) {
		stopOtherCategoryPreview(soundManager, soundSource);
		if (canPlaySound(soundManager)) {
			SoundEvent soundEvent = switch (soundSource) {
				case RECORDS -> (SoundEvent)SoundEvents.NOTE_BLOCK_GUITAR.value();
				case WEATHER -> SoundEvents.LIGHTNING_BOLT_THUNDER;
				case BLOCKS -> SoundEvents.GRASS_PLACE;
				case HOSTILE -> SoundEvents.ZOMBIE_AMBIENT;
				case NEUTRAL -> SoundEvents.COW_AMBIENT;
				case PLAYERS -> (SoundEvent)SoundEvents.GENERIC_EAT.value();
				case AMBIENT -> (SoundEvent)SoundEvents.AMBIENT_CAVE.value();
				case UI -> (SoundEvent)SoundEvents.UI_BUTTON_CLICK.value();
				default -> SoundEvents.EMPTY;
			};
			if (soundEvent != SoundEvents.EMPTY) {
				activePreview = SimpleSoundInstance.forUI(soundEvent, 1.0F, f);
				soundManager.play(activePreview);
			}
		}
	}

	private static void stopOtherCategoryPreview(SoundManager soundManager, SoundSource soundSource) {
		if (previousCategory != soundSource) {
			previousCategory = soundSource;
			if (activePreview != null) {
				soundManager.stop(activePreview);
			}
		}
	}

	private static boolean canPlaySound(SoundManager soundManager) {
		return activePreview == null || !soundManager.isActive(activePreview);
	}
}
