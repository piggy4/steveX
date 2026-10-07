package net.minecraft.client.gui.components.toasts;

import com.google.common.collect.Queues;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MusicToastDisplayState;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ToastManager {
	private static final int SLOT_COUNT = 5;
	private static final int ALL_SLOTS_OCCUPIED = -1;
	final Minecraft minecraft;
	private final List<ToastManager.ToastInstance<?>> visibleToasts = new ArrayList<>();
	private final BitSet occupiedSlots = new BitSet(5);
	private final Deque<Toast> queued = Queues.newArrayDeque();
	private final Set<SoundEvent> playedToastSounds = new HashSet<>();
	private ToastManager.@Nullable ToastInstance<NowPlayingToast> nowPlayingToast;

	public ToastManager(Minecraft minecraft, Options options) {
		this.minecraft = minecraft;
		this.initializeMusicToast(options.musicToast().get());
	}

	public void update() {
		MutableBoolean mutableBoolean = new MutableBoolean(false);
		this.visibleToasts.removeIf(toastInstance -> {
			Toast.Visibility visibility = toastInstance.visibility;
			toastInstance.update();
			if (toastInstance.visibility != visibility && mutableBoolean.isFalse()) {
				mutableBoolean.setTrue();
				toastInstance.visibility.playSound(this.minecraft.getSoundManager());
			}

			if (toastInstance.hasFinishedRendering()) {
				this.occupiedSlots.clear(toastInstance.firstSlotIndex, toastInstance.firstSlotIndex + toastInstance.occupiedSlotCount);
				return true;
			} else {
				return false;
			}
		});
		if (!this.queued.isEmpty() && this.freeSlotCount() > 0) {
			this.queued.removeIf(toast -> {
				int i = toast.occcupiedSlotCount();
				int j = this.findFreeSlotsIndex(i);
				if (j == -1) {
					return false;
				}

				this.visibleToasts.add(new ToastManager.ToastInstance<>(toast, j, i));
				this.occupiedSlots.set(j, j + i);
				SoundEvent soundEvent = toast.getSoundEvent();
				if (soundEvent != null && this.playedToastSounds.add(soundEvent)) {
					this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(soundEvent, 1.0F, 1.0F));
				}

				return true;
			});
		}

		this.playedToastSounds.clear();
		if (this.nowPlayingToast != null) {
			this.nowPlayingToast.update();
		}
	}

	public void render(GuiGraphics guiGraphics) {
		if (!this.minecraft.options.hideGui) {
			int i = guiGraphics.guiWidth();
			if (!this.visibleToasts.isEmpty()) {
				guiGraphics.nextStratum();
			}

			for (ToastManager.ToastInstance<?> toastInstance : this.visibleToasts) {
				toastInstance.render(guiGraphics, i);
			}

			if (this.minecraft.options.musicToast().get().renderToast()
				&& this.nowPlayingToast != null
				&& (this.minecraft.screen == null || !(this.minecraft.screen instanceof PauseScreen))) {
				this.nowPlayingToast.render(guiGraphics, i);
			}
		}
	}

	private int findFreeSlotsIndex(int i) {
		if (this.freeSlotCount() >= i) {
			int j = 0;

			for (int k = 0; k < 5; k++) {
				if (this.occupiedSlots.get(k)) {
					j = 0;
				} else if (++j == i) {
					return k + 1 - j;
				}
			}
		}

		return -1;
	}

	private int freeSlotCount() {
		return 5 - this.occupiedSlots.cardinality();
	}

	public <T extends Toast> @Nullable T getToast(Class<? extends T> class_, Object object) {
		for (ToastManager.ToastInstance<?> toastInstance : this.visibleToasts) {
			if (class_.isAssignableFrom(toastInstance.getToast().getClass()) && toastInstance.getToast().getToken().equals(object)) {
				return (T)toastInstance.getToast();
			}
		}

		for (Toast toast : this.queued) {
			if (class_.isAssignableFrom(toast.getClass()) && toast.getToken().equals(object)) {
				return (T)toast;
			}
		}

		return null;
	}

	public void clear() {
		this.occupiedSlots.clear();
		this.visibleToasts.clear();
		this.queued.clear();
	}

	public void addToast(Toast toast) {
		this.queued.add(toast);
	}

	public void showNowPlayingToast() {
		if (this.nowPlayingToast != null) {
			this.nowPlayingToast.resetToast();
			this.nowPlayingToast.getToast().showToast(this.minecraft.options);
		}
	}

	public void hideNowPlayingToast() {
		if (this.nowPlayingToast != null) {
			this.nowPlayingToast.getToast().setWantedVisibility(Toast.Visibility.HIDE);
		}
	}

	public Minecraft getMinecraft() {
		return this.minecraft;
	}

	public double getNotificationDisplayTimeMultiplier() {
		return this.minecraft.options.notificationDisplayTime().get();
	}

	private void initializeMusicToast(MusicToastDisplayState musicToastDisplayState) {
		switch (musicToastDisplayState) {
			case PAUSE:
			case PAUSE_AND_TOAST:
				this.nowPlayingToast = new ToastManager.ToastInstance<>(new NowPlayingToast(), 0, 0);
		}
	}

	public void setMusicToastDisplayState(MusicToastDisplayState musicToastDisplayState) {
		switch (musicToastDisplayState) {
			case PAUSE:
				this.nowPlayingToast = new ToastManager.ToastInstance<>(new NowPlayingToast(), 0, 0);
				break;
			case PAUSE_AND_TOAST:
				this.nowPlayingToast = new ToastManager.ToastInstance<>(new NowPlayingToast(), 0, 0);
				if (this.minecraft.options.getFinalSoundSourceVolume(SoundSource.MUSIC) > 0.0F) {
					this.nowPlayingToast.getToast().showToast(this.minecraft.options);
				}
				break;
			case NEVER:
				this.nowPlayingToast = null;
		}
	}

	@Environment(EnvType.CLIENT)
	class ToastInstance<T extends Toast> {
		private static final long SLIDE_ANIMATION_DURATION_MS = 600L;
		private final T toast;
		final int firstSlotIndex;
		final int occupiedSlotCount;
		private long animationStartTime;
		private long becameFullyVisibleAt;
		Toast.Visibility visibility;
		private long fullyVisibleFor;
		private float visiblePortion;
		protected boolean hasFinishedRendering;

		ToastInstance(final T toast, final int i, final int j) {
			this.toast = toast;
			this.firstSlotIndex = i;
			this.occupiedSlotCount = j;
			this.resetToast();
		}

		public T getToast() {
			return this.toast;
		}

		public void resetToast() {
			this.animationStartTime = -1L;
			this.becameFullyVisibleAt = -1L;
			this.visibility = Toast.Visibility.HIDE;
			this.fullyVisibleFor = 0L;
			this.visiblePortion = 0.0F;
			this.hasFinishedRendering = false;
		}

		public boolean hasFinishedRendering() {
			return this.hasFinishedRendering;
		}

		private void calculateVisiblePortion(long l) {
			float f = Mth.clamp((float)(l - this.animationStartTime) / 600.0F, 0.0F, 1.0F);
			f *= f;
			if (this.visibility == Toast.Visibility.HIDE) {
				this.visiblePortion = 1.0F - f;
			} else {
				this.visiblePortion = f;
			}
		}

		public void update() {
			long l = Util.getMillis();
			if (this.animationStartTime == -1L) {
				this.animationStartTime = l;
				this.visibility = Toast.Visibility.SHOW;
			}

			if (this.visibility == Toast.Visibility.SHOW && l - this.animationStartTime <= 600L) {
				this.becameFullyVisibleAt = l;
			}

			this.fullyVisibleFor = l - this.becameFullyVisibleAt;
			this.calculateVisiblePortion(l);
			this.toast.update(ToastManager.this, this.fullyVisibleFor);
			Toast.Visibility visibility = this.toast.getWantedVisibility();
			if (visibility != this.visibility) {
				this.animationStartTime = l - (int)((1.0F - this.visiblePortion) * 600.0F);
				this.visibility = visibility;
			}

			boolean bl = this.hasFinishedRendering;
			this.hasFinishedRendering = this.visibility == Toast.Visibility.HIDE && l - this.animationStartTime > 600L;
			if (this.hasFinishedRendering && !bl) {
				this.toast.onFinishedRendering();
			}
		}

		public void render(GuiGraphics guiGraphics, int i) {
			if (!this.hasFinishedRendering) {
				guiGraphics.pose().pushMatrix();
				guiGraphics.pose().translate(this.toast.xPos(i, this.visiblePortion), this.toast.yPos(this.firstSlotIndex));
				this.toast.render(guiGraphics, ToastManager.this.minecraft.font, this.fullyVisibleFor);
				guiGraphics.pose().popMatrix();
			}
		}
	}
}
