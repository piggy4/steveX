package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WaterlilyBlock extends VegetationBlock {
	public static final MapCodec<WaterlilyBlock> CODEC = simpleCodec(WaterlilyBlock::new);
	private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 1.5);

	@Override
	public MapCodec<WaterlilyBlock> codec() {
		return CODEC;
	}

	public WaterlilyBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected void entityInside(
		BlockState blockState, Level level, BlockPos blockPos, Entity entity, InsideBlockEffectApplier insideBlockEffectApplier, boolean bl
	) {
		super.entityInside(blockState, level, blockPos, entity, insideBlockEffectApplier, bl);
		if (level instanceof ServerLevel && entity instanceof AbstractBoat) {
			level.destroyBlock(new BlockPos(blockPos), true, entity);
		}
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return SHAPE;
	}

	@Override
	protected boolean mayPlaceOn(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {
		FluidState fluidState = blockGetter.getFluidState(blockPos);
		FluidState fluidState2 = blockGetter.getFluidState(blockPos.above());
		return (fluidState.getType() == Fluids.WATER || blockState.getBlock() instanceof IceBlock) && fluidState2.getType() == Fluids.EMPTY;
	}
}
