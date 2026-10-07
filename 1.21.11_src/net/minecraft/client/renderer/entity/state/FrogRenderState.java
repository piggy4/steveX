package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

@Environment(EnvType.CLIENT)
public class FrogRenderState extends LivingEntityRenderState {
	private static final Identifier DEFAULT_TEXTURE = Identifier.withDefaultNamespace("textures/entity/frog/temperate_frog.png");
	public boolean isSwimming;
	public final AnimationState jumpAnimationState = new AnimationState();
	public final AnimationState croakAnimationState = new AnimationState();
	public final AnimationState tongueAnimationState = new AnimationState();
	public final AnimationState swimIdleAnimationState = new AnimationState();
	public Identifier texture = DEFAULT_TEXTURE;
}
