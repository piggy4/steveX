package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.animal.llama.LlamaModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.LlamaRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

@Environment(EnvType.CLIENT)
public class LlamaDecorLayer extends RenderLayer<LlamaRenderState, LlamaModel> {
	private final LlamaModel adultModel;
	private final LlamaModel babyModel;
	private final EquipmentLayerRenderer equipmentRenderer;

	public LlamaDecorLayer(
		RenderLayerParent<LlamaRenderState, LlamaModel> renderLayerParent, EntityModelSet entityModelSet, EquipmentLayerRenderer equipmentLayerRenderer
	) {
		super(renderLayerParent);
		this.equipmentRenderer = equipmentLayerRenderer;
		this.adultModel = new LlamaModel(entityModelSet.bakeLayer(ModelLayers.LLAMA_DECOR));
		this.babyModel = new LlamaModel(entityModelSet.bakeLayer(ModelLayers.LLAMA_BABY_DECOR));
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, LlamaRenderState llamaRenderState, float f, float g) {
		ItemStack itemStack = llamaRenderState.bodyItem;
		Equippable equippable = itemStack.get(DataComponents.EQUIPPABLE);
		if (equippable != null && equippable.assetId().isPresent()) {
			this.renderEquipment(poseStack, submitNodeCollector, llamaRenderState, itemStack, equippable.assetId().get(), i);
		} else if (llamaRenderState.isTraderLlama) {
			this.renderEquipment(poseStack, submitNodeCollector, llamaRenderState, ItemStack.EMPTY, EquipmentAssets.TRADER_LLAMA, i);
		}
	}

	private void renderEquipment(
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		LlamaRenderState llamaRenderState,
		ItemStack itemStack,
		ResourceKey<EquipmentAsset> resourceKey,
		int i
	) {
		LlamaModel llamaModel = llamaRenderState.isBaby ? this.babyModel : this.adultModel;
		this.equipmentRenderer
			.renderLayers(
				EquipmentClientInfo.LayerType.LLAMA_BODY,
				resourceKey,
				llamaModel,
				llamaRenderState,
				itemStack,
				poseStack,
				submitNodeCollector,
				i,
				llamaRenderState.outlineColor
			);
	}
}
