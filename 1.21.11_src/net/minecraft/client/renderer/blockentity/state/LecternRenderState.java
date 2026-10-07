package net.minecraft.client.renderer.blockentity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class LecternRenderState extends BlockEntityRenderState {
	public boolean hasBook;
	public float yRot;
}
