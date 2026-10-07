package net.minecraft.client.renderer.block;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.EmptyBlockAndTintGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState} injected by mod fabric-rendering-v1</p>
 */
@Environment(EnvType.CLIENT)
public class MovingBlockRenderState implements BlockAndTintGetter, FabricRenderState {
	public BlockPos randomSeedPos = BlockPos.ZERO;
	public BlockPos blockPos = BlockPos.ZERO;
	public BlockState blockState = Blocks.AIR.defaultBlockState();
	public @Nullable Holder<Biome> biome;
	public BlockAndTintGetter level = EmptyBlockAndTintGetter.INSTANCE;

	@Override
	public float getShade(Direction direction, boolean bl) {
		return this.level.getShade(direction, bl);
	}

	@Override
	public LevelLightEngine getLightEngine() {
		return this.level.getLightEngine();
	}

	@Override
	public int getBlockTint(BlockPos blockPos, ColorResolver colorResolver) {
		return this.biome == null ? -1 : colorResolver.getColor(this.biome.value(), blockPos.getX(), blockPos.getZ());
	}

	@Override
	public @Nullable BlockEntity getBlockEntity(BlockPos blockPos) {
		return null;
	}

	@Override
	public BlockState getBlockState(BlockPos blockPos) {
		return blockPos.equals(this.blockPos) ? this.blockState : Blocks.AIR.defaultBlockState();
	}

	@Override
	public FluidState getFluidState(BlockPos blockPos) {
		return this.getBlockState(blockPos).getFluidState();
	}

	@Override
	public int getHeight() {
		return 1;
	}

	@Override
	public int getMinY() {
		return this.blockPos.getY();
	}
}
