package net.minecraft.client.gui.screens.inventory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BookViewScreen extends Screen {
	public static final int PAGE_INDICATOR_TEXT_Y_OFFSET = 16;
	public static final int PAGE_TEXT_X_OFFSET = 36;
	public static final int PAGE_TEXT_Y_OFFSET = 30;
	private static final int BACKGROUND_TEXTURE_WIDTH = 256;
	private static final int BACKGROUND_TEXTURE_HEIGHT = 256;
	private static final Component TITLE = Component.translatable("book.view.title");
	private static final Style PAGE_TEXT_STYLE = Style.EMPTY.withoutShadow().withColor(-16777216);
	public static final BookViewScreen.BookAccess EMPTY_ACCESS = new BookViewScreen.BookAccess(List.of());
	public static final Identifier BOOK_LOCATION = Identifier.withDefaultNamespace("textures/gui/book.png");
	protected static final int TEXT_WIDTH = 114;
	protected static final int TEXT_HEIGHT = 128;
	protected static final int IMAGE_WIDTH = 192;
	private static final int PAGE_INDICATOR_X_OFFSET = 148;
	protected static final int IMAGE_HEIGHT = 192;
	private static final int PAGE_BUTTON_Y = 157;
	private static final int PAGE_BACK_BUTTON_X = 43;
	private static final int PAGE_FORWARD_BUTTON_X = 116;
	private BookViewScreen.BookAccess bookAccess;
	private int currentPage;
	private List<FormattedCharSequence> cachedPageComponents = Collections.emptyList();
	private int cachedPage = -1;
	private Component pageMsg = CommonComponents.EMPTY;
	private PageButton forwardButton;
	private PageButton backButton;
	private final boolean playTurnSound;

	public BookViewScreen(BookViewScreen.BookAccess bookAccess) {
		this(bookAccess, true);
	}

	public BookViewScreen() {
		this(EMPTY_ACCESS, false);
	}

	private BookViewScreen(BookViewScreen.BookAccess bookAccess, boolean bl) {
		super(TITLE);
		this.bookAccess = bookAccess;
		this.playTurnSound = bl;
	}

	public void setBookAccess(BookViewScreen.BookAccess bookAccess) {
		this.bookAccess = bookAccess;
		this.currentPage = Mth.clamp(this.currentPage, 0, bookAccess.getPageCount());
		this.updateButtonVisibility();
		this.cachedPage = -1;
	}

	public boolean setPage(int i) {
		int j = Mth.clamp(i, 0, this.bookAccess.getPageCount() - 1);
		if (j != this.currentPage) {
			this.currentPage = j;
			this.updateButtonVisibility();
			this.cachedPage = -1;
			return true;
		} else {
			return false;
		}
	}

	protected boolean forcePage(int i) {
		return this.setPage(i);
	}

	@Override
	protected void init() {
		this.createMenuControls();
		this.createPageControlButtons();
	}

	@Override
	public Component getNarrationMessage() {
		return CommonComponents.joinLines(super.getNarrationMessage(), this.getPageNumberMessage(), this.bookAccess.getPage(this.currentPage));
	}

	private Component getPageNumberMessage() {
		return Component.translatable("book.pageIndicator", this.currentPage + 1, Math.max(this.getNumPages(), 1)).withStyle(PAGE_TEXT_STYLE);
	}

	protected void createMenuControls() {
		this.addRenderableWidget(
			Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).pos((this.width - 200) / 2, this.menuControlsTop()).width(200).build()
		);
	}

	protected void createPageControlButtons() {
		int i = this.backgroundLeft();
		int j = this.backgroundTop();
		this.forwardButton = this.addRenderableWidget(new PageButton(i + 116, j + 157, true, button -> this.pageForward(), this.playTurnSound));
		this.backButton = this.addRenderableWidget(new PageButton(i + 43, j + 157, false, button -> this.pageBack(), this.playTurnSound));
		this.updateButtonVisibility();
	}

	private int getNumPages() {
		return this.bookAccess.getPageCount();
	}

	protected void pageBack() {
		if (this.currentPage > 0) {
			this.currentPage--;
		}

		this.updateButtonVisibility();
	}

	protected void pageForward() {
		if (this.currentPage < this.getNumPages() - 1) {
			this.currentPage++;
		}

		this.updateButtonVisibility();
	}

	private void updateButtonVisibility() {
		this.forwardButton.visible = this.currentPage < this.getNumPages() - 1;
		this.backButton.visible = this.currentPage > 0;
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if (super.keyPressed(keyEvent)) {
			return true;
		}

		return switch (keyEvent.key()) {
			case 266 -> {
				this.backButton.onPress(keyEvent);
				yield true;
			}
			case 267 -> {
				this.forwardButton.onPress(keyEvent);
				yield true;
			}
			default -> false;
		};
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		this.visitText(guiGraphics.textRenderer(GuiGraphics.HoveredTextEffects.TOOLTIP_AND_CURSOR), false);
	}

	private void visitText(ActiveTextCollector activeTextCollector, boolean bl) {
		if (this.cachedPage != this.currentPage) {
			FormattedText formattedText = ComponentUtils.mergeStyles(this.bookAccess.getPage(this.currentPage), PAGE_TEXT_STYLE);
			this.cachedPageComponents = this.font.split(formattedText, 114);
			this.pageMsg = this.getPageNumberMessage();
			this.cachedPage = this.currentPage;
		}

		int i = this.backgroundLeft();
		int j = this.backgroundTop();
		if (!bl) {
			activeTextCollector.accept(TextAlignment.RIGHT, i + 148, j + 16, this.pageMsg);
		}

		int k = Math.min(128 / 9, this.cachedPageComponents.size());

		for (int l = 0; l < k; l++) {
			FormattedCharSequence formattedCharSequence = this.cachedPageComponents.get(l);
			activeTextCollector.accept(i + 36, j + 30 + l * 9, formattedCharSequence);
		}
	}

	@Override
	public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
		super.renderBackground(guiGraphics, i, j, f);
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, BOOK_LOCATION, this.backgroundLeft(), this.backgroundTop(), 0.0F, 0.0F, 192, 192, 256, 256);
	}

	private int backgroundLeft() {
		return (this.width - 192) / 2;
	}

	private int backgroundTop() {
		return 2;
	}

	protected int menuControlsTop() {
		return this.backgroundTop() + 192 + 2;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
		if (mouseButtonEvent.button() == 0) {
			ActiveTextCollector.ClickableStyleFinder clickableStyleFinder = new ActiveTextCollector.ClickableStyleFinder(
				this.font, (int)mouseButtonEvent.x(), (int)mouseButtonEvent.y()
			);
			this.visitText(clickableStyleFinder, true);
			Style style = clickableStyleFinder.result();
			if (style != null && this.handleClickEvent(style.getClickEvent())) {
				return true;
			}
		}

		return super.mouseClicked(mouseButtonEvent, bl);
	}

	protected boolean handleClickEvent(@Nullable ClickEvent clickEvent) {
		if (clickEvent == null) {
			return false;
		}

		LocalPlayer localPlayer = Objects.requireNonNull(this.minecraft.player, "Player not available");
		switch (clickEvent) {
			case ClickEvent.ChangePage(int i):
				this.forcePage(i - 1);
				break;
			case ClickEvent.RunCommand(String string):
				this.closeContainerOnServer();
				clickCommandAction(localPlayer, string, null);
				break;
			default:
				defaultHandleGameClickEvent(clickEvent, this.minecraft, this);
		}

		return true;
	}

	protected void closeContainerOnServer() {
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	@Environment(EnvType.CLIENT)
	public record BookAccess(List<Component> pages) {
		public int getPageCount() {
			return this.pages.size();
		}

		public Component getPage(int i) {
			return i >= 0 && i < this.getPageCount() ? this.pages.get(i) : CommonComponents.EMPTY;
		}

		public static BookViewScreen.@Nullable BookAccess fromItem(ItemStack itemStack) {
			boolean bl = Minecraft.getInstance().isTextFilteringEnabled();
			WrittenBookContent writtenBookContent = itemStack.get(DataComponents.WRITTEN_BOOK_CONTENT);
			if (writtenBookContent != null) {
				return new BookViewScreen.BookAccess(writtenBookContent.getPages(bl));
			}

			WritableBookContent writableBookContent = itemStack.get(DataComponents.WRITABLE_BOOK_CONTENT);
			return writableBookContent != null ? new BookViewScreen.BookAccess(writableBookContent.getPages(bl).map(Component::literal).toList()) : null;
		}
	}
}
