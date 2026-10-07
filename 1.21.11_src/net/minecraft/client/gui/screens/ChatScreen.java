package net.minecraft.client.gui.screens;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ChatScreen extends Screen {
	public static final double MOUSE_SCROLL_SPEED = 7.0;
	private static final Component USAGE_TEXT = Component.translatable("chat_screen.usage");
	private String historyBuffer = "";
	private int historyPos = -1;
	protected EditBox input;
	protected String initial;
	protected boolean isDraft;
	protected ChatScreen.ExitReason exitReason = ChatScreen.ExitReason.INTERRUPTED;
	CommandSuggestions commandSuggestions;

	public ChatScreen(String string, boolean bl) {
		super(Component.translatable("chat_screen.title"));
		this.initial = string;
		this.isDraft = bl;
	}

	@Override
	protected void init() {
		this.historyPos = this.minecraft.gui.getChat().getRecentChat().size();
		this.input = new EditBox(this.minecraft.fontFilterFishy, 4, this.height - 12, this.width - 4, 12, Component.translatable("chat.editBox")) {
			@Override
			protected MutableComponent createNarrationMessage() {
				return super.createNarrationMessage().append(ChatScreen.this.commandSuggestions.getNarrationMessage());
			}
		};
		this.input.setMaxLength(256);
		this.input.setBordered(false);
		this.input.setValue(this.initial);
		this.input.setResponder(this::onEdited);
		this.input.addFormatter(this::formatChat);
		this.input.setCanLoseFocus(false);
		this.addRenderableWidget(this.input);
		this.commandSuggestions = new CommandSuggestions(this.minecraft, this, this.input, this.font, false, false, 1, 10, true, -805306368);
		this.commandSuggestions.setAllowHiding(false);
		this.commandSuggestions.setAllowSuggestions(false);
		this.commandSuggestions.updateCommandInfo();
	}

	@Override
	protected void setInitialFocus() {
		this.setInitialFocus(this.input);
	}

	@Override
	public void resize(int i, int j) {
		this.initial = this.input.getValue();
		this.init(i, j);
	}

	@Override
	public void onClose() {
		this.exitReason = ChatScreen.ExitReason.INTENTIONAL;
		super.onClose();
	}

	@Override
	public void removed() {
		this.minecraft.gui.getChat().resetChatScroll();
		this.initial = this.input.getValue();
		if (this.shouldDiscardDraft() || StringUtils.isBlank(this.initial)) {
			this.minecraft.gui.getChat().discardDraft();
		} else if (!this.isDraft) {
			this.minecraft.gui.getChat().saveAsDraft(this.initial);
		}
	}

	protected boolean shouldDiscardDraft() {
		return this.exitReason != ChatScreen.ExitReason.INTERRUPTED
			&& (this.exitReason != ChatScreen.ExitReason.INTENTIONAL || !this.minecraft.options.saveChatDrafts().get());
	}

	private void onEdited(String string) {
		this.commandSuggestions.setAllowSuggestions(true);
		this.commandSuggestions.updateCommandInfo();
		this.isDraft = false;
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if (this.commandSuggestions.keyPressed(keyEvent)) {
			return true;
		}

		if (this.isDraft && keyEvent.key() == 259) {
			this.input.setValue("");
			this.isDraft = false;
			return true;
		}

		if (super.keyPressed(keyEvent)) {
			return true;
		}

		if (keyEvent.isConfirmation()) {
			this.handleChatInput(this.input.getValue(), true);
			this.exitReason = ChatScreen.ExitReason.DONE;
			this.minecraft.setScreen(null);
			return true;
		}

		switch (keyEvent.key()) {
			case 264:
				this.moveInHistory(1);
				break;
			case 265:
				this.moveInHistory(-1);
				break;
			case 266:
				this.minecraft.gui.getChat().scrollChat(this.minecraft.gui.getChat().getLinesPerPage() - 1);
				break;
			case 267:
				this.minecraft.gui.getChat().scrollChat(-this.minecraft.gui.getChat().getLinesPerPage() + 1);
				break;
			default:
				return false;
		}

		return true;
	}

	@Override
	public boolean mouseScrolled(double d, double e, double f, double g) {
		g = Mth.clamp(g, -1.0, 1.0);
		if (this.commandSuggestions.mouseScrolled(g)) {
			return true;
		}

		if (!this.minecraft.hasShiftDown()) {
			g *= 7.0;
		}

		this.minecraft.gui.getChat().scrollChat((int)g);
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
		if (this.commandSuggestions.mouseClicked(mouseButtonEvent)) {
			return true;
		}

		if (mouseButtonEvent.button() == 0) {
			int i = this.minecraft.getWindow().getGuiScaledHeight();
			ActiveTextCollector.ClickableStyleFinder clickableStyleFinder = new ActiveTextCollector.ClickableStyleFinder(
					this.getFont(), (int)mouseButtonEvent.x(), (int)mouseButtonEvent.y()
				)
				.includeInsertions(this.insertionClickMode());
			this.minecraft.gui.getChat().captureClickableText(clickableStyleFinder, i, this.minecraft.gui.getGuiTicks(), true);
			Style style = clickableStyleFinder.result();
			if (style != null && this.handleComponentClicked(style, this.insertionClickMode())) {
				this.initial = this.input.getValue();
				return true;
			}
		}

		return super.mouseClicked(mouseButtonEvent, bl);
	}

	private boolean insertionClickMode() {
		return this.minecraft.hasShiftDown();
	}

	private boolean handleComponentClicked(Style style, boolean bl) {
		ClickEvent clickEvent = style.getClickEvent();
		if (bl) {
			if (style.getInsertion() != null) {
				this.insertText(style.getInsertion(), false);
			}
		} else if (clickEvent != null) {
			if (clickEvent instanceof ClickEvent.Custom custom && custom.id().equals(ChatComponent.QUEUE_EXPAND_ID)) {
				ChatListener chatListener = this.minecraft.getChatListener();
				if (chatListener.queueSize() != 0L) {
					chatListener.acceptNextDelayedMessage();
				}
			} else {
				defaultHandleGameClickEvent(clickEvent, this.minecraft, this);
			}

			return true;
		}

		return false;
	}

	@Override
	public void insertText(String string, boolean bl) {
		if (bl) {
			this.input.setValue(string);
		} else {
			this.input.insertText(string);
		}
	}

	public void moveInHistory(int i) {
		int j = this.historyPos + i;
		int k = this.minecraft.gui.getChat().getRecentChat().size();
		j = Mth.clamp(j, 0, k);
		if (j != this.historyPos) {
			if (j == k) {
				this.historyPos = k;
				this.input.setValue(this.historyBuffer);
			} else {
				if (this.historyPos == k) {
					this.historyBuffer = this.input.getValue();
				}

				this.input.setValue(this.minecraft.gui.getChat().getRecentChat().get(j));
				this.commandSuggestions.setAllowSuggestions(false);
				this.historyPos = j;
			}
		}
	}

	private @Nullable FormattedCharSequence formatChat(String string, int i) {
		return this.isDraft ? FormattedCharSequence.forward(string, Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(true)) : null;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		guiGraphics.fill(2, this.height - 14, this.width - 2, this.height - 2, this.minecraft.options.getBackgroundColor(Integer.MIN_VALUE));
		this.minecraft.gui.getChat().render(guiGraphics, this.font, this.minecraft.gui.getGuiTicks(), i, j, true, this.insertionClickMode());
		super.render(guiGraphics, i, j, f);
		this.commandSuggestions.render(guiGraphics, i, j);
	}

	@Override
	public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isAllowedInPortal() {
		return true;
	}

	@Override
	protected void updateNarrationState(NarrationElementOutput narrationElementOutput) {
		narrationElementOutput.add(NarratedElementType.TITLE, this.getTitle());
		narrationElementOutput.add(NarratedElementType.USAGE, USAGE_TEXT);
		String string = this.input.getValue();
		if (!string.isEmpty()) {
			narrationElementOutput.nest().add(NarratedElementType.TITLE, Component.translatable("chat_screen.message", string));
		}
	}

	public void handleChatInput(String string, boolean bl) {
		string = this.normalizeChatMessage(string);
		if (!string.isEmpty()) {
			if (bl) {
				this.minecraft.gui.getChat().addRecentChat(string);
			}

			if (string.startsWith("/")) {
				this.minecraft.player.connection.sendCommand(string.substring(1));
			} else {
				this.minecraft.player.connection.sendChat(string);
			}
		}
	}

	public String normalizeChatMessage(String string) {
		return StringUtil.trimChatMessage(StringUtils.normalizeSpace(string.trim()));
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	public interface ChatConstructor<T extends ChatScreen> {
		T create(String string, boolean bl);
	}

	@Environment(EnvType.CLIENT)
	protected enum ExitReason {
		INTENTIONAL,
		INTERRUPTED,
		DONE;
	}
}
