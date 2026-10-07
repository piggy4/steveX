package net.minecraft.client.renderer.blockentity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.blockentity.state.EndPortalRenderState;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;

@Environment(EnvType.CLIENT)
public class TheEndPortalRenderer extends AbstractEndPortalRenderer<TheEndPortalBlockEntity, EndPortalRenderState> {
	public EndPortalRenderState createRenderState() {
		return new EndPortalRenderState();
	}
}
