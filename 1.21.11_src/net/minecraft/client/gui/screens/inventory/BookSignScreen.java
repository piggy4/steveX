package net.minecraft.client.gui.screens.inventory;

import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

@Environment(EnvType.CLIENT)
public class BookSignScreen extends Screen {
	private static final Component EDIT_TITLE_LABEL = Component.translatable("book.editTitle");
	private static final Component FINALIZE_WARNING_LABEL = Component.translatable("book.finalizeWarning");
	private static final Component TITLE = Component.translatable("book.sign.title");
	private static final Component TITLE_EDIT_BOX = Component.translatable("book.sign.titlebox");
	private final BookEditScreen bookEditScreen;
	private final Player owner;
	private final List<String> pages;
	private final InteractionHand hand;
	private final Component ownerText;
	private EditBox titleBox;
	private String titleValue = "";

	public BookSignScreen(BookEditScreen bookEditScreen, Player player, InteractionHand interactionHand, List<String> list) {
		super(TITLE);
		this.bookEditScreen = bookEditScreen;
		this.owner = player;
		this.hand = interactionHand;
		this.pages = list;
		this.ownerText = Component.translatable("book.byAuthor", player.getName()).withStyle(ChatFormatting.DARK_GRAY);
	}

	@Override
	protected void init() {
		Button button = Button.builder(Component.translatable("book.finalizeButton"), buttonx -> {
			this.saveChanges();
			this.minecraft.setScreen(null);
		}).bounds(this.width / 2 - 100, 196, 98, 20).build();
		button.active = false;
		this.titleBox = this.addRenderableWidget(new EditBox(this.minecraft.font, (this.width - 114) / 2 - 3, 50, 114, 20, TITLE_EDIT_BOX));
		this.titleBox.setMaxLength(15);
		this.titleBox.setBordered(false);
		this.titleBox.setCentered(true);
		this.titleBox.setTextColor(-16777216);
		this.titleBox.setTextShadow(false);
		this.titleBox.setResponder(string -> button.active = !StringUtil.isBlank(string));
		this.titleBox.setValue(this.titleValue);
		this.addRenderableWidget(button);
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, buttonx -> {
			this.titleValue = this.titleBox.getValue();
			this.minecraft.setScreen(this.bookEditScreen);
		}).bounds(this.width / 2 + 2, 196, 98, 20).build());
	}

	@Override
	protected void setInitialFocus() {
		this.setInitialFocus(this.titleBox);
	}

	private void saveChanges() {
		int i = this.hand == InteractionHand.MAIN_HAND ? this.owner.getInventory().getSelectedSlot() : 40;
		this.minecraft.getConnection().send(new ServerboundEditBookPacket(i, this.pages, Optional.of(this.titleBox.getValue().trim())));
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if (this.titleBox.isFocused() && !this.titleBox.getValue().isEmpty() && keyEvent.isConfirmation()) {
			this.saveChanges();
			this.minecraft.setScreen(null);
			return true;
		} else {
			return super.keyPressed(keyEvent);
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		int k = (this.width - 192) / 2;
		int l = 2;
		int m = this.font.width(EDIT_TITLE_LABEL);
		guiGraphics.drawString(this.font, EDIT_TITLE_LABEL, k + 36 + (114 - m) / 2, 34, -16777216, false);
		int n = this.font.width(this.ownerText);
		guiGraphics.drawString(this.font, this.ownerText, k + 36 + (114 - n) / 2, 60, -16777216, false);
		guiGraphics.drawWordWrap(this.font, FINALIZE_WARNING_LABEL, k + 36, 82, 114, -16777216, false);
	}

	@Override
	public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
		super.renderBackground(guiGraphics, i, j, f);
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, BookViewScreen.BOOK_LOCATION, (this.width - 192) / 2, 2, 0.0F, 0.0F, 192, 192, 256, 256);
	}
}
