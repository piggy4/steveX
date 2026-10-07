package net.minecraft.client.renderer.blockentity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class CondiutRenderState extends BlockEntityRenderState {
	public float animTime;
	public boolean isActive;
	public float activeRotation;
	public int animationPhase;
	public boolean isHunting;
}
