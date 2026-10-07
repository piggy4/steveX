package net.minecraft.client.gui.screens;

import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class CreateFlatWorldScreen extends Screen {
	private static final Component TITLE = Component.translatable("createWorld.customize.flat.title");
	static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
	private static final int SLOT_BG_SIZE = 18;
	private static final int SLOT_STAT_HEIGHT = 20;
	private static final int SLOT_BG_X = 1;
	private static final int SLOT_BG_Y = 1;
	private static final int SLOT_FG_X = 2;
	private static final int SLOT_FG_Y = 2;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 33, 64);
	protected final CreateWorldScreen parent;
	private final Consumer<FlatLevelGeneratorSettings> applySettings;
	FlatLevelGeneratorSettings generator;
	private CreateFlatWorldScreen.@Nullable DetailsList list;
	private @Nullable Button deleteLayerButton;

	public CreateFlatWorldScreen(
		CreateWorldScreen createWorldScreen, Consumer<FlatLevelGeneratorSettings> consumer, FlatLevelGeneratorSettings flatLevelGeneratorSettings
	) {
		super(TITLE);
		this.parent = createWorldScreen;
		this.applySettings = consumer;
		this.generator = flatLevelGeneratorSettings;
	}

	public FlatLevelGeneratorSettings settings() {
		return this.generator;
	}

	public void setConfig(FlatLevelGeneratorSettings flatLevelGeneratorSettings) {
		this.generator = flatLevelGeneratorSettings;
		if (this.list != null) {
			this.list.resetRows();
			this.updateButtonValidity();
		}
	}

	@Override
	protected void init() {
		this.layout.addTitleHeader(this.title, this.font);
		this.list = this.layout.addToContents(new CreateFlatWorldScreen.DetailsList());
		LinearLayout linearLayout = this.layout.addToFooter(LinearLayout.vertical().spacing(4));
		linearLayout.defaultCellSetting().alignVerticallyMiddle();
		LinearLayout linearLayout2 = linearLayout.addChild(LinearLayout.horizontal().spacing(8));
		LinearLayout linearLayout3 = linearLayout.addChild(LinearLayout.horizontal().spacing(8));
		this.deleteLayerButton = linearLayout2.addChild(Button.builder(Component.translatable("createWorld.customize.flat.removeLayer"), button -> {
			if (this.list != null && this.list.getSelected() instanceof CreateFlatWorldScreen.DetailsList.LayerEntry layerEntry) {
				this.list.deleteLayer(layerEntry);
			}
		}).build());
		linearLayout2.addChild(Button.builder(Component.translatable("createWorld.customize.presets"), button -> {
			this.minecraft.setScreen(new PresetFlatWorldScreen(this));
			this.generator.updateLayers();
			this.updateButtonValidity();
		}).build());
		linearLayout3.addChild(Button.builder(CommonComponents.GUI_DONE, button -> {
			this.applySettings.accept(this.generator);
			this.onClose();
			this.generator.updateLayers();
		}).build());
		linearLayout3.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> {
			this.onClose();
			this.generator.updateLayers();
		}).build());
		this.generator.updateLayers();
		this.updateButtonValidity();
		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
	}

	@Override
	protected void repositionElements() {
		if (this.list != null) {
			this.list.updateSize(this.width, this.layout);
		}

		this.layout.arrangeElements();
	}

	void updateButtonValidity() {
		if (this.deleteLayerButton != null) {
			this.deleteLayerButton.active = this.hasValidSelection();
		}
	}

	private boolean hasValidSelection() {
		return this.list != null && this.list.getSelected() instanceof CreateFlatWorldScreen.DetailsList.LayerEntry;
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}

	@Environment(EnvType.CLIENT)
	class DetailsList extends ObjectSelectionList<CreateFlatWorldScreen.DetailsList.Entry> {
		static final Component LAYER_MATERIAL_TITLE = Component.translatable("createWorld.customize.flat.tile").withStyle(ChatFormatting.UNDERLINE);
		static final Component HEIGHT_TITLE = Component.translatable("createWorld.customize.flat.height").withStyle(ChatFormatting.UNDERLINE);

		public DetailsList() {
			super(CreateFlatWorldScreen.this.minecraft, CreateFlatWorldScreen.this.width, CreateFlatWorldScreen.this.height - 103, 43, 24);
			this.populateList();
		}

		private void populateList() {
			this.addEntry(new CreateFlatWorldScreen.DetailsList.HeaderEntry(CreateFlatWorldScreen.this.font), (int)(9.0 * 1.5));
			List<FlatLayerInfo> list = CreateFlatWorldScreen.this.generator.getLayersInfo().reversed();

			for (int i = 0; i < list.size(); i++) {
				this.addEntry(new CreateFlatWorldScreen.DetailsList.LayerEntry(list.get(i), i));
			}
		}

		public void setSelected(CreateFlatWorldScreen.DetailsList.@Nullable Entry entry) {
			super.setSelected(entry);
			CreateFlatWorldScreen.this.updateButtonValidity();
		}

		public void resetRows() {
			int i = this.children().indexOf(this.getSelected());
			this.clearEntries();
			this.populateList();
			List<CreateFlatWorldScreen.DetailsList.Entry> list = this.children();
			if (i >= 0 && i < list.size()) {
				this.setSelected(list.get(i));
			}
		}

		void deleteLayer(CreateFlatWorldScreen.DetailsList.LayerEntry layerEntry) {
			List<FlatLayerInfo> list = CreateFlatWorldScreen.this.generator.getLayersInfo();
			int i = this.children().indexOf(layerEntry);
			this.removeEntry(layerEntry);
			list.remove(layerEntry.layerInfo);
			this.setSelected(list.isEmpty() ? null : this.children().get(Math.min(i, list.size())));
			CreateFlatWorldScreen.this.generator.updateLayers();
			this.resetRows();
			CreateFlatWorldScreen.this.updateButtonValidity();
		}

		@Environment(EnvType.CLIENT)
		abstract static class Entry extends ObjectSelectionList.Entry<CreateFlatWorldScreen.DetailsList.Entry> {
		}

		@Environment(EnvType.CLIENT)
		static class HeaderEntry extends CreateFlatWorldScreen.DetailsList.Entry {
			private final Font font;

			public HeaderEntry(Font font) {
				this.font = font;
			}

			@Override
			public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
				guiGraphics.drawString(this.font, CreateFlatWorldScreen.DetailsList.LAYER_MATERIAL_TITLE, this.getContentX(), this.getContentY(), -1);
				guiGraphics.drawString(
					this.font,
					CreateFlatWorldScreen.DetailsList.HEIGHT_TITLE,
					this.getContentRight() - this.font.width(CreateFlatWorldScreen.DetailsList.HEIGHT_TITLE),
					this.getContentY(),
					-1
				);
			}

			@Override
			public Component getNarration() {
				return CommonComponents.joinForNarration(CreateFlatWorldScreen.DetailsList.LAYER_MATERIAL_TITLE, CreateFlatWorldScreen.DetailsList.HEIGHT_TITLE);
			}
		}

		@Environment(EnvType.CLIENT)
		class LayerEntry extends CreateFlatWorldScreen.DetailsList.Entry {
			final FlatLayerInfo layerInfo;
			private final int index;

			public LayerEntry(final FlatLayerInfo flatLayerInfo, final int i) {
				this.layerInfo = flatLayerInfo;
				this.index = i;
			}

			@Override
			public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
				BlockState blockState = this.layerInfo.getBlockState();
				ItemStack itemStack = this.getDisplayItem(blockState);
				this.blitSlot(guiGraphics, this.getContentX(), this.getContentY(), itemStack);
				int k = this.getContentYMiddle() - 9 / 2;
				guiGraphics.drawString(CreateFlatWorldScreen.this.font, itemStack.getHoverName(), this.getContentX() + 18 + 5, k, -1);
				Component component;
				if (this.index == 0) {
					component = Component.translatable("createWorld.customize.flat.layer.top", this.layerInfo.getHeight());
				} else if (this.index == CreateFlatWorldScreen.this.generator.getLayersInfo().size() - 1) {
					component = Component.translatable("createWorld.customize.flat.layer.bottom", this.layerInfo.getHeight());
				} else {
					component = Component.translatable("createWorld.customize.flat.layer", this.layerInfo.getHeight());
				}

				guiGraphics.drawString(CreateFlatWorldScreen.this.font, component, this.getContentRight() - CreateFlatWorldScreen.this.font.width(component), k, -1);
			}

			private ItemStack getDisplayItem(BlockState blockState) {
				Item item = blockState.getBlock().asItem();
				if (item == Items.AIR) {
					if (blockState.is(Blocks.WATER)) {
						item = Items.WATER_BUCKET;
					} else if (blockState.is(Blocks.LAVA)) {
						item = Items.LAVA_BUCKET;
					}
				}

				return new ItemStack(item);
			}

			@Override
			public Component getNarration() {
				ItemStack itemStack = this.getDisplayItem(this.layerInfo.getBlockState());
				return !itemStack.isEmpty()
					? CommonComponents.joinForNarration(
						Component.translatable("narrator.select", itemStack.getHoverName()),
						CreateFlatWorldScreen.DetailsList.HEIGHT_TITLE,
						Component.literal(String.valueOf(this.layerInfo.getHeight()))
					)
					: CommonComponents.EMPTY;
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
				DetailsList.this.setSelected(this);
				return super.mouseClicked(mouseButtonEvent, bl);
			}

			private void blitSlot(GuiGraphics guiGraphics, int i, int j, ItemStack itemStack) {
				this.blitSlotBg(guiGraphics, i + 1, j + 1);
				if (!itemStack.isEmpty()) {
					guiGraphics.renderFakeItem(itemStack, i + 2, j + 2);
				}
			}

			private void blitSlotBg(GuiGraphics guiGraphics, int i, int j) {
				guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, CreateFlatWorldScreen.SLOT_SPRITE, i, j, 18, 18);
			}
		}
	}
}
