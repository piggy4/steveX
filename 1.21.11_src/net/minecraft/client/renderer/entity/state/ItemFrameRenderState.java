package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ItemFrameRenderState extends EntityRenderState {
	public Direction direction = Direction.NORTH;
	public final ItemStackRenderState item = new ItemStackRenderState();
	public int rotation;
	public boolean isGlowFrame;
	public @Nullable MapId mapId;
	public final MapRenderState mapRenderState = new MapRenderState();
}
