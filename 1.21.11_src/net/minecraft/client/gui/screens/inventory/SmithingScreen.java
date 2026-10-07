package net.minecraft.client.gui.screens.inventory;

import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.equipment.Equippable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class SmithingScreen extends ItemCombinerScreen<SmithingMenu> {
	private static final Identifier ERROR_SPRITE = Identifier.withDefaultNamespace("container/smithing/error");
	private static final Identifier EMPTY_SLOT_SMITHING_TEMPLATE_ARMOR_TRIM = Identifier.withDefaultNamespace("container/slot/smithing_template_armor_trim");
	private static final Identifier EMPTY_SLOT_SMITHING_TEMPLATE_NETHERITE_UPGRADE = Identifier.withDefaultNamespace(
		"container/slot/smithing_template_netherite_upgrade"
	);
	private static final Component MISSING_TEMPLATE_TOOLTIP = Component.translatable("container.upgrade.missing_template_tooltip");
	private static final Component ERROR_TOOLTIP = Component.translatable("container.upgrade.error_tooltip");
	private static final List<Identifier> EMPTY_SLOT_SMITHING_TEMPLATES = List.of(
		EMPTY_SLOT_SMITHING_TEMPLATE_ARMOR_TRIM, EMPTY_SLOT_SMITHING_TEMPLATE_NETHERITE_UPGRADE
	);
	private static final int TITLE_LABEL_X = 44;
	private static final int TITLE_LABEL_Y = 15;
	private static final int ERROR_ICON_WIDTH = 28;
	private static final int ERROR_ICON_HEIGHT = 21;
	private static final int ERROR_ICON_X = 65;
	private static final int ERROR_ICON_Y = 46;
	private static final int TOOLTIP_WIDTH = 115;
	private static final int ARMOR_STAND_Y_ROT = 210;
	private static final int ARMOR_STAND_X_ROT = 25;
	private static final Vector3f ARMOR_STAND_TRANSLATION = new Vector3f(0.0F, 1.0F, 0.0F);
	private static final Quaternionf ARMOR_STAND_ANGLE = new Quaternionf().rotationXYZ(0.43633232F, 0.0F, (float) Math.PI);
	private static final int ARMOR_STAND_SCALE = 25;
	private static final int ARMOR_STAND_LEFT = 121;
	private static final int ARMOR_STAND_TOP = 20;
	private static final int ARMOR_STAND_RIGHT = 161;
	private static final int ARMOR_STAND_BOTTOM = 80;
	private final CyclingSlotBackground templateIcon = new CyclingSlotBackground(0);
	private final CyclingSlotBackground baseIcon = new CyclingSlotBackground(1);
	private final CyclingSlotBackground additionalIcon = new CyclingSlotBackground(2);
	private final ArmorStandRenderState armorStandPreview = new ArmorStandRenderState();

	public SmithingScreen(SmithingMenu smithingMenu, Inventory inventory, Component component) {
		super(smithingMenu, inventory, component, Identifier.withDefaultNamespace("textures/gui/container/smithing.png"));
		this.titleLabelX = 44;
		this.titleLabelY = 15;
		this.armorStandPreview.entityType = EntityType.ARMOR_STAND;
		this.armorStandPreview.showBasePlate = false;
		this.armorStandPreview.showArms = true;
		this.armorStandPreview.xRot = 25.0F;
		this.armorStandPreview.bodyRot = 210.0F;
	}

	@Override
	protected void subInit() {
		this.updateArmorStandPreview(this.menu.getSlot(3).getItem());
	}

	@Override
	public void containerTick() {
		super.containerTick();
		Optional<SmithingTemplateItem> optional = this.getTemplateItem();
		this.templateIcon.tick(EMPTY_SLOT_SMITHING_TEMPLATES);
		this.baseIcon.tick(optional.map(SmithingTemplateItem::getBaseSlotEmptyIcons).orElse(List.of()));
		this.additionalIcon.tick(optional.map(SmithingTemplateItem::getAdditionalSlotEmptyIcons).orElse(List.of()));
	}

	private Optional<SmithingTemplateItem> getTemplateItem() {
		ItemStack itemStack = this.menu.getSlot(0).getItem();
		return !itemStack.isEmpty() && itemStack.getItem() instanceof SmithingTemplateItem smithingTemplateItem
			? Optional.of(smithingTemplateItem)
			: Optional.empty();
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		this.renderOnboardingTooltips(guiGraphics, i, j);
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float f, int i, int j) {
		super.renderBg(guiGraphics, f, i, j);
		this.templateIcon.render(this.menu, guiGraphics, f, this.leftPos, this.topPos);
		this.baseIcon.render(this.menu, guiGraphics, f, this.leftPos, this.topPos);
		this.additionalIcon.render(this.menu, guiGraphics, f, this.leftPos, this.topPos);
		int k = this.leftPos + 121;
		int l = this.topPos + 20;
		int m = this.leftPos + 161;
		int n = this.topPos + 80;
		guiGraphics.submitEntityRenderState(this.armorStandPreview, 25.0F, ARMOR_STAND_TRANSLATION, ARMOR_STAND_ANGLE, null, k, l, m, n);
	}

	@Override
	public void slotChanged(AbstractContainerMenu abstractContainerMenu, int i, ItemStack itemStack) {
		if (i == 3) {
			this.updateArmorStandPreview(itemStack);
		}
	}

	private void updateArmorStandPreview(ItemStack itemStack) {
		this.armorStandPreview.leftHandItemStack = ItemStack.EMPTY;
		this.armorStandPreview.leftHandItemState.clear();
		this.armorStandPreview.headEquipment = ItemStack.EMPTY;
		this.armorStandPreview.headItem.clear();
		this.armorStandPreview.chestEquipment = ItemStack.EMPTY;
		this.armorStandPreview.legsEquipment = ItemStack.EMPTY;
		this.armorStandPreview.feetEquipment = ItemStack.EMPTY;
		if (!itemStack.isEmpty()) {
			Equippable equippable = itemStack.get(DataComponents.EQUIPPABLE);
			EquipmentSlot equipmentSlot = equippable != null ? equippable.slot() : null;
			ItemModelResolver itemModelResolver = this.minecraft.getItemModelResolver();
			switch (equipmentSlot) {
				case HEAD:
					if (HumanoidArmorLayer.shouldRender(itemStack, EquipmentSlot.HEAD)) {
						this.armorStandPreview.headEquipment = itemStack.copy();
					} else {
						itemModelResolver.updateForTopItem(this.armorStandPreview.headItem, itemStack, ItemDisplayContext.HEAD, null, null, 0);
					}
					break;
				case CHEST:
					this.armorStandPreview.chestEquipment = itemStack.copy();
					break;
				case LEGS:
					this.armorStandPreview.legsEquipment = itemStack.copy();
					break;
				case FEET:
					this.armorStandPreview.feetEquipment = itemStack.copy();
					break;
				case null:
				default:
					this.armorStandPreview.leftHandItemStack = itemStack.copy();
					itemModelResolver.updateForTopItem(this.armorStandPreview.leftHandItemState, itemStack, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, null, null, 0);
			}
		}
	}

	@Override
	protected void renderErrorIcon(GuiGraphics guiGraphics, int i, int j) {
		if (this.hasRecipeError()) {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, ERROR_SPRITE, i + 65, j + 46, 28, 21);
		}
	}

	private void renderOnboardingTooltips(GuiGraphics guiGraphics, int i, int j) {
		Optional<Component> optional = Optional.empty();
		if (this.hasRecipeError() && this.isHovering(65, 46, 28, 21, i, j)) {
			optional = Optional.of(ERROR_TOOLTIP);
		}

		if (this.hoveredSlot != null) {
			ItemStack itemStack = this.menu.getSlot(0).getItem();
			ItemStack itemStack2 = this.hoveredSlot.getItem();
			if (itemStack.isEmpty()) {
				if (this.hoveredSlot.index == 0) {
					optional = Optional.of(MISSING_TEMPLATE_TOOLTIP);
				}
			} else if (itemStack.getItem() instanceof SmithingTemplateItem smithingTemplateItem && itemStack2.isEmpty()) {
				if (this.hoveredSlot.index == 1) {
					optional = Optional.of(smithingTemplateItem.getBaseSlotDescription());
				} else if (this.hoveredSlot.index == 2) {
					optional = Optional.of(smithingTemplateItem.getAdditionSlotDescription());
				}
			}
		}

		optional.ifPresent(component -> guiGraphics.setTooltipForNextFrame(this.font, this.font.split(component, 115), i, j));
	}

	private boolean hasRecipeError() {
		return this.menu.hasRecipeError();
	}
}
