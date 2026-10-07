package net.minecraft.client.gui.components;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.Optionull;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.ArrayListDeque;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class ChatComponent {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int MAX_CHAT_HISTORY = 100;
	private static final int MESSAGE_INDENT = 4;
	private static final int BOTTOM_MARGIN = 40;
	private static final int TOOLTIP_MAX_WIDTH = 210;
	private static final int TIME_BEFORE_MESSAGE_DELETION = 60;
	private static final Component DELETED_CHAT_MESSAGE = Component.translatable("chat.deleted_marker").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
	public static final int MESSAGE_BOTTOM_TO_MESSAGE_TOP = 8;
	public static final Identifier QUEUE_EXPAND_ID = Identifier.withDefaultNamespace("internal/expand_chat_queue");
	private static final Style QUEUE_EXPAND_TEXT_STYLE = Style.EMPTY
		.withClickEvent(new ClickEvent.Custom(QUEUE_EXPAND_ID, Optional.empty()))
		.withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.queue.tooltip")));
	final Minecraft minecraft;
	private final ArrayListDeque<String> recentChat = new ArrayListDeque<>(100);
	private final List<GuiMessage> allMessages = Lists.newArrayList();
	private final List<GuiMessage.Line> trimmedMessages = Lists.newArrayList();
	private int chatScrollbarPos;
	private boolean newMessageSinceScroll;
	private ChatComponent.@Nullable Draft latestDraft;
	private @Nullable ChatScreen preservedScreen;
	private final List<ChatComponent.DelayedMessageDeletion> messageDeletionQueue = new ArrayList<>();

	public ChatComponent(Minecraft minecraft) {
		this.minecraft = minecraft;
		this.recentChat.addAll(minecraft.commandHistory().history());
	}

	public void tick() {
		if (!this.messageDeletionQueue.isEmpty()) {
			this.processMessageDeletionQueue();
		}
	}

	private int forEachLine(ChatComponent.AlphaCalculator alphaCalculator, ChatComponent.LineConsumer lineConsumer) {
		int i = this.getLinesPerPage();
		int j = 0;

		for (int k = Math.min(this.trimmedMessages.size() - this.chatScrollbarPos, i) - 1; k >= 0; k--) {
			int l = k + this.chatScrollbarPos;
			GuiMessage.Line line = this.trimmedMessages.get(l);
			float f = alphaCalculator.calculate(line);
			if (f > 1.0E-5F) {
				j++;
				lineConsumer.accept(line, k, f);
			}
		}

		return j;
	}

	public void render(GuiGraphics guiGraphics, Font font, int i, int j, int k, boolean bl, boolean bl2) {
		guiGraphics.pose().pushMatrix();
		this.render(
			bl ? new ChatComponent.DrawingFocusedGraphicsAccess(guiGraphics, font, j, k, bl2) : new ChatComponent.DrawingBackgroundGraphicsAccess(guiGraphics),
			guiGraphics.guiHeight(),
			i,
			bl
		);
		guiGraphics.pose().popMatrix();
	}

	public void captureClickableText(ActiveTextCollector activeTextCollector, int i, int j, boolean bl) {
		this.render(new ChatComponent.ClickableTextOnlyGraphicsAccess(activeTextCollector), i, j, bl);
	}

	private void render(ChatComponent.ChatGraphicsAccess chatGraphicsAccess, int i, int j, boolean bl) {
		if (!this.isChatHidden()) {
			int k = this.trimmedMessages.size();
			if (k > 0) {
				ProfilerFiller profilerFiller = Profiler.get();
				profilerFiller.push("chat");
				float f = (float)this.getScale();
				int l = Mth.ceil(this.getWidth() / f);
				final int m = Mth.floor((i - 40) / f);
				final float g = this.minecraft.options.chatOpacity().get().floatValue() * 0.9F + 0.1F;
				float h = this.minecraft.options.textBackgroundOpacity().get().floatValue();
				final int n = 9;
				int o = 8;
				double d = this.minecraft.options.chatLineSpacing().get();
				final int p = (int)(n * (d + 1.0));
				final int q = (int)Math.round(8.0 * (d + 1.0) - 4.0 * d);
				long r = this.minecraft.getChatListener().queueSize();
				ChatComponent.AlphaCalculator alphaCalculator = bl ? ChatComponent.AlphaCalculator.FULLY_VISIBLE : ChatComponent.AlphaCalculator.timeBased(j);
				chatGraphicsAccess.updatePose(matrix3x2f -> {
					matrix3x2f.scale(f, f);
					matrix3x2f.translate(4.0F, 0.0F);
				});
				this.forEachLine(alphaCalculator, (line, lx, gx) -> {
					int mx = m - lx * p;
					int nx = mx - p;
					chatGraphicsAccess.fill(-4, nx, l + 4 + 4, mx, ARGB.black(gx * h));
				});
				if (r > 0L) {
					chatGraphicsAccess.fill(-2, m, l + 4, m + n, ARGB.black(h));
				}

				int s = this.forEachLine(alphaCalculator, new ChatComponent.LineConsumer() {
					boolean hoveredOverCurrentMessage;

					@Override
					public void accept(GuiMessage.Line line, int ix, float fx) {
						int jx = m - ix * p;
						int kx = jx - p;
						int lx = jx - q;
						boolean blx = chatGraphicsAccess.handleMessage(lx, fx * g, line.content());
						this.hoveredOverCurrentMessage |= blx;
						boolean bl2;
						if (line.endOfEntry()) {
							bl2 = this.hoveredOverCurrentMessage;
							this.hoveredOverCurrentMessage = false;
						} else {
							bl2 = false;
						}

						GuiMessageTag guiMessageTag = line.tag();
						if (guiMessageTag != null) {
							chatGraphicsAccess.handleTag(-4, kx, -2, jx, fx * g, guiMessageTag);
							if (guiMessageTag.icon() != null) {
								int mx = line.getTagIconLeft(ChatComponent.this.minecraft.font);
								int nx = lx + n;
								chatGraphicsAccess.handleTagIcon(mx, nx, bl2, guiMessageTag, guiMessageTag.icon());
							}
						}
					}
				});
				if (r > 0L) {
					int t = m + n;
					Component component = Component.translatable("chat.queue", r).setStyle(QUEUE_EXPAND_TEXT_STYLE);
					chatGraphicsAccess.handleMessage(t - 8, 0.5F * g, component.getVisualOrderText());
				}

				if (bl) {
					int t = k * p;
					int u = s * p;
					int v = this.chatScrollbarPos * u / k - m;
					int w = u * u / t;
					if (t != u) {
						int x = v > 0 ? 170 : 96;
						int y = this.newMessageSinceScroll ? 13382451 : 3355562;
						int z = l + 4;
						chatGraphicsAccess.fill(z, -v, z + 2, -v - w, ARGB.color(x, y));
						chatGraphicsAccess.fill(z + 2, -v, z + 1, -v - w, ARGB.color(x, 13421772));
					}
				}

				profilerFiller.pop();
			}
		}
	}

	private boolean isChatHidden() {
		return this.minecraft.options.chatVisibility().get() == ChatVisiblity.HIDDEN;
	}

	public void clearMessages(boolean bl) {
		this.minecraft.getChatListener().flushQueue();
		this.messageDeletionQueue.clear();
		this.trimmedMessages.clear();
		this.allMessages.clear();
		if (bl) {
			this.recentChat.clear();
			this.recentChat.addAll(this.minecraft.commandHistory().history());
		}
	}

	public void addMessage(Component component) {
		this.addMessage(component, null, this.minecraft.isSingleplayer() ? GuiMessageTag.systemSinglePlayer() : GuiMessageTag.system());
	}

	public void addMessage(Component component, @Nullable MessageSignature messageSignature, @Nullable GuiMessageTag guiMessageTag) {
		GuiMessage guiMessage = new GuiMessage(this.minecraft.gui.getGuiTicks(), component, messageSignature, guiMessageTag);
		this.logChatMessage(guiMessage);
		this.addMessageToDisplayQueue(guiMessage);
		this.addMessageToQueue(guiMessage);
	}

	private void logChatMessage(GuiMessage guiMessage) {
		String string = guiMessage.content().getString().replaceAll("\r", "\\\\r").replaceAll("\n", "\\\\n");
		String string2 = Optionull.map(guiMessage.tag(), GuiMessageTag::logTag);
		if (string2 != null) {
			LOGGER.info("[{}] [CHAT] {}", string2, string);
		} else {
			LOGGER.info("[CHAT] {}", string);
		}
	}

	private void addMessageToDisplayQueue(GuiMessage guiMessage) {
		int i = Mth.floor(this.getWidth() / this.getScale());
		List<FormattedCharSequence> list = guiMessage.splitLines(this.minecraft.font, i);
		boolean bl = this.isChatFocused();

		for (int j = 0; j < list.size(); j++) {
			FormattedCharSequence formattedCharSequence = list.get(j);
			if (bl && this.chatScrollbarPos > 0) {
				this.newMessageSinceScroll = true;
				this.scrollChat(1);
			}

			boolean bl2 = j == list.size() - 1;
			this.trimmedMessages.addFirst(new GuiMessage.Line(guiMessage.addedTime(), formattedCharSequence, guiMessage.tag(), bl2));
		}

		while (this.trimmedMessages.size() > 100) {
			this.trimmedMessages.removeLast();
		}
	}

	private void addMessageToQueue(GuiMessage guiMessage) {
		this.allMessages.addFirst(guiMessage);

		while (this.allMessages.size() > 100) {
			this.allMessages.removeLast();
		}
	}

	private void processMessageDeletionQueue() {
		int i = this.minecraft.gui.getGuiTicks();
		this.messageDeletionQueue
			.removeIf(
				delayedMessageDeletion -> i >= delayedMessageDeletion.deletableAfter() ? this.deleteMessageOrDelay(delayedMessageDeletion.signature()) == null : false
			);
	}

	public void deleteMessage(MessageSignature messageSignature) {
		ChatComponent.DelayedMessageDeletion delayedMessageDeletion = this.deleteMessageOrDelay(messageSignature);
		if (delayedMessageDeletion != null) {
			this.messageDeletionQueue.add(delayedMessageDeletion);
		}
	}

	private ChatComponent.@Nullable DelayedMessageDeletion deleteMessageOrDelay(MessageSignature messageSignature) {
		int i = this.minecraft.gui.getGuiTicks();
		ListIterator<GuiMessage> listIterator = this.allMessages.listIterator();

		while (listIterator.hasNext()) {
			GuiMessage guiMessage = listIterator.next();
			if (messageSignature.equals(guiMessage.signature())) {
				int j = guiMessage.addedTime() + 60;
				if (i >= j) {
					listIterator.set(this.createDeletedMarker(guiMessage));
					this.refreshTrimmedMessages();
					return null;
				}

				return new ChatComponent.DelayedMessageDeletion(messageSignature, j);
			}
		}

		return null;
	}

	private GuiMessage createDeletedMarker(GuiMessage guiMessage) {
		return new GuiMessage(guiMessage.addedTime(), DELETED_CHAT_MESSAGE, null, GuiMessageTag.system());
	}

	public void rescaleChat() {
		this.resetChatScroll();
		this.refreshTrimmedMessages();
	}

	private void refreshTrimmedMessages() {
		this.trimmedMessages.clear();

		for (GuiMessage guiMessage : Lists.reverse(this.allMessages)) {
			this.addMessageToDisplayQueue(guiMessage);
		}
	}

	public ArrayListDeque<String> getRecentChat() {
		return this.recentChat;
	}

	public void addRecentChat(String string) {
		if (!string.equals(this.recentChat.peekLast())) {
			if (this.recentChat.size() >= 100) {
				this.recentChat.removeFirst();
			}

			this.recentChat.addLast(string);
		}

		if (string.startsWith("/")) {
			this.minecraft.commandHistory().addCommand(string);
		}
	}

	public void resetChatScroll() {
		this.chatScrollbarPos = 0;
		this.newMessageSinceScroll = false;
	}

	public void scrollChat(int i) {
		this.chatScrollbarPos += i;
		int j = this.trimmedMessages.size();
		if (this.chatScrollbarPos > j - this.getLinesPerPage()) {
			this.chatScrollbarPos = j - this.getLinesPerPage();
		}

		if (this.chatScrollbarPos <= 0) {
			this.chatScrollbarPos = 0;
			this.newMessageSinceScroll = false;
		}
	}

	public boolean isChatFocused() {
		return this.minecraft.screen instanceof ChatScreen;
	}

	private int getWidth() {
		return getWidth(this.minecraft.options.chatWidth().get());
	}

	private int getHeight() {
		return getHeight(this.isChatFocused() ? this.minecraft.options.chatHeightFocused().get() : this.minecraft.options.chatHeightUnfocused().get());
	}

	private double getScale() {
		return this.minecraft.options.chatScale().get();
	}

	public static int getWidth(double d) {
		int i = 320;
		int j = 40;
		return Mth.floor(d * 280.0 + 40.0);
	}

	public static int getHeight(double d) {
		int i = 180;
		int j = 20;
		return Mth.floor(d * 160.0 + 20.0);
	}

	public static double defaultUnfocusedPct() {
		int i = 180;
		int j = 20;
		return 70.0 / (getHeight(1.0) - 20);
	}

	public int getLinesPerPage() {
		return this.getHeight() / this.getLineHeight();
	}

	private int getLineHeight() {
		return (int)(9.0 * (this.minecraft.options.chatLineSpacing().get() + 1.0));
	}

	public void saveAsDraft(String string) {
		boolean bl = string.startsWith("/");
		this.latestDraft = new ChatComponent.Draft(string, bl ? ChatComponent.ChatMethod.COMMAND : ChatComponent.ChatMethod.MESSAGE);
	}

	public void discardDraft() {
		this.latestDraft = null;
	}

	public <T extends ChatScreen> T createScreen(ChatComponent.ChatMethod chatMethod, ChatScreen.ChatConstructor<T> chatConstructor) {
		return this.latestDraft != null && chatMethod.isDraftRestorable(this.latestDraft)
			? chatConstructor.create(this.latestDraft.text(), true)
			: chatConstructor.create(chatMethod.prefix(), false);
	}

	public void openScreen(ChatComponent.ChatMethod chatMethod, ChatScreen.ChatConstructor<?> chatConstructor) {
		this.minecraft.setScreen(this.createScreen(chatMethod, (ChatScreen.ChatConstructor<Screen>)chatConstructor));
	}

	public void preserveCurrentChatScreen() {
		if (this.minecraft.screen instanceof ChatScreen chatScreen) {
			this.preservedScreen = chatScreen;
		}
	}

	public @Nullable ChatScreen restoreChatScreen() {
		ChatScreen chatScreen = this.preservedScreen;
		this.preservedScreen = null;
		return chatScreen;
	}

	public ChatComponent.State storeState() {
		return new ChatComponent.State(List.copyOf(this.allMessages), List.copyOf(this.recentChat), List.copyOf(this.messageDeletionQueue));
	}

	public void restoreState(ChatComponent.State state) {
		this.recentChat.clear();
		this.recentChat.addAll(state.history);
		this.messageDeletionQueue.clear();
		this.messageDeletionQueue.addAll(state.delayedMessageDeletions);
		this.allMessages.clear();
		this.allMessages.addAll(state.messages);
		this.refreshTrimmedMessages();
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	interface AlphaCalculator {
		ChatComponent.AlphaCalculator FULLY_VISIBLE = line -> 1.0F;

		static ChatComponent.AlphaCalculator timeBased(int i) {
			return line -> {
				int j = i - line.addedTime();
				double d = j / 200.0;
				d = 1.0 - d;
				d *= 10.0;
				d = Mth.clamp(d, 0.0, 1.0);
				d *= d;
				return (float)d;
			};
		}

		float calculate(GuiMessage.Line line);
	}

	@Environment(EnvType.CLIENT)
	public interface ChatGraphicsAccess {
		void updatePose(Consumer<Matrix3x2f> consumer);

		void fill(int i, int j, int k, int l, int m);

		boolean handleMessage(int i, float f, FormattedCharSequence formattedCharSequence);

		void handleTag(int i, int j, int k, int l, float f, GuiMessageTag guiMessageTag);

		void handleTagIcon(int i, int j, boolean bl, GuiMessageTag guiMessageTag, GuiMessageTag.Icon icon);
	}

	@Environment(EnvType.CLIENT)
	public enum ChatMethod {
		MESSAGE("") {
			@Override
			public boolean isDraftRestorable(ChatComponent.Draft draft) {
				return true;
			}
		},
		COMMAND("/") {
			@Override
			public boolean isDraftRestorable(ChatComponent.Draft draft) {
				return this == draft.chatMethod;
			}
		};

		private final String prefix;

		ChatMethod(final String string2) {
			this.prefix = string2;
		}

		public String prefix() {
			return this.prefix;
		}

		public abstract boolean isDraftRestorable(ChatComponent.Draft draft);
	}

	@Environment(EnvType.CLIENT)
	static class ClickableTextOnlyGraphicsAccess implements ChatComponent.ChatGraphicsAccess {
		private final ActiveTextCollector output;

		public ClickableTextOnlyGraphicsAccess(ActiveTextCollector activeTextCollector) {
			this.output = activeTextCollector;
		}

		@Override
		public void updatePose(Consumer<Matrix3x2f> consumer) {
			ActiveTextCollector.Parameters parameters = this.output.defaultParameters();
			Matrix3x2f matrix3x2f = new Matrix3x2f(parameters.pose());
			consumer.accept(matrix3x2f);
			this.output.defaultParameters(parameters.withPose(matrix3x2f));
		}

		@Override
		public void fill(int i, int j, int k, int l, int m) {
		}

		@Override
		public boolean handleMessage(int i, float f, FormattedCharSequence formattedCharSequence) {
			this.output.accept(TextAlignment.LEFT, 0, i, formattedCharSequence);
			return false;
		}

		@Override
		public void handleTag(int i, int j, int k, int l, float f, GuiMessageTag guiMessageTag) {
		}

		@Override
		public void handleTagIcon(int i, int j, boolean bl, GuiMessageTag guiMessageTag, GuiMessageTag.Icon icon) {
		}
	}

	@Environment(EnvType.CLIENT)
	record DelayedMessageDeletion(MessageSignature signature, int deletableAfter) {
	}

	@Environment(EnvType.CLIENT)
	public record Draft(String text, ChatComponent.ChatMethod chatMethod) {
	}

	@Environment(EnvType.CLIENT)
	static class DrawingBackgroundGraphicsAccess implements ChatComponent.ChatGraphicsAccess {
		private final GuiGraphics graphics;
		private final ActiveTextCollector textRenderer;
		private ActiveTextCollector.Parameters parameters;

		public DrawingBackgroundGraphicsAccess(GuiGraphics guiGraphics) {
			this.graphics = guiGraphics;
			this.textRenderer = guiGraphics.textRenderer(GuiGraphics.HoveredTextEffects.NONE, null);
			this.parameters = this.textRenderer.defaultParameters();
		}

		@Override
		public void updatePose(Consumer<Matrix3x2f> consumer) {
			consumer.accept(this.graphics.pose());
			this.parameters = this.parameters.withPose(new Matrix3x2f(this.graphics.pose()));
		}

		@Override
		public void fill(int i, int j, int k, int l, int m) {
			this.graphics.fill(i, j, k, l, m);
		}

		@Override
		public boolean handleMessage(int i, float f, FormattedCharSequence formattedCharSequence) {
			this.textRenderer.accept(TextAlignment.LEFT, 0, i, this.parameters.withOpacity(f), formattedCharSequence);
			return false;
		}

		@Override
		public void handleTag(int i, int j, int k, int l, float f, GuiMessageTag guiMessageTag) {
			int m = ARGB.color(f, guiMessageTag.indicatorColor());
			this.graphics.fill(i, j, k, l, m);
		}

		@Override
		public void handleTagIcon(int i, int j, boolean bl, GuiMessageTag guiMessageTag, GuiMessageTag.Icon icon) {
		}
	}

	@Environment(EnvType.CLIENT)
	static class DrawingFocusedGraphicsAccess implements ChatComponent.ChatGraphicsAccess, Consumer<Style> {
		private final GuiGraphics graphics;
		private final Font font;
		private final ActiveTextCollector textRenderer;
		private ActiveTextCollector.Parameters parameters;
		private final int globalMouseX;
		private final int globalMouseY;
		private final Vector2f localMousePos = new Vector2f();
		private @Nullable Style hoveredStyle;
		private final boolean changeCursorOnInsertions;

		public DrawingFocusedGraphicsAccess(GuiGraphics guiGraphics, Font font, int i, int j, boolean bl) {
			this.graphics = guiGraphics;
			this.font = font;
			this.textRenderer = guiGraphics.textRenderer(GuiGraphics.HoveredTextEffects.TOOLTIP_AND_CURSOR, this);
			this.globalMouseX = i;
			this.globalMouseY = j;
			this.changeCursorOnInsertions = bl;
			this.parameters = this.textRenderer.defaultParameters();
			this.updateLocalMousePos();
		}

		private void updateLocalMousePos() {
			this.graphics.pose().invert(new Matrix3x2f()).transformPosition(this.globalMouseX, this.globalMouseY, this.localMousePos);
		}

		@Override
		public void updatePose(Consumer<Matrix3x2f> consumer) {
			consumer.accept(this.graphics.pose());
			this.parameters = this.parameters.withPose(new Matrix3x2f(this.graphics.pose()));
			this.updateLocalMousePos();
		}

		@Override
		public void fill(int i, int j, int k, int l, int m) {
			this.graphics.fill(i, j, k, l, m);
		}

		public void accept(Style style) {
			this.hoveredStyle = style;
		}

		@Override
		public boolean handleMessage(int i, float f, FormattedCharSequence formattedCharSequence) {
			this.hoveredStyle = null;
			this.textRenderer.accept(TextAlignment.LEFT, 0, i, this.parameters.withOpacity(f), formattedCharSequence);
			if (this.changeCursorOnInsertions && this.hoveredStyle != null && this.hoveredStyle.getInsertion() != null) {
				this.graphics.requestCursor(CursorTypes.POINTING_HAND);
			}

			return this.hoveredStyle != null;
		}

		private boolean isMouseOver(int i, int j, int k, int l) {
			return ActiveTextCollector.isPointInRectangle(this.localMousePos.x, this.localMousePos.y, i, j, k, l);
		}

		@Override
		public void handleTag(int i, int j, int k, int l, float f, GuiMessageTag guiMessageTag) {
			int m = ARGB.color(f, guiMessageTag.indicatorColor());
			this.graphics.fill(i, j, k, l, m);
			if (this.isMouseOver(i, j, k, l)) {
				this.showTooltip(guiMessageTag);
			}
		}

		@Override
		public void handleTagIcon(int i, int j, boolean bl, GuiMessageTag guiMessageTag, GuiMessageTag.Icon icon) {
			int k = j - icon.height - 1;
			int l = i + icon.width;
			boolean bl2 = this.isMouseOver(i, k, l, j);
			if (bl2) {
				this.showTooltip(guiMessageTag);
			}

			if (bl || bl2) {
				icon.draw(this.graphics, i, k);
			}
		}

		private void showTooltip(GuiMessageTag guiMessageTag) {
			if (guiMessageTag.text() != null) {
				this.graphics.setTooltipForNextFrame(this.font, this.font.split(guiMessageTag.text(), 210), this.globalMouseX, this.globalMouseY);
			}
		}
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	interface LineConsumer {
		void accept(GuiMessage.Line line, int i, float f);
	}

	@Environment(EnvType.CLIENT)
	public static class State {
		final List<GuiMessage> messages;
		final List<String> history;
		final List<ChatComponent.DelayedMessageDeletion> delayedMessageDeletions;

		public State(List<GuiMessage> list, List<String> list2, List<ChatComponent.DelayedMessageDeletion> list3) {
			this.messages = list;
			this.history = list2;
			this.delayedMessageDeletions = list3;
		}
	}
}
