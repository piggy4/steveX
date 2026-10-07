package net.minecraft.client.renderer.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.core.BlockPos;

@Environment(EnvType.CLIENT)
public class BlockBreakingRenderState extends MovingBlockRenderState {
	public int progress;

	public BlockBreakingRenderState(ClientLevel clientLevel, BlockPos blockPos, int i) {
		this.level = clientLevel;
		this.blockPos = blockPos;
		this.blockState = clientLevel.getBlockState(blockPos);
		this.progress = i;
		this.biome = clientLevel.getBiome(blockPos);
	}
}
