package net.minecraft.client.gui.components.toasts;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class TutorialToast implements Toast {
	private static final Identifier BACKGROUND_SPRITE = Identifier.withDefaultNamespace("toast/tutorial");
	public static final int PROGRESS_BAR_WIDTH = 154;
	public static final int PROGRESS_BAR_HEIGHT = 1;
	public static final int PROGRESS_BAR_X = 3;
	public static final int PROGRESS_BAR_MARGIN_BOTTOM = 4;
	private static final int PADDING_TOP = 7;
	private static final int PADDING_BOTTOM = 3;
	private static final int LINE_SPACING = 11;
	private static final int TEXT_LEFT = 30;
	private static final int TEXT_WIDTH = 126;
	private final TutorialToast.Icons icon;
	private final List<FormattedCharSequence> lines;
	private Toast.Visibility visibility = Toast.Visibility.SHOW;
	private long lastSmoothingTime;
	private float smoothedProgress;
	private float progress;
	private final boolean progressable;
	private final int timeToDisplayMs;

	public TutorialToast(Font font, TutorialToast.Icons icons, Component component, @Nullable Component component2, boolean bl, int i) {
		this.icon = icons;
		this.lines = new ArrayList<>(2);
		this.lines.addAll(font.split(component.copy().withColor(-11534256), 126));
		if (component2 != null) {
			this.lines.addAll(font.split(component2, 126));
		}

		this.progressable = bl;
		this.timeToDisplayMs = i;
	}

	public TutorialToast(Font font, TutorialToast.Icons icons, Component component, @Nullable Component component2, boolean bl) {
		this(font, icons, component, component2, bl, 0);
	}

	@Override
	public Toast.Visibility getWantedVisibility() {
		return this.visibility;
	}

	@Override
	public void update(ToastManager toastManager, long l) {
		if (this.timeToDisplayMs > 0) {
			this.progress = Math.min((float)l / this.timeToDisplayMs, 1.0F);
			this.smoothedProgress = this.progress;
			this.lastSmoothingTime = l;
			if (l > this.timeToDisplayMs) {
				this.hide();
			}
		} else if (this.progressable) {
			this.smoothedProgress = Mth.clampedLerp((float)(l - this.lastSmoothingTime) / 100.0F, this.smoothedProgress, this.progress);
			this.lastSmoothingTime = l;
		}
	}

	@Override
	public int height() {
		return 7 + this.contentHeight() + 3;
	}

	private int contentHeight() {
		return Math.max(this.lines.size(), 2) * 11;
	}

	@Override
	public void render(GuiGraphics guiGraphics, Font font, long l) {
		int i = this.height();
		guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_SPRITE, 0, 0, this.width(), i);
		this.icon.render(guiGraphics, 6, 6);
		int j = this.lines.size() * 11;
		int k = 7 + (this.contentHeight() - j) / 2;

		for (int m = 0; m < this.lines.size(); m++) {
			guiGraphics.drawString(font, this.lines.get(m), 30, k + m * 11, -16777216, false);
		}

		if (this.progressable) {
			int m = i - 4;
			guiGraphics.fill(3, m, 157, m + 1, -1);
			int n;
			if (this.progress >= this.smoothedProgress) {
				n = -16755456;
			} else {
				n = -11206656;
			}

			guiGraphics.fill(3, m, (int)(3.0F + 154.0F * this.smoothedProgress), m + 1, n);
		}
	}

	public void hide() {
		this.visibility = Toast.Visibility.HIDE;
	}

	public void updateProgress(float f) {
		this.progress = f;
	}

	@Environment(EnvType.CLIENT)
	public enum Icons {
		MOVEMENT_KEYS(Identifier.withDefaultNamespace("toast/movement_keys")),
		MOUSE(Identifier.withDefaultNamespace("toast/mouse")),
		TREE(Identifier.withDefaultNamespace("toast/tree")),
		RECIPE_BOOK(Identifier.withDefaultNamespace("toast/recipe_book")),
		WOODEN_PLANKS(Identifier.withDefaultNamespace("toast/wooden_planks")),
		SOCIAL_INTERACTIONS(Identifier.withDefaultNamespace("toast/social_interactions")),
		RIGHT_CLICK(Identifier.withDefaultNamespace("toast/right_click"));

		private final Identifier sprite;

		Icons(final Identifier identifier) {
			this.sprite = identifier;
		}

		public void render(GuiGraphics guiGraphics, int i, int j) {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.sprite, i, j, 20, 20);
		}
	}
}
