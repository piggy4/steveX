package net.minecraft.client.renderer.blockentity.state;

import java.util.Collections;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;

@Environment(EnvType.CLIENT)
public class CampfireRenderState extends BlockEntityRenderState {
	public List<ItemStackRenderState> items = Collections.emptyList();
	public Direction facing = Direction.NORTH;
}
