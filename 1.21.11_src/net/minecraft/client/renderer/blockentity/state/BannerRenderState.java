package net.minecraft.client.renderer.blockentity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

@Environment(EnvType.CLIENT)
public class BannerRenderState extends BlockEntityRenderState {
	public DyeColor baseColor;
	public BannerPatternLayers patterns;
	public float phase;
	public float angle;
	public boolean standing;
}
