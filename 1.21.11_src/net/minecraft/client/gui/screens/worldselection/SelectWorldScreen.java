package net.minecraft.client.gui.screens.worldselection;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FileUtil;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelSummary;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class SelectWorldScreen extends Screen {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final WorldOptions TEST_OPTIONS = new WorldOptions("test1".hashCode(), true, false);
	protected final Screen lastScreen;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 8 + 9 + 8 + 20 + 4, 60);
	private @Nullable Button deleteButton;
	private @Nullable Button selectButton;
	private @Nullable Button renameButton;
	private @Nullable Button copyButton;
	protected @Nullable EditBox searchBox;
	private @Nullable WorldSelectionList list;

	public SelectWorldScreen(Screen screen) {
		super(Component.translatable("selectWorld.title"));
		this.lastScreen = screen;
	}

	@Override
	protected void init() {
		LinearLayout linearLayout = this.layout.addToHeader(LinearLayout.vertical().spacing(4));
		linearLayout.defaultCellSetting().alignHorizontallyCenter();
		linearLayout.addChild(new StringWidget(this.title, this.font));
		LinearLayout linearLayout2 = linearLayout.addChild(LinearLayout.horizontal().spacing(4));
		if (SharedConstants.DEBUG_WORLD_RECREATE) {
			linearLayout2.addChild(this.createDebugWorldRecreateButton());
		}

		this.searchBox = linearLayout2.addChild(
			new EditBox(this.font, this.width / 2 - 100, 22, 200, 20, this.searchBox, Component.translatable("selectWorld.search"))
		);
		this.searchBox.setResponder(string -> {
			if (this.list != null) {
				this.list.updateFilter(string);
			}
		});
		this.searchBox.setHint(Component.translatable("gui.selectWorld.search").setStyle(EditBox.SEARCH_HINT_STYLE));
		Consumer<WorldSelectionList.WorldListEntry> consumer = WorldSelectionList.WorldListEntry::joinWorld;
		this.list = this.layout
			.addToContents(
				new WorldSelectionList.Builder(this.minecraft, this)
					.width(this.width)
					.height(this.layout.getContentHeight())
					.filter(this.searchBox.getValue())
					.oldList(this.list)
					.onEntrySelect(this::updateButtonStatus)
					.onEntryInteract(consumer)
					.build()
			);
		this.createFooterButtons(consumer, this.list);
		this.layout.visitWidgets(guiEventListener -> {
			AbstractWidget var10000 = this.addRenderableWidget(guiEventListener);
		});
		this.repositionElements();
		this.updateButtonStatus(null);
	}

	private void createFooterButtons(Consumer<WorldSelectionList.WorldListEntry> consumer, WorldSelectionList worldSelectionList) {
		GridLayout gridLayout = this.layout.addToFooter(new GridLayout().columnSpacing(8).rowSpacing(4));
		gridLayout.defaultCellSetting().alignHorizontallyCenter();
		GridLayout.RowHelper rowHelper = gridLayout.createRowHelper(4);
		this.selectButton = rowHelper.addChild(Button.builder(LevelSummary.PLAY_WORLD, button -> worldSelectionList.getSelectedOpt().ifPresent(consumer)).build(), 2);
		rowHelper.addChild(
			Button.builder(Component.translatable("selectWorld.create"), button -> CreateWorldScreen.openFresh(this.minecraft, worldSelectionList::returnToScreen))
				.build(),
			2
		);
		this.renameButton = rowHelper.addChild(
			Button.builder(
					Component.translatable("selectWorld.edit"), button -> worldSelectionList.getSelectedOpt().ifPresent(WorldSelectionList.WorldListEntry::editWorld)
				)
				.width(71)
				.build()
		);
		this.deleteButton = rowHelper.addChild(
			Button.builder(
					Component.translatable("selectWorld.delete"), button -> worldSelectionList.getSelectedOpt().ifPresent(WorldSelectionList.WorldListEntry::deleteWorld)
				)
				.width(71)
				.build()
		);
		this.copyButton = rowHelper.addChild(
			Button.builder(
					Component.translatable("selectWorld.recreate"), button -> worldSelectionList.getSelectedOpt().ifPresent(WorldSelectionList.WorldListEntry::recreateWorld)
				)
				.width(71)
				.build()
		);
		rowHelper.addChild(Button.builder(CommonComponents.GUI_BACK, button -> this.minecraft.setScreen(this.lastScreen)).width(71).build());
	}

	private Button createDebugWorldRecreateButton() {
		return Button.builder(
				Component.literal("DEBUG recreate"),
				button -> {
					try {
						String string = "DEBUG world";
						if (this.list != null && !this.list.children().isEmpty()) {
							WorldSelectionList.Entry entry = this.list.children().getFirst();
							if (entry instanceof WorldSelectionList.WorldListEntry worldListEntry && worldListEntry.getLevelName().equals("DEBUG world")) {
								worldListEntry.doDeleteWorld();
							}
						}

						LevelSettings levelSettings = new LevelSettings(
							"DEBUG world",
							GameType.SPECTATOR,
							false,
							Difficulty.NORMAL,
							true,
							new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()),
							WorldDataConfiguration.DEFAULT
						);
						String string2 = FileUtil.findAvailableName(this.minecraft.getLevelSource().getBaseDir(), "DEBUG world", "");
						this.minecraft.createWorldOpenFlows().createFreshLevel(string2, levelSettings, TEST_OPTIONS, WorldPresets::createNormalWorldDimensions, this);
					} catch (IOException iOException) {
						LOGGER.error("Failed to recreate the debug world", iOException);
					}
				}
			)
			.width(72)
			.build();
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
		if (this.searchBox != null) {
			this.setInitialFocus(this.searchBox);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.lastScreen);
	}

	public void updateButtonStatus(@Nullable LevelSummary levelSummary) {
		if (this.selectButton != null && this.renameButton != null && this.copyButton != null && this.deleteButton != null) {
			if (levelSummary == null) {
				this.selectButton.setMessage(LevelSummary.PLAY_WORLD);
				this.selectButton.active = false;
				this.renameButton.active = false;
				this.copyButton.active = false;
				this.deleteButton.active = false;
			} else {
				this.selectButton.setMessage(levelSummary.primaryActionMessage());
				this.selectButton.active = levelSummary.primaryActionActive();
				this.renameButton.active = levelSummary.canEdit();
				this.copyButton.active = levelSummary.canRecreate();
				this.deleteButton.active = levelSummary.canDelete();
			}
		}
	}

	@Override
	public void removed() {
		if (this.list != null) {
			this.list.children().forEach(WorldSelectionList.Entry::close);
		}
	}
}
