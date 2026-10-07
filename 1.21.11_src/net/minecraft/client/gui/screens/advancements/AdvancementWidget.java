package net.minecraft.client.gui.screens.advancements;

import com.google.common.collect.Lists;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class AdvancementWidget {
	private static final Identifier TITLE_BOX_SPRITE = Identifier.withDefaultNamespace("advancements/title_box");
	private static final int HEIGHT = 26;
	private static final int BOX_X = 0;
	private static final int BOX_WIDTH = 200;
	private static final int FRAME_WIDTH = 26;
	private static final int ICON_X = 8;
	private static final int ICON_Y = 5;
	private static final int ICON_WIDTH = 26;
	private static final int TITLE_PADDING_LEFT = 3;
	private static final int TITLE_PADDING_RIGHT = 5;
	private static final int TITLE_X = 32;
	private static final int TITLE_PADDING_TOP = 9;
	private static final int TITLE_PADDING_BOTTOM = 8;
	private static final int TITLE_MAX_WIDTH = 163;
	private static final int TITLE_MIN_WIDTH = 80;
	private static final int[] TEST_SPLIT_OFFSETS = new int[]{0, 10, -10, 25, -25};
	private final AdvancementTab tab;
	private final AdvancementNode advancementNode;
	private final DisplayInfo display;
	private final List<FormattedCharSequence> titleLines;
	private final int width;
	private final List<FormattedCharSequence> description;
	private final Minecraft minecraft;
	private @Nullable AdvancementWidget parent;
	private final List<AdvancementWidget> children = Lists.newArrayList();
	private @Nullable AdvancementProgress progress;
	private final int x;
	private final int y;

	public AdvancementWidget(AdvancementTab advancementTab, Minecraft minecraft, AdvancementNode advancementNode, DisplayInfo displayInfo) {
		this.tab = advancementTab;
		this.advancementNode = advancementNode;
		this.display = displayInfo;
		this.minecraft = minecraft;
		this.titleLines = minecraft.font.split(displayInfo.getTitle(), 163);
		this.x = Mth.floor(displayInfo.getX() * 28.0F);
		this.y = Mth.floor(displayInfo.getY() * 27.0F);
		int i = Math.max(this.titleLines.stream().mapToInt(minecraft.font::width).max().orElse(0), 80);
		int j = this.getMaxProgressWidth();
		int k = 29 + i + j;
		this.description = Language.getInstance()
			.getVisualOrder(
				this.findOptimalLines(ComponentUtils.mergeStyles(displayInfo.getDescription(), Style.EMPTY.withColor(displayInfo.getType().getChatColor())), k)
			);

		for (FormattedCharSequence formattedCharSequence : this.description) {
			k = Math.max(k, minecraft.font.width(formattedCharSequence));
		}

		this.width = k + 3 + 5;
	}

	private int getMaxProgressWidth() {
		int i = this.advancementNode.advancement().requirements().size();
		if (i <= 1) {
			return 0;
		}

		int j = 8;
		Component component = Component.translatable("advancements.progress", i, i);
		return this.minecraft.font.width(component) + 8;
	}

	private static float getMaxWidth(StringSplitter stringSplitter, List<FormattedText> list) {
		return (float)list.stream().mapToDouble(stringSplitter::stringWidth).max().orElse(0.0);
	}

	private List<FormattedText> findOptimalLines(Component component, int i) {
		StringSplitter stringSplitter = this.minecraft.font.getSplitter();
		List<FormattedText> list = null;
		float f = Float.MAX_VALUE;

		for (int j : TEST_SPLIT_OFFSETS) {
			List<FormattedText> list2 = stringSplitter.splitLines(component, i - j, Style.EMPTY);
			float g = Math.abs(getMaxWidth(stringSplitter, list2) - i);
			if (g <= 10.0F) {
				return list2;
			}

			if (g < f) {
				f = g;
				list = list2;
			}
		}

		return list;
	}

	private @Nullable AdvancementWidget getFirstVisibleParent(AdvancementNode advancementNode) {
		do {
			advancementNode = advancementNode.parent();
		} while (advancementNode != null && advancementNode.advancement().display().isEmpty());

		return advancementNode != null && !advancementNode.advancement().display().isEmpty() ? this.tab.getWidget(advancementNode.holder()) : null;
	}

	public void drawConnectivity(GuiGraphics guiGraphics, int i, int j, boolean bl) {
		if (this.parent != null) {
			int k = i + this.parent.x + 13;
			int l = i + this.parent.x + 26 + 4;
			int m = j + this.parent.y + 13;
			int n = i + this.x + 13;
			int o = j + this.y + 13;
			int p = bl ? -16777216 : -1;
			if (bl) {
				guiGraphics.hLine(l, k, m - 1, p);
				guiGraphics.hLine(l + 1, k, m, p);
				guiGraphics.hLine(l, k, m + 1, p);
				guiGraphics.hLine(n, l - 1, o - 1, p);
				guiGraphics.hLine(n, l - 1, o, p);
				guiGraphics.hLine(n, l - 1, o + 1, p);
				guiGraphics.vLine(l - 1, o, m, p);
				guiGraphics.vLine(l + 1, o, m, p);
			} else {
				guiGraphics.hLine(l, k, m, p);
				guiGraphics.hLine(n, l, o, p);
				guiGraphics.vLine(l, o, m, p);
			}
		}

		for (AdvancementWidget advancementWidget : this.children) {
			advancementWidget.drawConnectivity(guiGraphics, i, j, bl);
		}
	}

	public void draw(GuiGraphics guiGraphics, int i, int j) {
		if (!this.display.isHidden() || this.progress != null && this.progress.isDone()) {
			float f = this.progress == null ? 0.0F : this.progress.getPercent();
			AdvancementWidgetType advancementWidgetType;
			if (f >= 1.0F) {
				advancementWidgetType = AdvancementWidgetType.OBTAINED;
			} else {
				advancementWidgetType = AdvancementWidgetType.UNOBTAINED;
			}

			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, advancementWidgetType.frameSprite(this.display.getType()), i + this.x + 3, j + this.y, 26, 26);
			guiGraphics.renderFakeItem(this.display.getIcon(), i + this.x + 8, j + this.y + 5);
		}

		for (AdvancementWidget advancementWidget : this.children) {
			advancementWidget.draw(guiGraphics, i, j);
		}
	}

	public int getWidth() {
		return this.width;
	}

	public void setProgress(AdvancementProgress advancementProgress) {
		this.progress = advancementProgress;
	}

	public void addChild(AdvancementWidget advancementWidget) {
		this.children.add(advancementWidget);
	}

	public void drawHover(GuiGraphics guiGraphics, int i, int j, float f, int k, int l) {
		Font font = this.minecraft.font;
		int m = 9 * this.titleLines.size() + 9 + 8;
		int n = j + this.y + (26 - m) / 2;
		int o = n + m;
		int p = this.description.size() * 9;
		int q = 6 + p;
		boolean bl = k + i + this.x + this.width + 26 >= this.tab.getScreen().width;
		Component component = this.progress == null ? null : this.progress.getProgressText();
		int r = component == null ? 0 : font.width(component);
		boolean bl2 = o + q >= 113;
		float g = this.progress == null ? 0.0F : this.progress.getPercent();
		int s = Mth.floor(g * this.width);
		AdvancementWidgetType advancementWidgetType;
		AdvancementWidgetType advancementWidgetType2;
		AdvancementWidgetType advancementWidgetType3;
		if (g >= 1.0F) {
			s = this.width / 2;
			advancementWidgetType = AdvancementWidgetType.OBTAINED;
			advancementWidgetType2 = AdvancementWidgetType.OBTAINED;
			advancementWidgetType3 = AdvancementWidgetType.OBTAINED;
		} else if (s < 2) {
			s = this.width / 2;
			advancementWidgetType = AdvancementWidgetType.UNOBTAINED;
			advancementWidgetType2 = AdvancementWidgetType.UNOBTAINED;
			advancementWidgetType3 = AdvancementWidgetType.UNOBTAINED;
		} else if (s > this.width - 2) {
			s = this.width / 2;
			advancementWidgetType = AdvancementWidgetType.OBTAINED;
			advancementWidgetType2 = AdvancementWidgetType.OBTAINED;
			advancementWidgetType3 = AdvancementWidgetType.UNOBTAINED;
		} else {
			advancementWidgetType = AdvancementWidgetType.OBTAINED;
			advancementWidgetType2 = AdvancementWidgetType.UNOBTAINED;
			advancementWidgetType3 = AdvancementWidgetType.UNOBTAINED;
		}

		int t = this.width - s;
		int u;
		if (bl) {
			u = i + this.x - this.width + 26 + 6;
		} else {
			u = i + this.x;
		}

		int v = m + q;
		if (!this.description.isEmpty()) {
			if (bl2) {
				guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, TITLE_BOX_SPRITE, u, o - v, this.width, v);
			} else {
				guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, TITLE_BOX_SPRITE, u, n, this.width, v);
			}
		}

		if (advancementWidgetType != advancementWidgetType2) {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, advancementWidgetType.boxSprite(), 200, m, 0, 0, u, n, s, m);
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, advancementWidgetType2.boxSprite(), 200, m, 200 - t, 0, u + s, n, t, m);
		} else {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, advancementWidgetType.boxSprite(), u, n, this.width, m);
		}

		guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, advancementWidgetType3.frameSprite(this.display.getType()), i + this.x + 3, j + this.y, 26, 26);
		int w = u + 5;
		if (bl) {
			this.drawMultilineText(guiGraphics, this.titleLines, w, n + 9, -1);
			if (component != null) {
				guiGraphics.drawString(font, component, i + this.x - r, n + 9, -1);
			}
		} else {
			this.drawMultilineText(guiGraphics, this.titleLines, i + this.x + 32, n + 9, -1);
			if (component != null) {
				guiGraphics.drawString(font, component, i + this.x + this.width - r - 5, n + 9, -1);
			}
		}

		if (bl2) {
			this.drawMultilineText(guiGraphics, this.description, w, n - p + 1, -16711936);
		} else {
			this.drawMultilineText(guiGraphics, this.description, w, o, -16711936);
		}

		guiGraphics.renderFakeItem(this.display.getIcon(), i + this.x + 8, j + this.y + 5);
	}

	private void drawMultilineText(GuiGraphics guiGraphics, List<FormattedCharSequence> list, int i, int j, int k) {
		Font font = this.minecraft.font;

		for (int l = 0; l < list.size(); l++) {
			guiGraphics.drawString(font, list.get(l), i, j + l * 9, k);
		}
	}

	public boolean isMouseOver(int i, int j, int k, int l) {
		if (!this.display.isHidden() || this.progress != null && this.progress.isDone()) {
			int m = i + this.x;
			int n = m + 26;
			int o = j + this.y;
			int p = o + 26;
			return k >= m && k <= n && l >= o && l <= p;
		} else {
			return false;
		}
	}

	public void attachToParent() {
		if (this.parent == null && this.advancementNode.parent() != null) {
			this.parent = this.getFirstVisibleParent(this.advancementNode);
			if (this.parent != null) {
				this.parent.addChild(this);
			}
		}
	}

	public int getY() {
		return this.y;
	}

	public int getX() {
		return this.x;
	}
}
