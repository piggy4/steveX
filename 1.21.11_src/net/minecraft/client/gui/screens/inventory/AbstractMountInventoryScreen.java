package net.minecraft.client.gui.screens.inventory;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractMountInventoryMenu;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class AbstractMountInventoryScreen<T extends AbstractMountInventoryMenu> extends AbstractContainerScreen<T> {
	protected final int inventoryColumns;
	protected float xMouse;
	protected float yMouse;
	protected LivingEntity mount;

	public AbstractMountInventoryScreen(T abstractMountInventoryMenu, Inventory inventory, Component component, int i, LivingEntity livingEntity) {
		super(abstractMountInventoryMenu, inventory, component);
		this.inventoryColumns = i;
		this.mount = livingEntity;
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float f, int i, int j) {
		int k = (this.width - this.imageWidth) / 2;
		int l = (this.height - this.imageHeight) / 2;
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, this.getBackgroundTextureLocation(), k, l, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
		if (this.inventoryColumns > 0 && this.getChestSlotsSpriteLocation() != null) {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.getChestSlotsSpriteLocation(), 90, 54, 0, 0, k + 79, l + 17, this.inventoryColumns * 18, 54);
		}

		if (this.shouldRenderSaddleSlot()) {
			this.drawSlot(guiGraphics, k + 7, l + 35 - 18);
		}

		if (this.shouldRenderArmorSlot()) {
			this.drawSlot(guiGraphics, k + 7, l + 35);
		}

		InventoryScreen.renderEntityInInventoryFollowsMouse(guiGraphics, k + 26, l + 18, k + 78, l + 70, 17, 0.25F, this.xMouse, this.yMouse, this.mount);
	}

	protected void drawSlot(GuiGraphics guiGraphics, int i, int j) {
		guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.getSlotSpriteLocation(), i, j, 18, 18);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		this.xMouse = i;
		this.yMouse = j;
		super.render(guiGraphics, i, j, f);
		this.renderTooltip(guiGraphics, i, j);
	}

	protected abstract Identifier getBackgroundTextureLocation();

	protected abstract Identifier getSlotSpriteLocation();

	protected abstract @Nullable Identifier getChestSlotsSpriteLocation();

	protected abstract boolean shouldRenderSaddleSlot();

	protected abstract boolean shouldRenderArmorSlot();
}
