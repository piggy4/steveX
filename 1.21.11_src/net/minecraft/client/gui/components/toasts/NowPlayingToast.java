package net.minecraft.client.gui.components.toasts;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.color.ColorLerper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class NowPlayingToast implements Toast {
	private static final Identifier NOW_PLAYING_BACKGROUND_SPRITE = Identifier.withDefaultNamespace("toast/now_playing");
	private static final Identifier MUSIC_NOTES_SPRITE = Identifier.parse("icon/music_notes");
	private static final int PADDING = 7;
	private static final int MUSIC_NOTES_SIZE = 16;
	private static final int HEIGHT = 30;
	private static final int MUSIC_NOTES_SPACE = 30;
	private static final int VISIBILITY_DURATION = 5000;
	private static final int TEXT_COLOR = DyeColor.LIGHT_GRAY.getTextColor();
	private static final long MUSIC_COLOR_CHANGE_FREQUENCY_MS = 25L;
	private static int musicNoteColorTick;
	private static long lastMusicNoteColorChange;
	private static int musicNoteColor = -1;
	private boolean updateToast;
	private double notificationDisplayTimeMultiplier;
	private final Minecraft minecraft;
	private Toast.Visibility wantedVisibility = Toast.Visibility.HIDE;

	public NowPlayingToast() {
		this.minecraft = Minecraft.getInstance();
	}

	public static void renderToast(GuiGraphics guiGraphics, Font font) {
		String string = getCurrentSongName();
		if (string != null) {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, NOW_PLAYING_BACKGROUND_SPRITE, 0, 0, getWidth(string, font), 30);
			int i = 7;
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, MUSIC_NOTES_SPRITE, 7, 7, 16, 16, musicNoteColor);
			guiGraphics.drawString(font, getNowPlayingString(string), 30, 15 - 9 / 2, TEXT_COLOR);
		}
	}

	private static @Nullable String getCurrentSongName() {
		return Minecraft.getInstance().getMusicManager().getCurrentMusicTranslationKey();
	}

	public static void tickMusicNotes() {
		if (getCurrentSongName() != null) {
			long l = System.currentTimeMillis();
			if (l > lastMusicNoteColorChange + 25L) {
				musicNoteColorTick++;
				lastMusicNoteColorChange = l;
				musicNoteColor = ColorLerper.getLerpedColor(ColorLerper.Type.MUSIC_NOTE, musicNoteColorTick);
			}
		}
	}

	private static Component getNowPlayingString(@Nullable String string) {
		return string == null ? Component.empty() : Component.translatable(string.replace("/", "."));
	}

	public void showToast(Options options) {
		this.updateToast = true;
		this.notificationDisplayTimeMultiplier = options.notificationDisplayTime().get();
		this.setWantedVisibility(Toast.Visibility.SHOW);
	}

	@Override
	public void update(ToastManager toastManager, long l) {
		if (this.updateToast) {
			this.wantedVisibility = l < 5000.0 * this.notificationDisplayTimeMultiplier ? Toast.Visibility.SHOW : Toast.Visibility.HIDE;
			tickMusicNotes();
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, Font font, long l) {
		renderToast(guiGraphics, font);
	}

	@Override
	public void onFinishedRendering() {
		this.updateToast = false;
	}

	@Override
	public int width() {
		return getWidth(getCurrentSongName(), this.minecraft.font);
	}

	private static int getWidth(@Nullable String string, Font font) {
		return 30 + font.width(getNowPlayingString(string)) + 7;
	}

	@Override
	public int height() {
		return 30;
	}

	@Override
	public float xPos(int i, float f) {
		return this.width() * f - this.width();
	}

	@Override
	public float yPos(int i) {
		return 0.0F;
	}

	@Override
	public Toast.Visibility getWantedVisibility() {
		return this.wantedVisibility;
	}

	public void setWantedVisibility(Toast.Visibility visibility) {
		this.wantedVisibility = visibility;
	}
}
