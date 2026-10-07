package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.witch.WitchModel;
import net.minecraft.client.renderer.entity.layers.WitchItemLayer;
import net.minecraft.client.renderer.entity.state.HoldingEntityRenderState;
import net.minecraft.client.renderer.entity.state.WitchRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
public class WitchRenderer extends MobRenderer<Witch, WitchRenderState, WitchModel> {
	private static final Identifier WITCH_LOCATION = Identifier.withDefaultNamespace("textures/entity/witch.png");

	public WitchRenderer(EntityRendererProvider.Context context) {
		super(context, new WitchModel(context.bakeLayer(ModelLayers.WITCH)), 0.5F);
		this.addLayer(new WitchItemLayer(this));
	}

	public Identifier getTextureLocation(WitchRenderState witchRenderState) {
		return WITCH_LOCATION;
	}

	public WitchRenderState createRenderState() {
		return new WitchRenderState();
	}

	public void extractRenderState(Witch witch, WitchRenderState witchRenderState, float f) {
		super.extractRenderState(witch, witchRenderState, f);
		HoldingEntityRenderState.extractHoldingEntityRenderState(witch, witchRenderState, this.itemModelResolver);
		witchRenderState.entityId = witch.getId();
		ItemStack itemStack = witch.getMainHandItem();
		witchRenderState.isHoldingItem = !itemStack.isEmpty();
		witchRenderState.isHoldingPotion = itemStack.is(Items.POTION);
	}
}
