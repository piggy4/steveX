package net.minecraft.client.renderer.blockentity.state;

import java.util.EnumSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Direction;

@Environment(EnvType.CLIENT)
public class EndPortalRenderState extends BlockEntityRenderState {
	public EnumSet<Direction> facesToShow = EnumSet.noneOf(Direction.class);
}
