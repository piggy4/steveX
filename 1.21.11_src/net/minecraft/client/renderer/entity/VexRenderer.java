package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.vex.VexModel;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.VexRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.Vex;

@Environment(EnvType.CLIENT)
public class VexRenderer extends MobRenderer<Vex, VexRenderState, VexModel> {
	private static final Identifier VEX_LOCATION = Identifier.withDefaultNamespace("textures/entity/illager/vex.png");
	private static final Identifier VEX_CHARGING_LOCATION = Identifier.withDefaultNamespace("textures/entity/illager/vex_charging.png");

	public VexRenderer(EntityRendererProvider.Context context) {
		super(context, new VexModel(context.bakeLayer(ModelLayers.VEX)), 0.3F);
		this.addLayer(new ItemInHandLayer<>(this));
	}

	protected int getBlockLightLevel(Vex vex, BlockPos blockPos) {
		return 15;
	}

	public Identifier getTextureLocation(VexRenderState vexRenderState) {
		return vexRenderState.isCharging ? VEX_CHARGING_LOCATION : VEX_LOCATION;
	}

	public VexRenderState createRenderState() {
		return new VexRenderState();
	}

	public void extractRenderState(Vex vex, VexRenderState vexRenderState, float f) {
		super.extractRenderState(vex, vexRenderState, f);
		ArmedEntityRenderState.extractArmedEntityRenderState(vex, vexRenderState, this.itemModelResolver, f);
		vexRenderState.isCharging = vex.isCharging();
	}
}
