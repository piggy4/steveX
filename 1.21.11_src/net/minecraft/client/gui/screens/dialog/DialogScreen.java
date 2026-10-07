package net.minecraft.client.gui.screens.dialog;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.body.DialogBodyHandlers;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.body.DialogBody;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class DialogScreen<T extends Dialog> extends Screen {
	public static final Component DISCONNECT = Component.translatable("menu.custom_screen_info.disconnect");
	private static final int WARNING_BUTTON_SIZE = 20;
	private static final WidgetSprites WARNING_BUTTON_SPRITES = new WidgetSprites(
		Identifier.withDefaultNamespace("dialog/warning_button"),
		Identifier.withDefaultNamespace("dialog/warning_button_disabled"),
		Identifier.withDefaultNamespace("dialog/warning_button_highlighted")
	);
	private final T dialog;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private final @Nullable Screen previousScreen;
	private @Nullable ScrollableLayout bodyScroll;
	private Button warningButton;
	private final DialogConnectionAccess connectionAccess;
	private Supplier<Optional<ClickEvent>> onClose = DialogControlSet.EMPTY_ACTION;

	public DialogScreen(@Nullable Screen screen, T dialog, DialogConnectionAccess dialogConnectionAccess) {
		super(dialog.common().title());
		this.dialog = dialog;
		this.previousScreen = screen;
		this.connectionAccess = dialogConnectionAccess;
	}

	@Override
	protected final void init() {
		super.init();
		this.warningButton = this.createWarningButton();
		this.warningButton.setTabOrderGroup(-10);
		DialogControlSet dialogControlSet = new DialogControlSet(this);
		LinearLayout linearLayout = LinearLayout.vertical().spacing(10);
		linearLayout.defaultCellSetting().alignHorizontallyCenter();
		this.layout.addToHeader(this.createTitleWithWarningButton());

		for (DialogBody dialogBody : this.dialog.common().body()) {
			LayoutElement layoutElement = DialogBodyHandlers.createBodyElement(this, dialogBody);
			if (layoutElement != null) {
				linearLayout.addChild(layoutElement);
			}
		}

		for (Input input : this.dialog.common().inputs()) {
			dialogControlSet.addInput(input, linearLayout::addChild);
		}

		this.populateBodyElements(linearLayout, dialogControlSet, this.dialog, this.connectionAccess);
		this.bodyScroll = new ScrollableLayout(this.minecraft, linearLayout, this.layout.getContentHeight());
		this.layout.addToContents(this.bodyScroll);
		this.updateHeaderAndFooter(this.layout, dialogControlSet, this.dialog, this.connectionAccess);
		this.onClose = dialogControlSet.bindAction(this.dialog.onCancel());
		this.layout.visitWidgets(abstractWidget -> {
			if (abstractWidget != this.warningButton) {
				this.addRenderableWidget(abstractWidget);
			}
		});
		this.addRenderableWidget(this.warningButton);
		this.repositionElements();
	}

	protected void populateBodyElements(LinearLayout linearLayout, DialogControlSet dialogControlSet, T dialog, DialogConnectionAccess dialogConnectionAccess) {
	}

	protected void updateHeaderAndFooter(
		HeaderAndFooterLayout headerAndFooterLayout, DialogControlSet dialogControlSet, T dialog, DialogConnectionAccess dialogConnectionAccess
	) {
	}

	@Override
	protected void repositionElements() {
		this.bodyScroll.setMaxHeight(this.layout.getContentHeight());
		this.layout.arrangeElements();
		this.makeSureWarningButtonIsInBounds();
	}

	protected LayoutElement createTitleWithWarningButton() {
		LinearLayout linearLayout = LinearLayout.horizontal().spacing(10);
		linearLayout.defaultCellSetting().alignHorizontallyCenter().alignVerticallyMiddle();
		linearLayout.addChild(new StringWidget(this.title, this.font));
		linearLayout.addChild(this.warningButton);
		return linearLayout;
	}

	protected void makeSureWarningButtonIsInBounds() {
		int i = this.warningButton.getX();
		int j = this.warningButton.getY();
		if (i < 0 || j < 0 || i > this.width - 20 || j > this.height - 20) {
			this.warningButton.setX(Math.max(0, this.width - 40));
			this.warningButton.setY(Math.min(5, this.height));
		}
	}

	private Button createWarningButton() {
		ImageButton imageButton = new ImageButton(
			0,
			0,
			20,
			20,
			WARNING_BUTTON_SPRITES,
			button -> this.minecraft.setScreen(DialogScreen.WarningScreen.create(this.minecraft, this.connectionAccess, this)),
			Component.translatable("menu.custom_screen_info.button_narration")
		);
		imageButton.setTooltip(Tooltip.create(Component.translatable("menu.custom_screen_info.tooltip")));
		return imageButton;
	}

	@Override
	public boolean isPauseScreen() {
		return this.dialog.common().pause();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return this.dialog.common().canCloseWithEscape();
	}

	@Override
	public void onClose() {
		this.runAction(this.onClose.get(), DialogAction.CLOSE);
	}

	public void runAction(Optional<ClickEvent> optional) {
		this.runAction(optional, this.dialog.common().afterAction());
	}

	public void runAction(Optional<ClickEvent> optional, DialogAction dialogAction) {
		Screen screen = switch (dialogAction) {
			case NONE -> this;
			case CLOSE -> this.previousScreen;
			case WAIT_FOR_RESPONSE -> new WaitingForResponseScreen(this.previousScreen);
		};
		if (optional.isPresent()) {
			this.handleDialogClickEvent(optional.get(), screen);
		} else {
			this.minecraft.setScreen(screen);
		}
	}

	private void handleDialogClickEvent(ClickEvent clickEvent, @Nullable Screen screen) {
		switch (clickEvent) {
			case ClickEvent.RunCommand(String string):
				this.connectionAccess.runCommand(Commands.trimOptionalPrefix(string), screen);
				break;
			case ClickEvent.ShowDialog showDialog:
				this.connectionAccess.openDialog(showDialog.dialog(), screen);
				break;
			case ClickEvent.Custom custom:
				this.connectionAccess.sendCustomAction(custom.id(), custom.payload());
				this.minecraft.setScreen(screen);
				break;
			default:
				defaultHandleClickEvent(clickEvent, this.minecraft, screen);
		}
	}

	public @Nullable Screen previousScreen() {
		return this.previousScreen;
	}

	protected static LayoutElement packControlsIntoColumns(List<? extends LayoutElement> list, int i) {
		GridLayout gridLayout = new GridLayout();
		gridLayout.defaultCellSetting().alignHorizontallyCenter();
		gridLayout.columnSpacing(2).rowSpacing(2);
		int j = list.size();
		int k = j / i;
		int l = k * i;

		for (int m = 0; m < l; m++) {
			gridLayout.addChild(list.get(m), m / i, m % i);
		}

		if (j != l) {
			LinearLayout linearLayout = LinearLayout.horizontal().spacing(2);
			linearLayout.defaultCellSetting().alignHorizontallyCenter();

			for (int n = l; n < j; n++) {
				linearLayout.addChild(list.get(n));
			}

			gridLayout.addChild(linearLayout, k, 0, 1, i);
		}

		return gridLayout;
	}

	@Environment(EnvType.CLIENT)
	public static class WarningScreen extends ConfirmScreen {
		private final MutableObject<@Nullable Screen> returnScreen;

		public static Screen create(Minecraft minecraft, DialogConnectionAccess dialogConnectionAccess, Screen screen) {
			return new DialogScreen.WarningScreen(minecraft, dialogConnectionAccess, new MutableObject<>(screen));
		}

		private WarningScreen(Minecraft minecraft, DialogConnectionAccess dialogConnectionAccess, MutableObject<Screen> mutableObject) {
			super(
				bl -> {
					if (bl) {
						dialogConnectionAccess.disconnect(DialogScreen.DISCONNECT);
					} else {
						minecraft.setScreen(mutableObject.get());
					}
				},
				Component.translatable("menu.custom_screen_info.title"),
				Component.translatable("menu.custom_screen_info.contents"),
				CommonComponents.disconnectButtonLabel(minecraft.isLocalServer()),
				CommonComponents.GUI_BACK
			);
			this.returnScreen = mutableObject;
		}

		public @Nullable Screen returnScreen() {
			return this.returnScreen.get();
		}

		public void updateReturnScreen(@Nullable Screen screen) {
			this.returnScreen.setValue(screen);
		}
	}
}
