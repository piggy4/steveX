package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

@Environment(EnvType.CLIENT)
public class HumanoidArmorLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>, A extends HumanoidModel<S>> extends RenderLayer<S, M> {
	private final ArmorModelSet<A> modelSet;
	private final ArmorModelSet<A> babyModelSet;
	private final EquipmentLayerRenderer equipmentRenderer;

	public HumanoidArmorLayer(RenderLayerParent<S, M> renderLayerParent, ArmorModelSet<A> armorModelSet, EquipmentLayerRenderer equipmentLayerRenderer) {
		this(renderLayerParent, armorModelSet, armorModelSet, equipmentLayerRenderer);
	}

	public HumanoidArmorLayer(
		RenderLayerParent<S, M> renderLayerParent, ArmorModelSet<A> armorModelSet, ArmorModelSet<A> armorModelSet2, EquipmentLayerRenderer equipmentLayerRenderer
	) {
		super(renderLayerParent);
		this.modelSet = armorModelSet;
		this.babyModelSet = armorModelSet2;
		this.equipmentRenderer = equipmentLayerRenderer;
	}

	public static boolean shouldRender(ItemStack itemStack, EquipmentSlot equipmentSlot) {
		Equippable equippable = itemStack.get(DataComponents.EQUIPPABLE);
		return equippable != null && shouldRender(equippable, equipmentSlot);
	}

	private static boolean shouldRender(Equippable equippable, EquipmentSlot equipmentSlot) {
		return equippable.assetId().isPresent() && equippable.slot() == equipmentSlot;
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, S humanoidRenderState, float f, float g) {
		this.renderArmorPiece(poseStack, submitNodeCollector, humanoidRenderState.chestEquipment, EquipmentSlot.CHEST, i, humanoidRenderState);
		this.renderArmorPiece(poseStack, submitNodeCollector, humanoidRenderState.legsEquipment, EquipmentSlot.LEGS, i, humanoidRenderState);
		this.renderArmorPiece(poseStack, submitNodeCollector, humanoidRenderState.feetEquipment, EquipmentSlot.FEET, i, humanoidRenderState);
		this.renderArmorPiece(poseStack, submitNodeCollector, humanoidRenderState.headEquipment, EquipmentSlot.HEAD, i, humanoidRenderState);
	}

	private void renderArmorPiece(
		PoseStack poseStack, SubmitNodeCollector submitNodeCollector, ItemStack itemStack, EquipmentSlot equipmentSlot, int i, S humanoidRenderState
	) {
		Equippable equippable = itemStack.get(DataComponents.EQUIPPABLE);
		if (equippable != null && shouldRender(equippable, equipmentSlot)) {
			A humanoidModel = this.getArmorModel(humanoidRenderState, equipmentSlot);
			EquipmentClientInfo.LayerType layerType = this.usesInnerModel(equipmentSlot)
				? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS
				: EquipmentClientInfo.LayerType.HUMANOID;
			this.equipmentRenderer
				.renderLayers(
					layerType,
					equippable.assetId().orElseThrow(),
					humanoidModel,
					humanoidRenderState,
					itemStack,
					poseStack,
					submitNodeCollector,
					i,
					humanoidRenderState.outlineColor
				);
		}
	}

	private A getArmorModel(S humanoidRenderState, EquipmentSlot equipmentSlot) {
		return (humanoidRenderState.isBaby ? this.babyModelSet : this.modelSet).get(equipmentSlot);
	}

	private boolean usesInnerModel(EquipmentSlot equipmentSlot) {
		return equipmentSlot == EquipmentSlot.LEGS;
	}
}
