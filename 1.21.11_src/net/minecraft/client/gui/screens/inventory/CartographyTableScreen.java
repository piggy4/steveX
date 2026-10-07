package net.minecraft.client.gui.screens.inventory;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class CartographyTableScreen extends AbstractContainerScreen<CartographyTableMenu> {
	private static final Identifier ERROR_SPRITE = Identifier.withDefaultNamespace("container/cartography_table/error");
	private static final Identifier SCALED_MAP_SPRITE = Identifier.withDefaultNamespace("container/cartography_table/scaled_map");
	private static final Identifier DUPLICATED_MAP_SPRITE = Identifier.withDefaultNamespace("container/cartography_table/duplicated_map");
	private static final Identifier MAP_SPRITE = Identifier.withDefaultNamespace("container/cartography_table/map");
	private static final Identifier LOCKED_SPRITE = Identifier.withDefaultNamespace("container/cartography_table/locked");
	private static final Identifier BG_LOCATION = Identifier.withDefaultNamespace("textures/gui/container/cartography_table.png");
	private final MapRenderState mapRenderState = new MapRenderState();

	public CartographyTableScreen(CartographyTableMenu cartographyTableMenu, Inventory inventory, Component component) {
		super(cartographyTableMenu, inventory, component);
		this.titleLabelY -= 2;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		this.renderTooltip(guiGraphics, i, j);
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float f, int i, int j) {
		int k = this.leftPos;
		int l = this.topPos;
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, BG_LOCATION, k, l, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
		ItemStack itemStack = this.menu.getSlot(1).getItem();
		boolean bl = itemStack.is(Items.MAP);
		boolean bl2 = itemStack.is(Items.PAPER);
		boolean bl3 = itemStack.is(Items.GLASS_PANE);
		ItemStack itemStack2 = this.menu.getSlot(0).getItem();
		MapId mapId = itemStack2.get(DataComponents.MAP_ID);
		boolean bl4 = false;
		MapItemSavedData mapItemSavedData;
		if (mapId != null) {
			mapItemSavedData = MapItem.getSavedData(mapId, this.minecraft.level);
			if (mapItemSavedData != null) {
				if (mapItemSavedData.locked) {
					bl4 = true;
					if (bl2 || bl3) {
						guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, ERROR_SPRITE, k + 35, l + 31, 28, 21);
					}
				}

				if (bl2 && mapItemSavedData.scale >= 4) {
					bl4 = true;
					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, ERROR_SPRITE, k + 35, l + 31, 28, 21);
				}
			}
		} else {
			mapItemSavedData = null;
		}

		this.renderResultingMap(guiGraphics, mapId, mapItemSavedData, bl, bl2, bl3, bl4);
	}

	private void renderResultingMap(
		GuiGraphics guiGraphics, @Nullable MapId mapId, @Nullable MapItemSavedData mapItemSavedData, boolean bl, boolean bl2, boolean bl3, boolean bl4
	) {
		int i = this.leftPos;
		int j = this.topPos;
		if (bl2 && !bl4) {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCALED_MAP_SPRITE, i + 67, j + 13, 66, 66);
			this.renderMap(guiGraphics, mapId, mapItemSavedData, i + 85, j + 31, 0.226F);
		} else if (bl) {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, DUPLICATED_MAP_SPRITE, i + 67 + 16, j + 13, 50, 66);
			this.renderMap(guiGraphics, mapId, mapItemSavedData, i + 86, j + 16, 0.34F);
			guiGraphics.nextStratum();
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, DUPLICATED_MAP_SPRITE, i + 67, j + 13 + 16, 50, 66);
			this.renderMap(guiGraphics, mapId, mapItemSavedData, i + 70, j + 32, 0.34F);
		} else if (bl3) {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, MAP_SPRITE, i + 67, j + 13, 66, 66);
			this.renderMap(guiGraphics, mapId, mapItemSavedData, i + 71, j + 17, 0.45F);
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, LOCKED_SPRITE, i + 118, j + 60, 10, 14);
		} else {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, MAP_SPRITE, i + 67, j + 13, 66, 66);
			this.renderMap(guiGraphics, mapId, mapItemSavedData, i + 71, j + 17, 0.45F);
		}
	}

	private void renderMap(GuiGraphics guiGraphics, @Nullable MapId mapId, @Nullable MapItemSavedData mapItemSavedData, int i, int j, float f) {
		if (mapId != null && mapItemSavedData != null) {
			guiGraphics.pose().pushMatrix();
			guiGraphics.pose().translate(i, j);
			guiGraphics.pose().scale(f, f);
			this.minecraft.getMapRenderer().extractRenderState(mapId, mapItemSavedData, this.mapRenderState);
			guiGraphics.submitMapRenderState(this.mapRenderState);
			guiGraphics.pose().popMatrix();
		}
	}
}
