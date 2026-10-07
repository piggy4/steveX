package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class SimpleEquipmentLayer<S extends LivingEntityRenderState, RM extends EntityModel<? super S>, EM extends EntityModel<? super S>>
	extends RenderLayer<S, RM> {
	private final EquipmentLayerRenderer equipmentRenderer;
	private final EquipmentClientInfo.LayerType layer;
	private final Function<S, ItemStack> itemGetter;
	private final EM adultModel;
	private final @Nullable EM babyModel;
	private final int order;

	public SimpleEquipmentLayer(
		RenderLayerParent<S, RM> renderLayerParent,
		EquipmentLayerRenderer equipmentLayerRenderer,
		EquipmentClientInfo.LayerType layerType,
		Function<S, ItemStack> function,
		EM entityModel,
		@Nullable EM entityModel2,
		int i
	) {
		super(renderLayerParent);
		this.equipmentRenderer = equipmentLayerRenderer;
		this.layer = layerType;
		this.itemGetter = function;
		this.adultModel = entityModel;
		this.babyModel = entityModel2;
		this.order = i;
	}

	public SimpleEquipmentLayer(
		RenderLayerParent<S, RM> renderLayerParent,
		EquipmentLayerRenderer equipmentLayerRenderer,
		EquipmentClientInfo.LayerType layerType,
		Function<S, ItemStack> function,
		EM entityModel,
		@Nullable EM entityModel2
	) {
		this(renderLayerParent, equipmentLayerRenderer, layerType, function, entityModel, entityModel2, 0);
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, S livingEntityRenderState, float f, float g) {
		ItemStack itemStack = this.itemGetter.apply(livingEntityRenderState);
		Equippable equippable = itemStack.get(DataComponents.EQUIPPABLE);
		if (equippable != null && !equippable.assetId().isEmpty() && (!livingEntityRenderState.isBaby || this.babyModel != null)) {
			EM entityModel = livingEntityRenderState.isBaby ? this.babyModel : this.adultModel;
			this.equipmentRenderer
				.renderLayers(
					this.layer,
					equippable.assetId().get(),
					entityModel,
					livingEntityRenderState,
					itemStack,
					poseStack,
					submitNodeCollector,
					i,
					null,
					livingEntityRenderState.outlineColor,
					this.order
				);
		}
	}
}
