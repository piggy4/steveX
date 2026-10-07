package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.Shapes;
import org.jspecify.annotations.Nullable;

public class KelpPlantBlock extends GrowingPlantBodyBlock implements LiquidBlockContainer {
	public static final MapCodec<KelpPlantBlock> CODEC = simpleCodec(KelpPlantBlock::new);

	@Override
	public MapCodec<KelpPlantBlock> codec() {
		return CODEC;
	}

	public KelpPlantBlock(BlockBehaviour.Properties properties) {
		super(properties, Direction.UP, Shapes.block(), true);
	}

	@Override
	protected GrowingPlantHeadBlock getHeadBlock() {
		return (GrowingPlantHeadBlock)Blocks.KELP;
	}

	@Override
	protected FluidState getFluidState(BlockState blockState) {
		return Fluids.WATER.getSource(false);
	}

	@Override
	protected boolean canAttachTo(BlockState blockState) {
		return this.getHeadBlock().canAttachTo(blockState);
	}

	@Override
	public boolean canPlaceLiquid(@Nullable LivingEntity livingEntity, BlockGetter blockGetter, BlockPos blockPos, BlockState blockState, Fluid fluid) {
		return false;
	}

	@Override
	public boolean placeLiquid(LevelAccessor levelAccessor, BlockPos blockPos, BlockState blockState, FluidState fluidState) {
		return false;
	}
}
