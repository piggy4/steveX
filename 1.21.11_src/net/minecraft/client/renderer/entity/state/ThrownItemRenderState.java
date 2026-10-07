package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.item.ItemStackRenderState;

@Environment(EnvType.CLIENT)
public class ThrownItemRenderState extends EntityRenderState {
	public final ItemStackRenderState item = new ItemStackRenderState();
}
