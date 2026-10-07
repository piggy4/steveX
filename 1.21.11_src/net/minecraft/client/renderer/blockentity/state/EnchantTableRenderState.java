package net.minecraft.client.renderer.blockentity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class EnchantTableRenderState extends BlockEntityRenderState {
	public float time;
	public float yRot;
	public float flip;
	public float open;
}
