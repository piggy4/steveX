package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.item.ItemStackRenderState;

@Environment(EnvType.CLIENT)
public class ItemDisplayEntityRenderState extends DisplayEntityRenderState {
	public final ItemStackRenderState item = new ItemStackRenderState();

	@Override
	public boolean hasSubState() {
		return !this.item.isEmpty();
	}
}
