package net.minecraft.client.renderer.blockentity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.state.properties.ChestType;

@Environment(EnvType.CLIENT)
public class ChestRenderState extends BlockEntityRenderState {
	public ChestType type = ChestType.SINGLE;
	public float open;
	public float angle;
	public ChestRenderState.ChestMaterialType material = ChestRenderState.ChestMaterialType.REGULAR;

	@Environment(EnvType.CLIENT)
	public enum ChestMaterialType {
		ENDER_CHEST,
		CHRISTMAS,
		TRAPPED,
		COPPER_UNAFFECTED,
		COPPER_EXPOSED,
		COPPER_WEATHERED,
		COPPER_OXIDIZED,
		REGULAR;
	}
}
