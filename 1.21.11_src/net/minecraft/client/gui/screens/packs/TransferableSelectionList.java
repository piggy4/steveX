package net.minecraft.client.gui.screens.packs;

import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.SelectableEntry;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackCompatibility;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class TransferableSelectionList extends ObjectSelectionList<TransferableSelectionList.Entry> {
	static final Identifier SELECT_HIGHLIGHTED_SPRITE = Identifier.withDefaultNamespace("transferable_list/select_highlighted");
	static final Identifier SELECT_SPRITE = Identifier.withDefaultNamespace("transferable_list/select");
	static final Identifier UNSELECT_HIGHLIGHTED_SPRITE = Identifier.withDefaultNamespace("transferable_list/unselect_highlighted");
	static final Identifier UNSELECT_SPRITE = Identifier.withDefaultNamespace("transferable_list/unselect");
	static final Identifier MOVE_UP_HIGHLIGHTED_SPRITE = Identifier.withDefaultNamespace("transferable_list/move_up_highlighted");
	static final Identifier MOVE_UP_SPRITE = Identifier.withDefaultNamespace("transferable_list/move_up");
	static final Identifier MOVE_DOWN_HIGHLIGHTED_SPRITE = Identifier.withDefaultNamespace("transferable_list/move_down_highlighted");
	static final Identifier MOVE_DOWN_SPRITE = Identifier.withDefaultNamespace("transferable_list/move_down");
	static final Component INCOMPATIBLE_TITLE = Component.translatable("pack.incompatible");
	static final Component INCOMPATIBLE_CONFIRM_TITLE = Component.translatable("pack.incompatible.confirm.title");
	private static final int ENTRY_PADDING = 2;
	private final Component title;
	final PackSelectionScreen screen;

	public TransferableSelectionList(Minecraft minecraft, PackSelectionScreen packSelectionScreen, int i, int j, Component component) {
		super(minecraft, i, j, 33, 36);
		this.screen = packSelectionScreen;
		this.title = component;
		this.centerListVertically = false;
	}

	@Override
	public int getRowWidth() {
		return this.width - 4;
	}

	@Override
	protected int scrollBarX() {
		return this.getRight() - 6;
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		return this.getSelected() != null ? this.getSelected().keyPressed(keyEvent) : super.keyPressed(keyEvent);
	}

	public void updateList(Stream<PackSelectionModel.Entry> stream, PackSelectionModel.@Nullable EntryBase entryBase) {
		this.clearEntries();
		Component component = Component.empty().append(this.title).withStyle(ChatFormatting.UNDERLINE, ChatFormatting.BOLD);
		this.addEntry(new TransferableSelectionList.HeaderEntry(this.minecraft.font, component), (int)(9.0F * 1.5F));
		this.setSelected(null);
		stream.forEach(entry -> {
			TransferableSelectionList.PackEntry packEntry = new TransferableSelectionList.PackEntry(this.minecraft, this, entry);
			this.addEntry(packEntry);
			if (entryBase != null && entryBase.getId().equals(entry.getId())) {
				this.screen.setFocused(this);
				this.setFocused(packEntry);
			}
		});
		this.refreshScrollAmount();
	}

	@Environment(EnvType.CLIENT)
	public abstract class Entry extends ObjectSelectionList.Entry<TransferableSelectionList.Entry> {
		@Override
		public int getWidth() {
			return super.getWidth() - (TransferableSelectionList.this.scrollbarVisible() ? 6 : 0);
		}

		public abstract String getPackId();
	}

	@Environment(EnvType.CLIENT)
	public class HeaderEntry extends TransferableSelectionList.Entry {
		private final Font font;
		private final Component text;

		public HeaderEntry(final Font font, final Component component) {
			this.font = font;
			this.text = component;
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			guiGraphics.drawCenteredString(this.font, this.text, this.getX() + this.getWidth() / 2, this.getContentYMiddle() - 9 / 2, -1);
		}

		@Override
		public Component getNarration() {
			return this.text;
		}

		@Override
		public String getPackId() {
			return "";
		}
	}

	@Environment(EnvType.CLIENT)
	public class PackEntry extends TransferableSelectionList.Entry implements SelectableEntry {
		private static final int MAX_DESCRIPTION_WIDTH_PIXELS = 157;
		public static final int ICON_SIZE = 32;
		private final TransferableSelectionList parent;
		protected final Minecraft minecraft;
		private final PackSelectionModel.Entry pack;
		private final StringWidget nameWidget;
		private final MultiLineTextWidget descriptionWidget;

		public PackEntry(final Minecraft minecraft, final TransferableSelectionList transferableSelectionList2, final PackSelectionModel.Entry entry) {
			this.minecraft = minecraft;
			this.pack = entry;
			this.parent = transferableSelectionList2;
			this.nameWidget = new StringWidget(entry.getTitle(), minecraft.font);
			this.descriptionWidget = new MultiLineTextWidget(ComponentUtils.mergeStyles(entry.getExtendedDescription(), Style.EMPTY.withColor(-8355712)), minecraft.font);
			this.descriptionWidget.setMaxRows(2);
		}

		@Override
		public Component getNarration() {
			return Component.translatable("narrator.select", this.pack.getTitle());
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			PackCompatibility packCompatibility = this.pack.getCompatibility();
			if (!packCompatibility.isCompatible()) {
				int k = this.getContentX() - 1;
				int l = this.getContentY() - 1;
				int m = this.getContentRight() + 1;
				int n = this.getContentBottom() + 1;
				guiGraphics.fill(k, l, m, n, -8978432);
			}

			guiGraphics.blit(RenderPipelines.GUI_TEXTURED, this.pack.getIconTexture(), this.getContentX(), this.getContentY(), 0.0F, 0.0F, 32, 32, 32, 32);
			if (!this.nameWidget.getMessage().equals(this.pack.getTitle())) {
				this.nameWidget.setMessage(this.pack.getTitle());
			}

			if (!this.descriptionWidget.getMessage().getContents().equals(this.pack.getExtendedDescription().getContents())) {
				this.descriptionWidget.setMessage(ComponentUtils.mergeStyles(this.pack.getExtendedDescription(), Style.EMPTY.withColor(-8355712)));
			}

			if (this.showHoverOverlay() && (this.minecraft.options.touchscreen().get() || bl || this.parent.getSelected() == this && this.parent.isFocused())) {
				guiGraphics.fill(this.getContentX(), this.getContentY(), this.getContentX() + 32, this.getContentY() + 32, -1601138544);
				int k = i - this.getContentX();
				int l = j - this.getContentY();
				if (!this.pack.getCompatibility().isCompatible()) {
					this.nameWidget.setMessage(TransferableSelectionList.INCOMPATIBLE_TITLE);
					this.descriptionWidget.setMessage(this.pack.getCompatibility().getDescription());
				}

				if (this.pack.canSelect()) {
					if (this.mouseOverIcon(k, l, 32)) {
						guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, TransferableSelectionList.SELECT_HIGHLIGHTED_SPRITE, this.getContentX(), this.getContentY(), 32, 32);
						TransferableSelectionList.this.handleCursor(guiGraphics);
					} else {
						guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, TransferableSelectionList.SELECT_SPRITE, this.getContentX(), this.getContentY(), 32, 32);
					}
				} else {
					if (this.pack.canUnselect()) {
						if (this.mouseOverLeftHalf(k, l, 32)) {
							guiGraphics.blitSprite(
								RenderPipelines.GUI_TEXTURED, TransferableSelectionList.UNSELECT_HIGHLIGHTED_SPRITE, this.getContentX(), this.getContentY(), 32, 32
							);
							TransferableSelectionList.this.handleCursor(guiGraphics);
						} else {
							guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, TransferableSelectionList.UNSELECT_SPRITE, this.getContentX(), this.getContentY(), 32, 32);
						}
					}

					if (this.pack.canMoveUp()) {
						if (this.mouseOverTopRightQuarter(k, l, 32)) {
							guiGraphics.blitSprite(
								RenderPipelines.GUI_TEXTURED, TransferableSelectionList.MOVE_UP_HIGHLIGHTED_SPRITE, this.getContentX(), this.getContentY(), 32, 32
							);
							TransferableSelectionList.this.handleCursor(guiGraphics);
						} else {
							guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, TransferableSelectionList.MOVE_UP_SPRITE, this.getContentX(), this.getContentY(), 32, 32);
						}
					}

					if (this.pack.canMoveDown()) {
						if (this.mouseOverBottomRightQuarter(k, l, 32)) {
							guiGraphics.blitSprite(
								RenderPipelines.GUI_TEXTURED, TransferableSelectionList.MOVE_DOWN_HIGHLIGHTED_SPRITE, this.getContentX(), this.getContentY(), 32, 32
							);
							TransferableSelectionList.this.handleCursor(guiGraphics);
						} else {
							guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, TransferableSelectionList.MOVE_DOWN_SPRITE, this.getContentX(), this.getContentY(), 32, 32);
						}
					}
				}
			}

			this.nameWidget.setMaxWidth(157 - (TransferableSelectionList.this.scrollbarVisible() ? 6 : 0));
			this.nameWidget.setPosition(this.getContentX() + 32 + 2, this.getContentY() + 1);
			this.nameWidget.render(guiGraphics, i, j, f);
			this.descriptionWidget.setMaxWidth(157 - (TransferableSelectionList.this.scrollbarVisible() ? 6 : 0));
			this.descriptionWidget.setPosition(this.getContentX() + 32 + 2, this.getContentY() + 12);
			this.descriptionWidget.render(guiGraphics, i, j, f);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
			if (this.showHoverOverlay()) {
				int i = (int)mouseButtonEvent.x() - this.getContentX();
				int j = (int)mouseButtonEvent.y() - this.getContentY();
				if (this.pack.canSelect() && this.mouseOverIcon(i, j, 32)) {
					this.handlePackSelection();
					return true;
				}

				if (this.pack.canUnselect() && this.mouseOverLeftHalf(i, j, 32)) {
					this.pack.unselect();
					return true;
				}

				if (this.pack.canMoveUp() && this.mouseOverTopRightQuarter(i, j, 32)) {
					this.pack.moveUp();
					return true;
				}

				if (this.pack.canMoveDown() && this.mouseOverBottomRightQuarter(i, j, 32)) {
					this.pack.moveDown();
					return true;
				}
			}

			return super.mouseClicked(mouseButtonEvent, bl);
		}

		@Override
		public boolean keyPressed(KeyEvent keyEvent) {
			if (keyEvent.isConfirmation()) {
				this.keyboardSelection();
				return true;
			}

			if (keyEvent.hasShiftDown()) {
				if (keyEvent.isUp()) {
					this.keyboardMoveUp();
					return true;
				}

				if (keyEvent.isDown()) {
					this.keyboardMoveDown();
					return true;
				}
			}

			return super.keyPressed(keyEvent);
		}

		private boolean showHoverOverlay() {
			return !this.pack.isFixedPosition() || !this.pack.isRequired();
		}

		public void keyboardSelection() {
			if (this.pack.canSelect()) {
				this.handlePackSelection();
			} else if (this.pack.canUnselect()) {
				this.pack.unselect();
			}
		}

		private void keyboardMoveUp() {
			if (this.pack.canMoveUp()) {
				this.pack.moveUp();
			}
		}

		private void keyboardMoveDown() {
			if (this.pack.canMoveDown()) {
				this.pack.moveDown();
			}
		}

		private void handlePackSelection() {
			if (this.pack.getCompatibility().isCompatible()) {
				this.pack.select();
			} else {
				Component component = this.pack.getCompatibility().getConfirmation();
				this.minecraft.setScreen(new ConfirmScreen(bl -> {
					this.minecraft.setScreen(this.parent.screen);
					if (bl) {
						this.pack.select();
					}
				}, TransferableSelectionList.INCOMPATIBLE_CONFIRM_TITLE, component));
			}
		}

		@Override
		public String getPackId() {
			return this.pack.getId();
		}

		@Override
		public boolean shouldTakeFocusAfterInteraction() {
			return TransferableSelectionList.this.children().stream().anyMatch(entry -> entry.getPackId().equals(this.getPackId()));
		}
	}
}
