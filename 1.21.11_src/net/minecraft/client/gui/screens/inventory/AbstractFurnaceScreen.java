package net.minecraft.client.gui.screens.inventory;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.recipebook.FurnaceRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractFurnaceMenu;

@Environment(EnvType.CLIENT)
public abstract class AbstractFurnaceScreen<T extends AbstractFurnaceMenu> extends AbstractRecipeBookScreen<T> {
	private final Identifier texture;
	private final Identifier litProgressSprite;
	private final Identifier burnProgressSprite;

	public AbstractFurnaceScreen(
		T abstractFurnaceMenu,
		Inventory inventory,
		Component component,
		Component component2,
		Identifier identifier,
		Identifier identifier2,
		Identifier identifier3,
		List<RecipeBookComponent.TabInfo> list
	) {
		super(abstractFurnaceMenu, new FurnaceRecipeBookComponent(abstractFurnaceMenu, component2, list), inventory, component);
		this.texture = identifier;
		this.litProgressSprite = identifier2;
		this.burnProgressSprite = identifier3;
	}

	@Override
	public void init() {
		super.init();
		this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
	}

	@Override
	protected ScreenPosition getRecipeBookButtonPosition() {
		return new ScreenPosition(this.leftPos + 20, this.height / 2 - 49);
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float f, int i, int j) {
		int k = this.leftPos;
		int l = this.topPos;
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, this.texture, k, l, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
		if (this.menu.isLit()) {
			int m = 14;
			int n = Mth.ceil(this.menu.getLitProgress() * 13.0F) + 1;
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.litProgressSprite, 14, 14, 0, 14 - n, k + 56, l + 36 + 14 - n, 14, n);
		}

		int m = 24;
		int n = Mth.ceil(this.menu.getBurnProgress() * 24.0F);
		guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.burnProgressSprite, 24, 16, 0, 0, k + 79, l + 34, n, 16);
	}
}
