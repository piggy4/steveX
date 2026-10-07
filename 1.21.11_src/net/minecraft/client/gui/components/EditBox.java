package net.minecraft.client.gui.components;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class EditBox extends AbstractWidget {
	private static final WidgetSprites SPRITES = new WidgetSprites(
		Identifier.withDefaultNamespace("widget/text_field"), Identifier.withDefaultNamespace("widget/text_field_highlighted")
	);
	public static final int BACKWARDS = -1;
	public static final int FORWARDS = 1;
	private static final int CURSOR_INSERT_WIDTH = 1;
	private static final String CURSOR_APPEND_CHARACTER = "_";
	public static final int DEFAULT_TEXT_COLOR = -2039584;
	public static final Style DEFAULT_HINT_STYLE = Style.EMPTY.withColor(ChatFormatting.DARK_GRAY);
	public static final Style SEARCH_HINT_STYLE = Style.EMPTY.applyFormats(ChatFormatting.GRAY, ChatFormatting.ITALIC);
	private static final int CURSOR_BLINK_INTERVAL_MS = 300;
	private final Font font;
	private String value = "";
	private int maxLength = 32;
	private boolean bordered = true;
	private boolean canLoseFocus = true;
	private boolean isEditable = true;
	private boolean centered = false;
	private boolean textShadow = true;
	private boolean invertHighlightedTextColor = true;
	private int displayPos;
	private int cursorPos;
	private int highlightPos;
	private int textColor = -2039584;
	private int textColorUneditable = -9408400;
	private @Nullable String suggestion;
	private @Nullable Consumer<String> responder;
	private Predicate<String> filter = Objects::nonNull;
	private final List<EditBox.TextFormatter> formatters = new ArrayList<>();
	private @Nullable Component hint;
	private long focusedTime = Util.getMillis();
	private int textX;
	private int textY;

	public EditBox(Font font, int i, int j, Component component) {
		this(font, 0, 0, i, j, component);
	}

	public EditBox(Font font, int i, int j, int k, int l, Component component) {
		this(font, i, j, k, l, null, component);
	}

	public EditBox(Font font, int i, int j, int k, int l, @Nullable EditBox editBox, Component component) {
		super(i, j, k, l, component);
		this.font = font;
		if (editBox != null) {
			this.setValue(editBox.getValue());
		}

		this.updateTextPosition();
	}

	public void setResponder(Consumer<String> consumer) {
		this.responder = consumer;
	}

	public void addFormatter(EditBox.TextFormatter textFormatter) {
		this.formatters.add(textFormatter);
	}

	@Override
	protected MutableComponent createNarrationMessage() {
		Component component = this.getMessage();
		return Component.translatable("gui.narrate.editBox", component, this.value);
	}

	public void setValue(String string) {
		if (this.filter.test(string)) {
			if (string.length() > this.maxLength) {
				this.value = string.substring(0, this.maxLength);
			} else {
				this.value = string;
			}

			this.moveCursorToEnd(false);
			this.setHighlightPos(this.cursorPos);
			this.onValueChange(string);
		}
	}

	public String getValue() {
		return this.value;
	}

	public String getHighlighted() {
		int i = Math.min(this.cursorPos, this.highlightPos);
		int j = Math.max(this.cursorPos, this.highlightPos);
		return this.value.substring(i, j);
	}

	@Override
	public void setX(int i) {
		super.setX(i);
		this.updateTextPosition();
	}

	@Override
	public void setY(int i) {
		super.setY(i);
		this.updateTextPosition();
	}

	public void setFilter(Predicate<String> predicate) {
		this.filter = predicate;
	}

	public void insertText(String string) {
		int i = Math.min(this.cursorPos, this.highlightPos);
		int j = Math.max(this.cursorPos, this.highlightPos);
		int k = this.maxLength - this.value.length() - (i - j);
		if (k > 0) {
			String string2 = StringUtil.filterText(string);
			int l = string2.length();
			if (k < l) {
				if (Character.isHighSurrogate(string2.charAt(k - 1))) {
					k--;
				}

				string2 = string2.substring(0, k);
				l = k;
			}

			String string3 = new StringBuilder(this.value).replace(i, j, string2).toString();
			if (this.filter.test(string3)) {
				this.value = string3;
				this.setCursorPosition(i + l);
				this.setHighlightPos(this.cursorPos);
				this.onValueChange(this.value);
			}
		}
	}

	private void onValueChange(String string) {
		if (this.responder != null) {
			this.responder.accept(string);
		}

		this.updateTextPosition();
	}

	private void deleteText(int i, boolean bl) {
		if (bl) {
			this.deleteWords(i);
		} else {
			this.deleteChars(i);
		}
	}

	public void deleteWords(int i) {
		if (!this.value.isEmpty()) {
			if (this.highlightPos != this.cursorPos) {
				this.insertText("");
			} else {
				this.deleteCharsToPos(this.getWordPosition(i));
			}
		}
	}

	public void deleteChars(int i) {
		this.deleteCharsToPos(this.getCursorPos(i));
	}

	public void deleteCharsToPos(int i) {
		if (!this.value.isEmpty()) {
			if (this.highlightPos != this.cursorPos) {
				this.insertText("");
			} else {
				int j = Math.min(i, this.cursorPos);
				int k = Math.max(i, this.cursorPos);
				if (j != k) {
					String string = new StringBuilder(this.value).delete(j, k).toString();
					if (this.filter.test(string)) {
						this.value = string;
						this.moveCursorTo(j, false);
					}
				}
			}
		}
	}

	public int getWordPosition(int i) {
		return this.getWordPosition(i, this.getCursorPosition());
	}

	private int getWordPosition(int i, int j) {
		return this.getWordPosition(i, j, true);
	}

	private int getWordPosition(int i, int j, boolean bl) {
		int k = j;
		boolean bl2 = i < 0;
		int l = Math.abs(i);

		for (int m = 0; m < l; m++) {
			if (!bl2) {
				int n = this.value.length();
				k = this.value.indexOf(32, k);
				if (k == -1) {
					k = n;
				} else {
					while (bl && k < n && this.value.charAt(k) == ' ') {
						k++;
					}
				}
			} else {
				while (bl && k > 0 && this.value.charAt(k - 1) == ' ') {
					k--;
				}

				while (k > 0 && this.value.charAt(k - 1) != ' ') {
					k--;
				}
			}
		}

		return k;
	}

	public void moveCursor(int i, boolean bl) {
		this.moveCursorTo(this.getCursorPos(i), bl);
	}

	private int getCursorPos(int i) {
		return Util.offsetByCodepoints(this.value, this.cursorPos, i);
	}

	public void moveCursorTo(int i, boolean bl) {
		this.setCursorPosition(i);
		if (!bl) {
			this.setHighlightPos(this.cursorPos);
		}

		this.onValueChange(this.value);
	}

	public void setCursorPosition(int i) {
		this.cursorPos = Mth.clamp(i, 0, this.value.length());
		this.scrollTo(this.cursorPos);
	}

	public void moveCursorToStart(boolean bl) {
		this.moveCursorTo(0, bl);
	}

	public void moveCursorToEnd(boolean bl) {
		this.moveCursorTo(this.value.length(), bl);
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if (this.isActive() && this.isFocused()) {
			switch (keyEvent.key()) {
				case 259:
					if (this.isEditable) {
						this.deleteText(-1, keyEvent.hasControlDownWithQuirk());
					}

					return true;
				case 260:
				case 264:
				case 265:
				case 266:
				case 267:
				default:
					if (keyEvent.isSelectAll()) {
						this.moveCursorToEnd(false);
						this.setHighlightPos(0);
						return true;
					} else if (keyEvent.isCopy()) {
						Minecraft.getInstance().keyboardHandler.setClipboard(this.getHighlighted());
						return true;
					} else if (keyEvent.isPaste()) {
						if (this.isEditable()) {
							this.insertText(Minecraft.getInstance().keyboardHandler.getClipboard());
						}

						return true;
					} else {
						if (keyEvent.isCut()) {
							Minecraft.getInstance().keyboardHandler.setClipboard(this.getHighlighted());
							if (this.isEditable()) {
								this.insertText("");
							}

							return true;
						}

						return false;
					}
				case 261:
					if (this.isEditable) {
						this.deleteText(1, keyEvent.hasControlDownWithQuirk());
					}

					return true;
				case 262:
					if (keyEvent.hasControlDownWithQuirk()) {
						this.moveCursorTo(this.getWordPosition(1), keyEvent.hasShiftDown());
					} else {
						this.moveCursor(1, keyEvent.hasShiftDown());
					}

					return true;
				case 263:
					if (keyEvent.hasControlDownWithQuirk()) {
						this.moveCursorTo(this.getWordPosition(-1), keyEvent.hasShiftDown());
					} else {
						this.moveCursor(-1, keyEvent.hasShiftDown());
					}

					return true;
				case 268:
					this.moveCursorToStart(keyEvent.hasShiftDown());
					return true;
				case 269:
					this.moveCursorToEnd(keyEvent.hasShiftDown());
					return true;
			}
		} else {
			return false;
		}
	}

	public boolean canConsumeInput() {
		return this.isActive() && this.isFocused() && this.isEditable();
	}

	@Override
	public boolean charTyped(CharacterEvent characterEvent) {
		if (!this.canConsumeInput()) {
			return false;
		}

		if (characterEvent.isAllowedChatCharacter()) {
			if (this.isEditable) {
				this.insertText(characterEvent.codepointAsString());
			}

			return true;
		} else {
			return false;
		}
	}

	private int findClickedPositionInText(MouseButtonEvent mouseButtonEvent) {
		int i = Math.min(Mth.floor(mouseButtonEvent.x()) - this.textX, this.getInnerWidth());
		String string = this.value.substring(this.displayPos);
		return this.displayPos + this.font.plainSubstrByWidth(string, i).length();
	}

	private void selectWord(MouseButtonEvent mouseButtonEvent) {
		int i = this.findClickedPositionInText(mouseButtonEvent);
		int j = this.getWordPosition(-1, i);
		int k = this.getWordPosition(1, i);
		this.moveCursorTo(j, false);
		this.moveCursorTo(k, true);
	}

	@Override
	public void onClick(MouseButtonEvent mouseButtonEvent, boolean bl) {
		if (bl) {
			this.selectWord(mouseButtonEvent);
		} else {
			this.moveCursorTo(this.findClickedPositionInText(mouseButtonEvent), mouseButtonEvent.hasShiftDown());
		}
	}

	@Override
	protected void onDrag(MouseButtonEvent mouseButtonEvent, double d, double e) {
		this.moveCursorTo(this.findClickedPositionInText(mouseButtonEvent), true);
	}

	@Override
	public void playDownSound(SoundManager soundManager) {
	}

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
		if (this.isVisible()) {
			if (this.isBordered()) {
				Identifier identifier = SPRITES.get(this.isActive(), this.isFocused());
				guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, identifier, this.getX(), this.getY(), this.getWidth(), this.getHeight());
			}

			int k = this.isEditable ? this.textColor : this.textColorUneditable;
			int l = this.cursorPos - this.displayPos;
			String string = this.font.plainSubstrByWidth(this.value.substring(this.displayPos), this.getInnerWidth());
			boolean bl = l >= 0 && l <= string.length();
			boolean bl2 = this.isFocused() && (Util.getMillis() - this.focusedTime) / 300L % 2L == 0L && bl;
			int m = this.textX;
			int n = Mth.clamp(this.highlightPos - this.displayPos, 0, string.length());
			if (!string.isEmpty()) {
				String string2 = bl ? string.substring(0, l) : string;
				FormattedCharSequence formattedCharSequence = this.applyFormat(string2, this.displayPos);
				guiGraphics.drawString(this.font, formattedCharSequence, m, this.textY, k, this.textShadow);
				m += this.font.width(formattedCharSequence) + 1;
			}

			boolean bl3 = this.cursorPos < this.value.length() || this.value.length() >= this.getMaxLength();
			int o = m;
			if (!bl) {
				o = l > 0 ? this.textX + this.width : this.textX;
			} else if (bl3) {
				o--;
				m--;
			}

			if (!string.isEmpty() && bl && l < string.length()) {
				guiGraphics.drawString(this.font, this.applyFormat(string.substring(l), this.cursorPos), m, this.textY, k, this.textShadow);
			}

			if (this.hint != null && string.isEmpty() && !this.isFocused()) {
				guiGraphics.drawString(this.font, this.hint, m, this.textY, k);
			}

			if (!bl3 && this.suggestion != null) {
				guiGraphics.drawString(this.font, this.suggestion, o - 1, this.textY, -8355712, this.textShadow);
			}

			if (n != l) {
				int p = this.textX + this.font.width(string.substring(0, n));
				guiGraphics.textHighlight(
					Math.min(o, this.getX() + this.width), this.textY - 1, Math.min(p - 1, this.getX() + this.width), this.textY + 1 + 9, this.invertHighlightedTextColor
				);
			}

			if (bl2) {
				if (bl3) {
					guiGraphics.fill(o, this.textY - 1, o + 1, this.textY + 1 + 9, k);
				} else {
					guiGraphics.drawString(this.font, "_", o, this.textY, k, this.textShadow);
				}
			}

			if (this.isHovered()) {
				guiGraphics.requestCursor(this.isEditable() ? CursorTypes.IBEAM : CursorTypes.NOT_ALLOWED);
			}
		}
	}

	private FormattedCharSequence applyFormat(String string, int i) {
		for (EditBox.TextFormatter textFormatter : this.formatters) {
			FormattedCharSequence formattedCharSequence = textFormatter.format(string, i);
			if (formattedCharSequence != null) {
				return formattedCharSequence;
			}
		}

		return FormattedCharSequence.forward(string, Style.EMPTY);
	}

	private void updateTextPosition() {
		if (this.font != null) {
			String string = this.font.plainSubstrByWidth(this.value.substring(this.displayPos), this.getInnerWidth());
			this.textX = this.getX() + (this.isCentered() ? (this.getWidth() - this.font.width(string)) / 2 : (this.bordered ? 4 : 0));
			this.textY = this.bordered ? this.getY() + (this.height - 8) / 2 : this.getY();
		}
	}

	public void setMaxLength(int i) {
		this.maxLength = i;
		if (this.value.length() > i) {
			this.value = this.value.substring(0, i);
			this.onValueChange(this.value);
		}
	}

	private int getMaxLength() {
		return this.maxLength;
	}

	public int getCursorPosition() {
		return this.cursorPos;
	}

	public boolean isBordered() {
		return this.bordered;
	}

	public void setBordered(boolean bl) {
		this.bordered = bl;
		this.updateTextPosition();
	}

	public void setTextColor(int i) {
		this.textColor = i;
	}

	public void setTextColorUneditable(int i) {
		this.textColorUneditable = i;
	}

	@Override
	public void setFocused(boolean bl) {
		if (this.canLoseFocus || bl) {
			super.setFocused(bl);
			if (bl) {
				this.focusedTime = Util.getMillis();
			}
		}
	}

	private boolean isEditable() {
		return this.isEditable;
	}

	public void setEditable(boolean bl) {
		this.isEditable = bl;
	}

	private boolean isCentered() {
		return this.centered;
	}

	public void setCentered(boolean bl) {
		this.centered = bl;
		this.updateTextPosition();
	}

	public void setTextShadow(boolean bl) {
		this.textShadow = bl;
	}

	public void setInvertHighlightedTextColor(boolean bl) {
		this.invertHighlightedTextColor = bl;
	}

	public int getInnerWidth() {
		return this.isBordered() ? this.width - 8 : this.width;
	}

	public void setHighlightPos(int i) {
		this.highlightPos = Mth.clamp(i, 0, this.value.length());
		this.scrollTo(this.highlightPos);
	}

	private void scrollTo(int i) {
		if (this.font != null) {
			this.displayPos = Math.min(this.displayPos, this.value.length());
			int j = this.getInnerWidth();
			String string = this.font.plainSubstrByWidth(this.value.substring(this.displayPos), j);
			int k = string.length() + this.displayPos;
			if (i == this.displayPos) {
				this.displayPos = this.displayPos - this.font.plainSubstrByWidth(this.value, j, true).length();
			}

			if (i > k) {
				this.displayPos += i - k;
			} else if (i <= this.displayPos) {
				this.displayPos = this.displayPos - (this.displayPos - i);
			}

			this.displayPos = Mth.clamp(this.displayPos, 0, this.value.length());
		}
	}

	public void setCanLoseFocus(boolean bl) {
		this.canLoseFocus = bl;
	}

	public boolean isVisible() {
		return this.visible;
	}

	public void setVisible(boolean bl) {
		this.visible = bl;
	}

	public void setSuggestion(@Nullable String string) {
		this.suggestion = string;
	}

	public int getScreenX(int i) {
		return i > this.value.length() ? this.getX() : this.getX() + this.font.width(this.value.substring(0, i));
	}

	@Override
	public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
		narrationElementOutput.add(NarratedElementType.TITLE, this.createNarrationMessage());
	}

	public void setHint(Component component) {
		boolean bl = component.getStyle().equals(Style.EMPTY);
		this.hint = bl ? component.copy().withStyle(DEFAULT_HINT_STYLE) : component;
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	public interface TextFormatter {
		@Nullable FormattedCharSequence format(String string, int i);
	}
}
