package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.animal.equine.Markings;
import net.minecraft.world.entity.animal.equine.Variant;

@Environment(EnvType.CLIENT)
public class HorseRenderState extends EquineRenderState {
	public Variant variant = Variant.WHITE;
	public Markings markings = Markings.NONE;
}
