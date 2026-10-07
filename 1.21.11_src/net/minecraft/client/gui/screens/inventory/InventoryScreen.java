package net.minecraft.client.gui.screens.inventory;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.recipebook.CraftingRecipeBookComponent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class InventoryScreen extends AbstractRecipeBookScreen<InventoryMenu> {
	private float xMouse;
	private float yMouse;
	private boolean buttonClicked;
	private final EffectsInInventory effects;

	public InventoryScreen(Player player) {
		super(player.inventoryMenu, new CraftingRecipeBookComponent(player.inventoryMenu), player.getInventory(), Component.translatable("container.crafting"));
		this.titleLabelX = 97;
		this.effects = new EffectsInInventory(this);
	}

	@Override
	public void containerTick() {
		super.containerTick();
		if (this.minecraft.player.hasInfiniteMaterials()) {
			this.minecraft
				.setScreen(
					new CreativeModeInventoryScreen(this.minecraft.player, this.minecraft.player.connection.enabledFeatures(), this.minecraft.options.operatorItemsTab().get())
				);
		}
	}

	@Override
	protected void init() {
		if (this.minecraft.player.hasInfiniteMaterials()) {
			this.minecraft
				.setScreen(
					new CreativeModeInventoryScreen(this.minecraft.player, this.minecraft.player.connection.enabledFeatures(), this.minecraft.options.operatorItemsTab().get())
				);
		} else {
			super.init();
		}
	}

	@Override
	protected ScreenPosition getRecipeBookButtonPosition() {
		return new ScreenPosition(this.leftPos + 104, this.height / 2 - 22);
	}

	@Override
	protected void onRecipeBookButtonClick() {
		this.buttonClicked = true;
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int i, int j) {
		guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, -12566464, false);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		this.effects.render(guiGraphics, i, j);
		super.render(guiGraphics, i, j, f);
		this.xMouse = i;
		this.yMouse = j;
	}

	@Override
	public boolean showsActiveEffects() {
		return this.effects.canSeeEffects();
	}

	@Override
	protected boolean isBiggerResultSlot() {
		return false;
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float f, int i, int j) {
		int k = this.leftPos;
		int l = this.topPos;
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_LOCATION, k, l, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
		renderEntityInInventoryFollowsMouse(guiGraphics, k + 26, l + 8, k + 75, l + 78, 30, 0.0625F, this.xMouse, this.yMouse, this.minecraft.player);
	}

	public static void renderEntityInInventoryFollowsMouse(
		GuiGraphics guiGraphics, int i, int j, int k, int l, int m, float f, float g, float h, LivingEntity livingEntity
	) {
		float n = (i + k) / 2.0F;
		float o = (j + l) / 2.0F;
		float p = (float)Math.atan((n - g) / 40.0F);
		float q = (float)Math.atan((o - h) / 40.0F);
		Quaternionf quaternionf = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf quaternionf2 = new Quaternionf().rotateX(q * 20.0F * (float) (Math.PI / 180.0));
		quaternionf.mul(quaternionf2);
		EntityRenderState entityRenderState = extractRenderState(livingEntity);
		if (entityRenderState instanceof LivingEntityRenderState livingEntityRenderState) {
			livingEntityRenderState.bodyRot = 180.0F + p * 20.0F;
			livingEntityRenderState.yRot = p * 20.0F;
			if (livingEntityRenderState.pose != Pose.FALL_FLYING) {
				livingEntityRenderState.xRot = -q * 20.0F;
			} else {
				livingEntityRenderState.xRot = 0.0F;
			}

			livingEntityRenderState.boundingBoxWidth = livingEntityRenderState.boundingBoxWidth / livingEntityRenderState.scale;
			livingEntityRenderState.boundingBoxHeight = livingEntityRenderState.boundingBoxHeight / livingEntityRenderState.scale;
			livingEntityRenderState.scale = 1.0F;
		}

		Vector3f vector3f = new Vector3f(0.0F, entityRenderState.boundingBoxHeight / 2.0F + f, 0.0F);
		guiGraphics.submitEntityRenderState(entityRenderState, m, vector3f, quaternionf, quaternionf2, i, j, k, l);
	}

	private static EntityRenderState extractRenderState(LivingEntity livingEntity) {
		EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		EntityRenderer<? super LivingEntity, ?> entityRenderer = entityRenderDispatcher.getRenderer(livingEntity);
		EntityRenderState entityRenderState = entityRenderer.createRenderState(livingEntity, 1.0F);
		entityRenderState.lightCoords = 15728880;
		entityRenderState.shadowPieces.clear();
		entityRenderState.outlineColor = 0;
		return entityRenderState;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
		if (this.buttonClicked) {
			this.buttonClicked = false;
			return true;
		} else {
			return super.mouseReleased(mouseButtonEvent);
		}
	}
}
