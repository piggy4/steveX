package net.minecraft.client.renderer.entity;

import java.util.Optional;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.animal.golem.CopperGolemModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.BlockDecorationLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.CopperGolemOxidationLevels;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.component.BlockItemStateProperties;

@Environment(EnvType.CLIENT)
public class CopperGolemRenderer extends MobRenderer<CopperGolem, CopperGolemRenderState, CopperGolemModel> {
	public CopperGolemRenderer(EntityRendererProvider.Context context) {
		super(context, new CopperGolemModel(context.bakeLayer(ModelLayers.COPPER_GOLEM)), 0.5F);
		this.addLayer(
			new LivingEntityEmissiveLayer<>(
				this,
				getEyeTextureLocationProvider(),
				(copperGolemRenderState, f) -> 1.0F,
				new CopperGolemModel(context.bakeLayer(ModelLayers.COPPER_GOLEM)),
				RenderTypes::eyes,
				false
			)
		);
		this.addLayer(new ItemInHandLayer<>(this));
		this.addLayer(new BlockDecorationLayer<>(this, copperGolemRenderState -> copperGolemRenderState.blockOnAntenna, this.model::applyBlockOnAntennaTransform));
		this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getPlayerSkinRenderCache()));
	}

	public Identifier getTextureLocation(CopperGolemRenderState copperGolemRenderState) {
		return CopperGolemOxidationLevels.getOxidationLevel(copperGolemRenderState.weathering).texture();
	}

	private static Function<CopperGolemRenderState, Identifier> getEyeTextureLocationProvider() {
		return copperGolemRenderState -> CopperGolemOxidationLevels.getOxidationLevel(copperGolemRenderState.weathering).eyeTexture();
	}

	public CopperGolemRenderState createRenderState() {
		return new CopperGolemRenderState();
	}

	public void extractRenderState(CopperGolem copperGolem, CopperGolemRenderState copperGolemRenderState, float f) {
		super.extractRenderState(copperGolem, copperGolemRenderState, f);
		ArmedEntityRenderState.extractArmedEntityRenderState(copperGolem, copperGolemRenderState, this.itemModelResolver, f);
		copperGolemRenderState.weathering = copperGolem.getWeatherState();
		copperGolemRenderState.copperGolemState = copperGolem.getState();
		copperGolemRenderState.idleAnimationState.copyFrom(copperGolem.getIdleAnimationState());
		copperGolemRenderState.interactionGetItem.copyFrom(copperGolem.getInteractionGetItemAnimationState());
		copperGolemRenderState.interactionGetNoItem.copyFrom(copperGolem.getInteractionGetNoItemAnimationState());
		copperGolemRenderState.interactionDropItem.copyFrom(copperGolem.getInteractionDropItemAnimationState());
		copperGolemRenderState.interactionDropNoItem.copyFrom(copperGolem.getInteractionDropNoItemAnimationState());
		copperGolemRenderState.blockOnAntenna = Optional.of(copperGolem.getItemBySlot(CopperGolem.EQUIPMENT_SLOT_ANTENNA)).flatMap(itemStack -> {
			if (itemStack.getItem() instanceof BlockItem blockItem) {
				BlockItemStateProperties blockItemStateProperties = itemStack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
				return Optional.of(blockItemStateProperties.apply(blockItem.getBlock().defaultBlockState()));
			} else {
				return Optional.empty();
			}
		});
	}
}
