package net.minecraft.client.gui.components;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

@Environment(EnvType.CLIENT)
public class MultiLineEditBox extends AbstractTextAreaWidget {
	private static final int CURSOR_INSERT_WIDTH = 1;
	private static final int CURSOR_COLOR = -3092272;
	private static final String CURSOR_APPEND_CHARACTER = "_";
	private static final int PLACEHOLDER_TEXT_COLOR = ARGB.color(204, -2039584);
	private static final int CURSOR_BLINK_INTERVAL_MS = 300;
	private final Font font;
	private final Component placeholder;
	private final MultilineTextField textField;
	private final int textColor;
	private final boolean textShadow;
	private final int cursorColor;
	private long focusedTime = Util.getMillis();

	MultiLineEditBox(Font font, int i, int j, int k, int l, Component component, Component component2, int m, boolean bl, int n, boolean bl2, boolean bl3) {
		super(i, j, k, l, component2, bl2, bl3);
		this.font = font;
		this.textShadow = bl;
		this.textColor = m;
		this.cursorColor = n;
		this.placeholder = component;
		this.textField = new MultilineTextField(font, k - this.totalInnerPadding());
		this.textField.setCursorListener(this::scrollToCursor);
	}

	public void setCharacterLimit(int i) {
		this.textField.setCharacterLimit(i);
	}

	public void setLineLimit(int i) {
		this.textField.setLineLimit(i);
	}

	public void setValueListener(Consumer<String> consumer) {
		this.textField.setValueListener(consumer);
	}

	public void setValue(String string) {
		this.setValue(string, false);
	}

	public void setValue(String string, boolean bl) {
		this.textField.setValue(string, bl);
	}

	public String getValue() {
		return this.textField.value();
	}

