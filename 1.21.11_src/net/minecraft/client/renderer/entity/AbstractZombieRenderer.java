package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;

@Environment(EnvType.CLIENT)
public abstract class AbstractZombieRenderer<T extends Zombie, S extends ZombieRenderState, M extends ZombieModel<S>> extends HumanoidMobRenderer<T, S, M> {
	private static final Identifier ZOMBIE_LOCATION = Identifier.withDefaultNamespace("textures/entity/zombie/zombie.png");

	protected AbstractZombieRenderer(
		EntityRendererProvider.Context context, M zombieModel, M zombieModel2, ArmorModelSet<M> armorModelSet, ArmorModelSet<M> armorModelSet2
	) {
		super(context, zombieModel, zombieModel2, 0.5F);
		this.addLayer(new HumanoidArmorLayer<>(this, armorModelSet, armorModelSet2, context.getEquipmentRenderer()));
	}

	public Identifier getTextureLocation(S zombieRenderState) {
		return ZOMBIE_LOCATION;
	}

	public void extractRenderState(T zombie, S zombieRenderState, float f) {
		super.extractRenderState(zombie, zombieRenderState, f);
		zombieRenderState.isAggressive = zombie.isAggressive();
		zombieRenderState.isConverting = zombie.isUnderWaterConverting();
	}

	protected boolean isShaking(S zombieRenderState) {
		return super.isShaking(zombieRenderState) || zombieRenderState.isConverting;
	}

	protected HumanoidModel.ArmPose getArmPose(T zombie, HumanoidArm humanoidArm) {
		SwingAnimation swingAnimation = zombie.getItemHeldByArm(humanoidArm.getOpposite()).get(DataComponents.SWING_ANIMATION);
		return swingAnimation != null && swingAnimation.type() == SwingAnimationType.STAB ? HumanoidModel.ArmPose.SPEAR : super.getArmPose(zombie, humanoidArm);
	}
}
