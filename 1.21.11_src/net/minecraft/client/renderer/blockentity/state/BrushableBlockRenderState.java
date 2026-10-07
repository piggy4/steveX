package net.minecraft.client.renderer.blockentity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BrushableBlockRenderState extends BlockEntityRenderState {
	public ItemStackRenderState itemState = new ItemStackRenderState();
	public int dustProgress;
	public @Nullable Direction hitDirection;
}
