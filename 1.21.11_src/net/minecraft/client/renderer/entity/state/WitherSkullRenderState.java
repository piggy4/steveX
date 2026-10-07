package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.object.skull.SkullModelBase;

@Environment(EnvType.CLIENT)
public class WitherSkullRenderState extends EntityRenderState {
	public boolean isDangerous;
	public final SkullModelBase.State modelState = new SkullModelBase.State();
}
