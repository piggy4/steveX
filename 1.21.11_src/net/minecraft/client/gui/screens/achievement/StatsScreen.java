package net.minecraft.client.gui.screens.achievement;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.ItemDisplayWidget;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.LoadingTab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class StatsScreen extends Screen {
	private static final Component TITLE = Component.translatable("gui.stats");
	static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
	static final Identifier HEADER_SPRITE = Identifier.withDefaultNamespace("statistics/header");
	static final Identifier SORT_UP_SPRITE = Identifier.withDefaultNamespace("statistics/sort_up");
	static final Identifier SORT_DOWN_SPRITE = Identifier.withDefaultNamespace("statistics/sort_down");
	private static final Component PENDING_TEXT = Component.translatable("multiplayer.downloadingStats");
	static final Component NO_VALUE_DISPLAY = Component.translatable("stats.none");
	private static final Component GENERAL_BUTTON = Component.translatable("stat.generalButton");
	private static final Component ITEMS_BUTTON = Component.translatable("stat.itemsButton");
	private static final Component MOBS_BUTTON = Component.translatable("stat.mobsButton");
	protected final Screen lastScreen;
	private static final int LIST_WIDTH = 280;
	final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private final TabManager tabManager = new TabManager(guiEventListener -> {
		AbstractWidget var10000 = this.addRenderableWidget(guiEventListener);
	}, guiEventListener -> this.removeWidget(guiEventListener));
	private @Nullable TabNavigationBar tabNavigationBar;
	final StatsCounter stats;
	private boolean isLoading = true;

	public StatsScreen(Screen screen, StatsCounter statsCounter) {
		super(TITLE);
		this.lastScreen = screen;
		this.stats = statsCounter;
	}

	@Override
	protected void init() {
		Component component = PENDING_TEXT;
		this.tabNavigationBar = TabNavigationBar.builder(this.tabManager, this.width)
			.addTabs(
				new LoadingTab(this.getFont(), GENERAL_BUTTON, component),
				new LoadingTab(this.getFont(), ITEMS_BUTTON, component),
				new LoadingTab(this.getFont(), MOBS_BUTTON, component)
			)
			.build();
		this.addRenderableWidget(this.tabNavigationBar);
		this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(200).build());
		this.tabNavigationBar.setTabActiveState(0, true);
		this.tabNavigationBar.setTabActiveState(1, false);
		this.tabNavigationBar.setTabActiveState(2, false);
		this.layout.visitWidgets(abstractWidget -> {
			abstractWidget.setTabOrderGroup(1);
			this.addRenderableWidget(abstractWidget);
		});
		this.tabNavigationBar.selectTab(0, false);
		this.repositionElements();
		this.minecraft.getConnection().send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
	}

	public void onStatsUpdated() {
		if (this.isLoading) {
			if (this.tabNavigationBar != null) {
				this.removeWidget(this.tabNavigationBar);
			}

			this.tabNavigationBar = TabNavigationBar.builder(this.tabManager, this.width)
				.addTabs(
					new StatsScreen.StatisticsTab(GENERAL_BUTTON, new StatsScreen.GeneralStatisticsList(this.minecraft)),
					new StatsScreen.StatisticsTab(ITEMS_BUTTON, new StatsScreen.ItemStatisticsList(this.minecraft)),
					new StatsScreen.StatisticsTab(MOBS_BUTTON, new StatsScreen.MobsStatisticsList(this.minecraft))
				)
				.build();
			this.setFocused(this.tabNavigationBar);
			this.addRenderableWidget(this.tabNavigationBar);
			this.setTabActiveStateAndTooltip(1);
			this.setTabActiveStateAndTooltip(2);
			this.tabNavigationBar.selectTab(0, false);
			this.repositionElements();
			this.isLoading = false;
		}
	}

	private void setTabActiveStateAndTooltip(int i) {
		if (this.tabNavigationBar != null) {
			boolean bl = this.tabNavigationBar.getTabs().get(i) instanceof StatsScreen.StatisticsTab statisticsTab && !statisticsTab.list.children().isEmpty();
			this.tabNavigationBar.setTabActiveState(i, bl);
			if (bl) {
				this.tabNavigationBar.setTabTooltip(i, null);
			} else {
				this.tabNavigationBar.setTabTooltip(i, Tooltip.create(Component.translatable("gui.stats.none_found")));
			}
		}
	}

	@Override
	protected void repositionElements() {
		if (this.tabNavigationBar != null) {
			this.tabNavigationBar.setWidth(this.width);
			this.tabNavigationBar.arrangeElements();
			int i = this.tabNavigationBar.getRectangle().bottom();
			ScreenRectangle screenRectangle = new ScreenRectangle(0, i, this.width, this.height - this.layout.getFooterHeight() - i);
			this.tabNavigationBar.getTabs().forEach(tab -> tab.visitChildren(abstractWidget -> abstractWidget.setHeight(screenRectangle.height())));
			this.tabManager.setTabArea(screenRectangle);
			this.layout.setHeaderHeight(i);
			this.layout.arrangeElements();
		}
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		return this.tabNavigationBar != null && this.tabNavigationBar.keyPressed(keyEvent) ? true : super.keyPressed(keyEvent);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, Screen.FOOTER_SEPARATOR, 0, this.height - this.layout.getFooterHeight(), 0.0F, 0.0F, this.width, 2, 32, 2);
	}

	@Override
	protected void renderMenuBackground(GuiGraphics guiGraphics) {
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, CreateWorldScreen.TAB_HEADER_BACKGROUND, 0, 0, 0.0F, 0.0F, this.width, this.layout.getHeaderHeight(), 16, 16);
		this.renderMenuBackground(guiGraphics, 0, this.layout.getHeaderHeight(), this.width, this.height);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.lastScreen);
	}

	static String getTranslationKey(Stat<Identifier> stat) {
		return "stat." + stat.getValue().toString().replace(':', '.');
	}

	@Environment(EnvType.CLIENT)
	class GeneralStatisticsList extends ObjectSelectionList<StatsScreen.GeneralStatisticsList.Entry> {
		public GeneralStatisticsList(final Minecraft minecraft) {
			super(minecraft, StatsScreen.this.width, StatsScreen.this.layout.getContentHeight(), 33, 14);
			ObjectArrayList<Stat<Identifier>> objectArrayList = new ObjectArrayList<>(Stats.CUSTOM.iterator());
			objectArrayList.sort(Comparator.comparing(statx -> I18n.get(StatsScreen.getTranslationKey(statx))));

			for (Stat<Identifier> stat : objectArrayList) {
				this.addEntry(new StatsScreen.GeneralStatisticsList.Entry(stat));
			}
		}

		@Override
		public int getRowWidth() {
			return 280;
		}

		@Override
		protected void renderListBackground(GuiGraphics guiGraphics) {
		}

		@Override
		protected void renderListSeparators(GuiGraphics guiGraphics) {
		}

		@Environment(EnvType.CLIENT)
		class Entry extends ObjectSelectionList.Entry<StatsScreen.GeneralStatisticsList.Entry> {
			private final Stat<Identifier> stat;
			private final Component statDisplay;

			Entry(final Stat<Identifier> stat) {
				this.stat = stat;
				this.statDisplay = Component.translatable(StatsScreen.getTranslationKey(stat));
			}

			private String getValueText() {
				return this.stat.format(StatsScreen.this.stats.getValue(this.stat));
			}

			@Override
			public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
				int k = this.getContentYMiddle() - 9 / 2;
				int l = GeneralStatisticsList.this.children().indexOf(this);
				int m = l % 2 == 0 ? -1 : -4539718;
				guiGraphics.drawString(StatsScreen.this.font, this.statDisplay, this.getContentX() + 2, k, m);
				String string = this.getValueText();
				guiGraphics.drawString(StatsScreen.this.font, string, this.getContentRight() - StatsScreen.this.font.width(string) - 4, k, m);
			}

			@Override
			public Component getNarration() {
				return Component.translatable("narrator.select", Component.empty().append(this.statDisplay).append(CommonComponents.SPACE).append(this.getValueText()));
			}
		}
	}

	@Environment(EnvType.CLIENT)
	class ItemStatisticsList extends ContainerObjectSelectionList<StatsScreen.ItemStatisticsList.Entry> {
		private static final int SLOT_BG_SIZE = 18;
		private static final int SLOT_STAT_HEIGHT = 22;
		private static final int SLOT_BG_Y = 1;
		private static final int SORT_NONE = 0;
		private static final int SORT_DOWN = -1;
		private static final int SORT_UP = 1;
		protected final List<StatType<Block>> blockColumns;
		protected final List<StatType<Item>> itemColumns;
		protected final Comparator<StatsScreen.ItemStatisticsList.ItemRow> itemStatSorter = new StatsScreen.ItemStatisticsList.ItemRowComparator();
		protected @Nullable StatType<?> sortColumn;
		protected int sortOrder;

		public ItemStatisticsList(final Minecraft minecraft) {
			super(minecraft, StatsScreen.this.width, StatsScreen.this.layout.getContentHeight(), 33, 22);
			this.blockColumns = Lists.newArrayList();
			this.blockColumns.add(Stats.BLOCK_MINED);
			this.itemColumns = Lists.newArrayList(Stats.ITEM_BROKEN, Stats.ITEM_CRAFTED, Stats.ITEM_USED, Stats.ITEM_PICKED_UP, Stats.ITEM_DROPPED);
			Set<Item> set = Sets.newIdentityHashSet();

			for (Item item : BuiltInRegistries.ITEM) {
				boolean bl = false;

				for (StatType<Item> statType : this.itemColumns) {
					if (statType.contains(item) && StatsScreen.this.stats.getValue(statType.get(item)) > 0) {
						bl = true;
					}
				}

				if (bl) {
					set.add(item);
				}
			}

			for (Block block : BuiltInRegistries.BLOCK) {
				boolean bl = false;

				for (StatType<Block> statType : this.blockColumns) {
					if (statType.contains(block) && StatsScreen.this.stats.getValue(statType.get(block)) > 0) {
						bl = true;
					}
				}

				if (bl) {
					set.add(block.asItem());
				}
			}

			set.remove(Items.AIR);
			if (!set.isEmpty()) {
				this.addEntry(new StatsScreen.ItemStatisticsList.HeaderEntry());

				for (Item item : set) {
					this.addEntry(new StatsScreen.ItemStatisticsList.ItemRow(item));
				}
			}
		}

		@Override
		protected void renderListBackground(GuiGraphics guiGraphics) {
		}

		int getColumnX(int i) {
			return 75 + 40 * i;
		}

		@Override
		public int getRowWidth() {
			return 280;
		}

		StatType<?> getColumn(int i) {
			return i < this.blockColumns.size() ? this.blockColumns.get(i) : this.itemColumns.get(i - this.blockColumns.size());
		}

		int getColumnIndex(StatType<?> statType) {
			int i = this.blockColumns.indexOf(statType);
			if (i >= 0) {
				return i;
			}

			int j = this.itemColumns.indexOf(statType);
			return j >= 0 ? j + this.blockColumns.size() : -1;
		}

		protected void sortByColumn(StatType<?> statType) {
			if (statType != this.sortColumn) {
				this.sortColumn = statType;
				this.sortOrder = -1;
			} else if (this.sortOrder == -1) {
				this.sortOrder = 1;
			} else {
				this.sortColumn = null;
				this.sortOrder = 0;
			}

			this.sortItems(this.itemStatSorter);
		}

		protected void sortItems(Comparator<StatsScreen.ItemStatisticsList.ItemRow> comparator) {
			List<StatsScreen.ItemStatisticsList.ItemRow> list = this.getItemRows();
			list.sort(comparator);
			this.clearEntriesExcept(this.children().getFirst());

			for (StatsScreen.ItemStatisticsList.ItemRow itemRow : list) {
				this.addEntry(itemRow);
			}
		}

		private List<StatsScreen.ItemStatisticsList.ItemRow> getItemRows() {
			List<StatsScreen.ItemStatisticsList.ItemRow> list = new ArrayList<>();
			this.children().forEach(entry -> {
				if (entry instanceof StatsScreen.ItemStatisticsList.ItemRow itemRow) {
					list.add(itemRow);
				}
			});
			return list;
		}

		@Override
		protected void renderListSeparators(GuiGraphics guiGraphics) {
		}

		@Environment(EnvType.CLIENT)
		abstract static class Entry extends ContainerObjectSelectionList.Entry<StatsScreen.ItemStatisticsList.Entry> {
		}

		@Environment(EnvType.CLIENT)
		class HeaderEntry extends StatsScreen.ItemStatisticsList.Entry {
			private static final Identifier BLOCK_MINED_SPRITE = Identifier.withDefaultNamespace("statistics/block_mined");
			private static final Identifier ITEM_BROKEN_SPRITE = Identifier.withDefaultNamespace("statistics/item_broken");
			private static final Identifier ITEM_CRAFTED_SPRITE = Identifier.withDefaultNamespace("statistics/item_crafted");
			private static final Identifier ITEM_USED_SPRITE = Identifier.withDefaultNamespace("statistics/item_used");
			private static final Identifier ITEM_PICKED_UP_SPRITE = Identifier.withDefaultNamespace("statistics/item_picked_up");
			private static final Identifier ITEM_DROPPED_SPRITE = Identifier.withDefaultNamespace("statistics/item_dropped");
			private final StatsScreen.ItemStatisticsList.HeaderEntry.StatSortButton blockMined;
			private final StatsScreen.ItemStatisticsList.HeaderEntry.StatSortButton itemBroken;
			private final StatsScreen.ItemStatisticsList.HeaderEntry.StatSortButton itemCrafted;
			private final StatsScreen.ItemStatisticsList.HeaderEntry.StatSortButton itemUsed;
			private final StatsScreen.ItemStatisticsList.HeaderEntry.StatSortButton itemPickedUp;
			private final StatsScreen.ItemStatisticsList.HeaderEntry.StatSortButton itemDropped;
			private final List<AbstractWidget> children = new ArrayList<>();

			HeaderEntry() {
				this.blockMined = new StatsScreen.ItemStatisticsList.HeaderEntry.StatSortButton(0, BLOCK_MINED_SPRITE);
				this.itemBroken = new StatsScreen.ItemStatisticsList.HeaderEntry.StatSortButton(1, ITEM_BROKEN_SPRITE);
				this.itemCrafted = new StatsScreen.ItemStatisticsList.HeaderEntry.StatSortButton(2, ITEM_CRAFTED_SPRITE);
				this.itemUsed = new StatsScreen.ItemStatisticsList.HeaderEntry.StatSortButton(3, ITEM_USED_SPRITE);
				this.itemPickedUp = new StatsScreen.ItemStatisticsList.HeaderEntry.StatSortButton(4, ITEM_PICKED_UP_SPRITE);
				this.itemDropped = new StatsScreen.ItemStatisticsList.HeaderEntry.StatSortButton(5, ITEM_DROPPED_SPRITE);
				this.children.addAll(List.of(this.blockMined, this.itemBroken, this.itemCrafted, this.itemUsed, this.itemPickedUp, this.itemDropped));
			}

			@Override
			public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
				this.blockMined.setPosition(this.getContentX() + ItemStatisticsList.this.getColumnX(0) - 18, this.getContentY() + 1);
				this.blockMined.render(guiGraphics, i, j, f);
				this.itemBroken.setPosition(this.getContentX() + ItemStatisticsList.this.getColumnX(1) - 18, this.getContentY() + 1);
				this.itemBroken.render(guiGraphics, i, j, f);
				this.itemCrafted.setPosition(this.getContentX() + ItemStatisticsList.this.getColumnX(2) - 18, this.getContentY() + 1);
				this.itemCrafted.render(guiGraphics, i, j, f);
				this.itemUsed.setPosition(this.getContentX() + ItemStatisticsList.this.getColumnX(3) - 18, this.getContentY() + 1);
				this.itemUsed.render(guiGraphics, i, j, f);
				this.itemPickedUp.setPosition(this.getContentX() + ItemStatisticsList.this.getColumnX(4) - 18, this.getContentY() + 1);
				this.itemPickedUp.render(guiGraphics, i, j, f);
				this.itemDropped.setPosition(this.getContentX() + ItemStatisticsList.this.getColumnX(5) - 18, this.getContentY() + 1);
				this.itemDropped.render(guiGraphics, i, j, f);
				if (ItemStatisticsList.this.sortColumn != null) {
					int k = ItemStatisticsList.this.getColumnX(ItemStatisticsList.this.getColumnIndex(ItemStatisticsList.this.sortColumn)) - 36;
					Identifier identifier = ItemStatisticsList.this.sortOrder == 1 ? StatsScreen.SORT_UP_SPRITE : StatsScreen.SORT_DOWN_SPRITE;
					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, identifier, this.getContentX() + k, this.getContentY() + 1, 18, 18);
				}
			}

			@Override
			public List<? extends GuiEventListener> children() {
				return this.children;
			}

			@Override
			public List<? extends NarratableEntry> narratables() {
				return this.children;
			}

			@Environment(EnvType.CLIENT)
			class StatSortButton extends ImageButton {
				private final Identifier sprite;

				StatSortButton(final int i, final Identifier identifier) {
					super(
						18,
						18,
						new WidgetSprites(StatsScreen.HEADER_SPRITE, StatsScreen.SLOT_SPRITE),
						button -> ItemStatisticsList.this.sortByColumn(ItemStatisticsList.this.getColumn(i)),
						ItemStatisticsList.this.getColumn(i).getDisplayName()
					);
					this.sprite = identifier;
					this.setTooltip(Tooltip.create(this.getMessage()));
				}

				@Override
				public void renderContents(GuiGraphics guiGraphics, int i, int j, float f) {
					Identifier identifier = this.sprites.get(this.isActive(), this.isHoveredOrFocused());
					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, identifier, this.getX(), this.getY(), this.width, this.height);
					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.sprite, this.getX(), this.getY(), this.width, this.height);
				}
			}
		}

		@Environment(EnvType.CLIENT)
		class ItemRow extends StatsScreen.ItemStatisticsList.Entry {
			private final Item item;
			private final StatsScreen.ItemStatisticsList.ItemRow.ItemRowWidget itemRowWidget;

			ItemRow(final Item item) {
				this.item = item;
				this.itemRowWidget = new StatsScreen.ItemStatisticsList.ItemRow.ItemRowWidget(item.getDefaultInstance());
			}

			protected Item getItem() {
				return this.item;
			}

			@Override
			public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
				this.itemRowWidget.setPosition(this.getContentX(), this.getContentY());
				this.itemRowWidget.render(guiGraphics, i, j, f);
				StatsScreen.ItemStatisticsList itemStatisticsList = ItemStatisticsList.this;
				int k = itemStatisticsList.children().indexOf(this);

				for (int l = 0; l < itemStatisticsList.blockColumns.size(); l++) {
					Stat<Block> stat;
					if (this.item instanceof BlockItem blockItem) {
						stat = itemStatisticsList.blockColumns.get(l).get(blockItem.getBlock());
					} else {
						stat = null;
					}

					this.renderStat(guiGraphics, stat, this.getContentX() + ItemStatisticsList.this.getColumnX(l), this.getContentYMiddle() - 9 / 2, k % 2 == 0);
				}

				for (int l = 0; l < itemStatisticsList.itemColumns.size(); l++) {
					this.renderStat(
						guiGraphics,
						itemStatisticsList.itemColumns.get(l).get(this.item),
						this.getContentX() + ItemStatisticsList.this.getColumnX(l + itemStatisticsList.blockColumns.size()),
						this.getContentYMiddle() - 9 / 2,
						k % 2 == 0
					);
				}
			}

			protected void renderStat(GuiGraphics guiGraphics, @Nullable Stat<?> stat, int i, int j, boolean bl) {
				Component component = stat == null ? StatsScreen.NO_VALUE_DISPLAY : Component.literal(stat.format(StatsScreen.this.stats.getValue(stat)));
				guiGraphics.drawString(StatsScreen.this.font, component, i - StatsScreen.this.font.width(component), j, bl ? -1 : -4539718);
			}

			@Override
			public List<? extends NarratableEntry> narratables() {
				return List.of(this.itemRowWidget);
			}

			@Override
			public List<? extends GuiEventListener> children() {
				return List.of(this.itemRowWidget);
			}

			@Environment(EnvType.CLIENT)
			class ItemRowWidget extends ItemDisplayWidget {
				ItemRowWidget(final ItemStack itemStack) {
					super(ItemStatisticsList.this.minecraft, 1, 1, 18, 18, itemStack.getHoverName(), itemStack, false, true);
				}

				@Override
				protected void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, StatsScreen.SLOT_SPRITE, ItemRow.this.getContentX(), ItemRow.this.getContentY(), 18, 18);
					super.renderWidget(guiGraphics, i, j, f);
				}

				@Override
				protected void renderTooltip(GuiGraphics guiGraphics, int i, int j) {
					super.renderTooltip(guiGraphics, ItemRow.this.getContentX() + 18, ItemRow.this.getContentY() + 18);
				}
			}
		}

		@Environment(EnvType.CLIENT)
		class ItemRowComparator implements Comparator<StatsScreen.ItemStatisticsList.ItemRow> {
			public int compare(StatsScreen.ItemStatisticsList.ItemRow itemRow, StatsScreen.ItemStatisticsList.ItemRow itemRow2) {
				Item item = itemRow.getItem();
				Item item2 = itemRow2.getItem();
				int i;
				int j;
				if (ItemStatisticsList.this.sortColumn == null) {
					i = 0;
					j = 0;
				} else if (ItemStatisticsList.this.blockColumns.contains(ItemStatisticsList.this.sortColumn)) {
					StatType<Block> statType = (StatType<Block>)ItemStatisticsList.this.sortColumn;
					i = item instanceof BlockItem ? StatsScreen.this.stats.getValue(statType, ((BlockItem)item).getBlock()) : -1;
					j = item2 instanceof BlockItem ? StatsScreen.this.stats.getValue(statType, ((BlockItem)item2).getBlock()) : -1;
				} else {
					StatType<Item> statType = (StatType<Item>)ItemStatisticsList.this.sortColumn;
					i = StatsScreen.this.stats.getValue(statType, item);
					j = StatsScreen.this.stats.getValue(statType, item2);
				}

				return i == j
					? ItemStatisticsList.this.sortOrder * Integer.compare(Item.getId(item), Item.getId(item2))
					: ItemStatisticsList.this.sortOrder * Integer.compare(i, j);
			}
		}
	}

	@Environment(EnvType.CLIENT)
	class MobsStatisticsList extends ObjectSelectionList<StatsScreen.MobsStatisticsList.MobRow> {
		public MobsStatisticsList(final Minecraft minecraft) {
			super(minecraft, StatsScreen.this.width, StatsScreen.this.layout.getContentHeight(), 33, 9 * 4);

			for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
				if (StatsScreen.this.stats.getValue(Stats.ENTITY_KILLED.get(entityType)) > 0 || StatsScreen.this.stats.getValue(Stats.ENTITY_KILLED_BY.get(entityType)) > 0
					)
				 {
					this.addEntry(new StatsScreen.MobsStatisticsList.MobRow(entityType));
				}
			}
		}

		@Override
		public int getRowWidth() {
			return 280;
		}

		@Override
		protected void renderListBackground(GuiGraphics guiGraphics) {
		}

		@Override
		protected void renderListSeparators(GuiGraphics guiGraphics) {
		}

		@Environment(EnvType.CLIENT)
		class MobRow extends ObjectSelectionList.Entry<StatsScreen.MobsStatisticsList.MobRow> {
			private final Component mobName;
			private final Component kills;
			private final Component killedBy;
			private final boolean hasKills;
			private final boolean wasKilledBy;

			public MobRow(final EntityType<?> entityType) {
				this.mobName = entityType.getDescription();
				int i = StatsScreen.this.stats.getValue(Stats.ENTITY_KILLED.get(entityType));
				if (i == 0) {
					this.kills = Component.translatable("stat_type.minecraft.killed.none", this.mobName);
					this.hasKills = false;
				} else {
					this.kills = Component.translatable("stat_type.minecraft.killed", i, this.mobName);
					this.hasKills = true;
				}

				int j = StatsScreen.this.stats.getValue(Stats.ENTITY_KILLED_BY.get(entityType));
				if (j == 0) {
					this.killedBy = Component.translatable("stat_type.minecraft.killed_by.none", this.mobName);
					this.wasKilledBy = false;
				} else {
					this.killedBy = Component.translatable("stat_type.minecraft.killed_by", this.mobName, j);
					this.wasKilledBy = true;
				}
			}

			@Override
			public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
				guiGraphics.drawString(StatsScreen.this.font, this.mobName, this.getContentX() + 2, this.getContentY() + 1, -1);
				guiGraphics.drawString(StatsScreen.this.font, this.kills, this.getContentX() + 2 + 10, this.getContentY() + 1 + 9, this.hasKills ? -4539718 : -8355712);
				guiGraphics.drawString(
					StatsScreen.this.font, this.killedBy, this.getContentX() + 2 + 10, this.getContentY() + 1 + 9 * 2, this.wasKilledBy ? -4539718 : -8355712
				);
			}

			@Override
			public Component getNarration() {
				return Component.translatable("narrator.select", CommonComponents.joinForNarration(this.kills, this.killedBy));
			}
		}
	}

	@Environment(EnvType.CLIENT)
	class StatisticsTab extends GridLayoutTab {
		protected final AbstractSelectionList<?> list;

		public StatisticsTab(final Component component, final AbstractSelectionList<?> abstractSelectionList) {
			super(component);
			this.layout.addChild(abstractSelectionList, 1, 1);
			this.list = abstractSelectionList;
		}

		@Override
		public void doLayout(ScreenRectangle screenRectangle) {
			this.list.updateSizeAndPosition(StatsScreen.this.width, StatsScreen.this.layout.getContentHeight(), StatsScreen.this.layout.getHeaderHeight());
			super.doLayout(screenRectangle);
		}
	}
}
