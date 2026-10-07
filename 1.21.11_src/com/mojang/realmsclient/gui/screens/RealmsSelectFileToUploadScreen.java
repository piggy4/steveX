package com.mojang.realmsclient.gui.screens;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.task.RealmCreationTask;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.realms.RealmsScreen;
import net.minecraft.world.level.storage.LevelSummary;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class RealmsSelectFileToUploadScreen extends RealmsScreen {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final Component TITLE = Component.translatable("mco.upload.select.world.title");
	private static final Component UNABLE_TO_LOAD_WORLD = Component.translatable("selectWorld.unable_to_load");
	private final @Nullable RealmCreationTask realmCreationTask;
	private final RealmsResetWorldScreen lastScreen;
	private final long realmId;
	private final int slotId;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 8 + 9 + 8 + 20 + 4, 33);
	protected @Nullable EditBox searchBox;
	private @Nullable WorldSelectionList list;
	private @Nullable Button uploadButton;

	public RealmsSelectFileToUploadScreen(@Nullable RealmCreationTask realmCreationTask, long l, int i, RealmsResetWorldScreen realmsResetWorldScreen) {
		super(TITLE);
		this.realmCreationTask = realmCreationTask;
		this.lastScreen = realmsResetWorldScreen;
		this.realmId = l;
		this.slotId = i;
	}

	@Override
	public void init() {
		LinearLayout linearLayout = this.layout.addToHeader(LinearLayout.vertical().spacing(4));
		linearLayout.defaultCellSetting().alignHorizontallyCenter();
		linearLayout.addChild(new StringWidget(this.title, this.font));
		this.searchBox = linearLayout.addChild(
			new EditBox(this.font, this.width / 2 - 100, 22, 200, 20, this.searchBox, Component.translatable("selectWorld.search"))
		);
		this.searchBox.setResponder(string -> {
			if (this.list != null) {
				this.list.updateFilter(string);
			}
		});

		try {
			this.list = this.layout
				.addToContents(
					new WorldSelectionList.Builder(this.minecraft, this)
						.width(this.width)
						.height(this.layout.getContentHeight())
						.filter(this.searchBox.getValue())
						.oldList(this.list)
						.uploadWorld()
						.onEntrySelect(this::updateButtonState)
						.onEntryInteract(this::upload)
						.build()
				);
		} catch (Exception exception) {
			LOGGER.error("Couldn't load level list", exception);
			this.minecraft.setScreen(new RealmsGenericErrorScreen(UNABLE_TO_LOAD_WORLD, Component.nullToEmpty(exception.getMessage()), this.lastScreen));
			return;
		}

		LinearLayout linearLayout2 = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		linearLayout2.defaultCellSetting().alignHorizontallyCenter();
		this.uploadButton = linearLayout2.addChild(
			Button.builder(Component.translatable("mco.upload.button.name"), button -> this.list.getSelectedOpt().ifPresent(this::upload)).build()
		);
		linearLayout2.addChild(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose()).build());
		this.updateButtonState(null);
		this.layout.visitWidgets(guiEventListener -> {
			AbstractWidget var10000 = this.addRenderableWidget(guiEventListener);
		});
		this.repositionElements();
	}

	@Override
	protected void repositionElements() {
		if (this.list != null) {
			this.list.updateSize(this.width, this.layout);
		}

		this.layout.arrangeElements();
	}

	@Override
	protected void setInitialFocus() {
		this.setInitialFocus(this.searchBox);
	}

	private void updateButtonState(@Nullable LevelSummary levelSummary) {
		if (this.list != null && this.uploadButton != null) {
			this.uploadButton.active = this.list.getSelected() != null;
		}
	}

	private void upload(WorldSelectionList.WorldListEntry worldListEntry) {
		this.minecraft.setScreen(new RealmsUploadScreen(this.realmCreationTask, this.realmId, this.slotId, this.lastScreen, worldListEntry.getLevelSummary()));
	}

	@Override
	public Component getNarrationMessage() {
		return CommonComponents.joinForNarration(this.getTitle(), this.createLabelNarration());
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.lastScreen);
	}
}
