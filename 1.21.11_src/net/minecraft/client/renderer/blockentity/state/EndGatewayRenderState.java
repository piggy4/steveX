package net.minecraft.client.renderer.blockentity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class EndGatewayRenderState extends EndPortalRenderState {
	public int height;
	public float scale;
	public int color;
	public float animationTime;
}
