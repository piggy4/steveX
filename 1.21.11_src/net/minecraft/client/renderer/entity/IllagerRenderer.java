package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.item.CrossbowItem;

@Environment(EnvType.CLIENT)
public abstract class IllagerRenderer<T extends AbstractIllager, S extends IllagerRenderState> extends MobRenderer<T, S, IllagerModel<S>> {
	protected IllagerRenderer(EntityRendererProvider.Context context, IllagerModel<S> illagerModel, float f) {
		super(context, illagerModel, f);
		this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getPlayerSkinRenderCache()));
	}

	public void extractRenderState(T abstractIllager, S illagerRenderState, float f) {
		super.extractRenderState(abstractIllager, illagerRenderState, f);
		ArmedEntityRenderState.extractArmedEntityRenderState(abstractIllager, illagerRenderState, this.itemModelResolver, f);
		illagerRenderState.isRiding = abstractIllager.isPassenger();
		illagerRenderState.mainArm = abstractIllager.getMainArm();
		illagerRenderState.armPose = abstractIllager.getArmPose();
		illagerRenderState.maxCrossbowChargeDuration = illagerRenderState.armPose == AbstractIllager.IllagerArmPose.CROSSBOW_CHARGE
			? CrossbowItem.getChargeDuration(abstractIllager.getUseItem(), abstractIllager)
			: 0;
		illagerRenderState.ticksUsingItem = abstractIllager.getTicksUsingItem(f);
		illagerRenderState.attackAnim = abstractIllager.getAttackAnim(f);
		illagerRenderState.isAggressive = abstractIllager.isAggressive();
	}
}
