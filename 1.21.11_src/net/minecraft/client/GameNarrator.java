package net.minecraft.client;

import com.mojang.logging.LogUtils;
import com.mojang.text2speech.Narrator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.main.SilentInitException;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class GameNarrator {
	public static final Component NO_TITLE = CommonComponents.EMPTY;
	private static final Logger LOGGER = LogUtils.getLogger();
	private final Minecraft minecraft;
	private final Narrator narrator = Narrator.getNarrator();

	public GameNarrator(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	public void sayChatQueued(Component component) {
		if (this.getStatus().shouldNarrateChat()) {
			this.narrateNotInterruptingMessage(component);
		}
	}

	public void saySystemChatQueued(Component component) {
		if (this.getStatus().shouldNarrateSystemOrChat()) {
			this.narrateNotInterruptingMessage(component);
		}
	}

	public void saySystemQueued(Component component) {
		if (this.getStatus().shouldNarrateSystem()) {
			this.narrateNotInterruptingMessage(component);
		}
	}

	private void narrateNotInterruptingMessage(Component component) {
		String string = component.getString();
		if (!string.isEmpty()) {
			this.logNarratedMessage(string);
			this.narrateMessage(string, false);
		}
	}

	public void saySystemNow(Component component) {
		this.saySystemNow(component.getString());
	}

	public void saySystemNow(String string) {
		if (this.getStatus().shouldNarrateSystem() && !string.isEmpty()) {
			this.logNarratedMessage(string);
			if (this.narrator.active()) {
				this.narrator.clear();
				this.narrateMessage(string, true);
			}
		}
	}

	private void narrateMessage(String string, boolean bl) {
		this.narrator.say(string, bl, this.minecraft.options.getFinalSoundSourceVolume(SoundSource.VOICE));
	}

	private NarratorStatus getStatus() {
		return this.minecraft.options.narrator().get();
	}

	private void logNarratedMessage(String string) {
		if (SharedConstants.IS_RUNNING_IN_IDE) {
			LOGGER.debug("Narrating: {}", string.replaceAll("\n", "\\\\n"));
		}
	}

	public void updateNarratorStatus(NarratorStatus narratorStatus) {
		this.clear();
		this.narrateMessage(Component.translatable("options.narrator").append(" : ").append(narratorStatus.getName()).getString(), true);
		ToastManager toastManager = Minecraft.getInstance().getToastManager();
		if (this.narrator.active()) {
			if (narratorStatus == NarratorStatus.OFF) {
				SystemToast.addOrUpdate(toastManager, SystemToast.SystemToastId.NARRATOR_TOGGLE, Component.translatable("narrator.toast.disabled"), null);
			} else {
				SystemToast.addOrUpdate(toastManager, SystemToast.SystemToastId.NARRATOR_TOGGLE, Component.translatable("narrator.toast.enabled"), narratorStatus.getName());
			}
		} else {
			SystemToast.addOrUpdate(
				toastManager,
				SystemToast.SystemToastId.NARRATOR_TOGGLE,
				Component.translatable("narrator.toast.disabled"),
				Component.translatable("options.narrator.notavailable")
			);
		}
	}

	public boolean isActive() {
		return this.narrator.active();
	}

	public void clear() {
		if (this.getStatus() != NarratorStatus.OFF && this.narrator.active()) {
			this.narrator.clear();
		}
	}

	public void destroy() {
		this.narrator.destroy();
	}

	public void checkStatus(boolean bl) {
		if (bl
			&& !this.isActive()
			&& !TinyFileDialogs.tinyfd_messageBox(
				"Minecraft",
				"Failed to initialize text-to-speech library. Do you want to continue?\nIf this problem persists, please report it at bugs.mojang.com",
				"yesno",
				"error",
				true
			)) {
			throw new GameNarrator.NarratorInitException("Narrator library is not active");
		}
	}

	@Environment(EnvType.CLIENT)
	public static class NarratorInitException extends SilentInitException {
		public NarratorInitException(String string) {
			super(string);
		}
	}
}
