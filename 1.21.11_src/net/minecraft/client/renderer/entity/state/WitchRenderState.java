package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class WitchRenderState extends HoldingEntityRenderState {
	public int entityId;
	public boolean isHoldingItem;
	public boolean isHoldingPotion;
}