	@Override
	public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
		narrationElementOutput.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.editBox", this.getMessage(), this.getValue()));
	}

	@Override
	public void onClick(MouseButtonEvent mouseButtonEvent, boolean bl) {
		if (bl) {
			this.textField.selectWordAtCursor();
		} else {
			this.textField.setSelecting(mouseButtonEvent.hasShiftDown());
			this.seekCursorScreen(mouseButtonEvent.x(), mouseButtonEvent.y());
		}
	}

	@Override
	protected void onDrag(MouseButtonEvent mouseButtonEvent, double d, double e) {
		this.textField.setSelecting(true);
		this.seekCursorScreen(mouseButtonEvent.x(), mouseButtonEvent.y());
		this.textField.setSelecting(mouseButtonEvent.hasShiftDown());
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		return this.textField.keyPressed(keyEvent);
	}

	@Override
	public boolean charTyped(CharacterEvent characterEvent) {
		if (this.visible && this.isFocused() && characterEvent.isAllowedChatCharacter()) {
			this.textField.insertText(characterEvent.codepointAsString());
			return true;
		} else {
			return false;
		}
	}

	@Override
	protected void renderContents(GuiGraphics guiGraphics, int i, int j, float f) {
		String string = this.textField.value();
		if (string.isEmpty() && !this.isFocused()) {
			guiGraphics.drawWordWrap(this.font, this.placeholder, this.getInnerLeft(), this.getInnerTop(), this.width - this.totalInnerPadding(), PLACEHOLDER_TEXT_COLOR);
		} else {
			int k = this.textField.cursor();
			boolean bl = this.isFocused() && (Util.getMillis() - this.focusedTime) / 300L % 2L == 0L;
			boolean bl2 = k < string.length();
			int l = 0;
			int m = 0;
			int n = this.getInnerTop();
			boolean bl3 = false;

			for (MultilineTextField.StringView stringView : this.textField.iterateLines()) {
				boolean bl4 = this.withinContentAreaTopBottom(n, n + 9);
				int o = this.getInnerLeft();
				if (bl && bl2 && k >= stringView.beginIndex() && k <= stringView.endIndex()) {
					if (bl4) {
						String string2 = string.substring(stringView.beginIndex(), k);
						guiGraphics.drawString(this.font, string2, o, n, this.textColor, this.textShadow);
						l = o + this.font.width(string2);
						if (!bl3) {
							guiGraphics.fill(l, n - 1, l + 1, n + 1 + 9, this.cursorColor);
							bl3 = true;
						}

						guiGraphics.drawString(this.font, string.substring(k, stringView.endIndex()), l, n, this.textColor, this.textShadow);
					}
				} else {
					if (bl4) {
						String string2 = string.substring(stringView.beginIndex(), stringView.endIndex());
						guiGraphics.drawString(this.font, string2, o, n, this.textColor, this.textShadow);
						l = o + this.font.width(string2) - 1;
					}

					m = n;
				}

				n += 9;
			}

			if (bl && !bl2 && this.withinContentAreaTopBottom(m, m + 9)) {
				guiGraphics.drawString(this.font, "_", l + 1, m, this.cursorColor, this.textShadow);
			}

			if (this.textField.hasSelection()) {
				MultilineTextField.StringView stringView2 = this.textField.getSelected();
				int p = this.getInnerLeft();
				n = this.getInnerTop();

				for (MultilineTextField.StringView stringView3 : this.textField.iterateLines()) {
					if (stringView2.beginIndex() > stringView3.endIndex()) {
						n += 9;
					} else {
						if (stringView3.beginIndex() > stringView2.endIndex()) {
							break;
						}

						if (this.withinContentAreaTopBottom(n, n + 9)) {
							int q = this.font.width(string.substring(stringView3.beginIndex(), Math.max(stringView2.beginIndex(), stringView3.beginIndex())));
							int r;
							if (stringView2.endIndex() > stringView3.endIndex()) {
								r = this.width - this.innerPadding();
							} else {
								r = this.font.width(string.substring(stringView3.beginIndex(), stringView2.endIndex()));
							}

							guiGraphics.textHighlight(p + q, n, p + r, n + 9, true);
						}

						n += 9;
					}
				}
			}

			if (this.isHovered()) {
				guiGraphics.requestCursor(CursorTypes.IBEAM);
			}
		}
	}

	@Override
	protected void renderDecorations(GuiGraphics guiGraphics) {
		super.renderDecorations(guiGraphics);
		if (this.textField.hasCharacterLimit()) {
			int i = this.textField.characterLimit();
			Component component = Component.translatable("gui.multiLineEditBox.character_limit", this.textField.value().length(), i);
			guiGraphics.drawString(this.font, component, this.getX() + this.width - this.font.width(component), this.getY() + this.height + 4, -6250336);
		}
	}

	@Override
	public int getInnerHeight() {
		return 9 * this.textField.getLineCount();
	}

	@Override
	protected double scrollRate() {
		return 9.0 / 2.0;
	}

	private void scrollToCursor() {
		double d = this.scrollAmount();
		MultilineTextField.StringView stringView = this.textField.getLineView((int)(d / 9.0));
		if (this.textField.cursor() <= stringView.beginIndex()) {
			d = this.textField.getLineAtCursor() * 9;
		} else {
			MultilineTextField.StringView stringView2 = this.textField.getLineView((int)((d + this.height) / 9.0) - 1);
			if (this.textField.cursor() > stringView2.endIndex()) {
				d = this.textField.getLineAtCursor() * 9 - this.height + 9 + this.totalInnerPadding();
			}
		}

		this.setScrollAmount(d);
	}

	private void seekCursorScreen(double d, double e) {
		double f = d - this.getX() - this.innerPadding();
		double g = e - this.getY() - this.innerPadding() + this.scrollAmount();
		this.textField.seekCursorToPoint(f, g);
	}

	@Override
	public void setFocused(boolean bl) {
		super.setFocused(bl);
		if (bl) {
			this.focusedTime = Util.getMillis();
		}
	}

	public static MultiLineEditBox.Builder builder() {
		return new MultiLineEditBox.Builder();
	}

	@Environment(EnvType.CLIENT)
	public static class Builder {
		private int x;
		private int y;
		private Component placeholder = CommonComponents.EMPTY;
		private int textColor = -2039584;
		private boolean textShadow = true;
		private int cursorColor = -3092272;
		private boolean showBackground = true;
		private boolean showDecorations = true;

		public MultiLineEditBox.Builder setX(int i) {
			this.x = i;
			return this;
		}

		public MultiLineEditBox.Builder setY(int i) {
			this.y = i;
			return this;
		}

		public MultiLineEditBox.Builder setPlaceholder(Component component) {
			this.placeholder = component;
			return this;
		}

		public MultiLineEditBox.Builder setTextColor(int i) {
			this.textColor = i;
			return this;
		}

		public MultiLineEditBox.Builder setTextShadow(boolean bl) {
			this.textShadow = bl;
			return this;
		}

		public MultiLineEditBox.Builder setCursorColor(int i) {
			this.cursorColor = i;
			return this;
		}

		public MultiLineEditBox.Builder setShowBackground(boolean bl) {
			this.showBackground = bl;
			return this;
		}

		public MultiLineEditBox.Builder setShowDecorations(boolean bl) {
			this.showDecorations = bl;
			return this;
		}

		public MultiLineEditBox build(Font font, int i, int j, Component component) {
			return new MultiLineEditBox(
				font, this.x, this.y, i, j, this.placeholder, component, this.textColor, this.textShadow, this.cursorColor, this.showBackground, this.showDecorations
			);
		}
	}
}
