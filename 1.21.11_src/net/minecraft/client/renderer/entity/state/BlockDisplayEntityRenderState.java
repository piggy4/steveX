package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Display;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BlockDisplayEntityRenderState extends DisplayEntityRenderState {
	public Display.BlockDisplay.@Nullable BlockRenderState blockRenderState;

	@Override
	public boolean hasSubState() {
		return this.blockRenderState != null;
	}
}
