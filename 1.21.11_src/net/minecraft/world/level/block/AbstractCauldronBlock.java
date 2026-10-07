package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class AbstractCauldronBlock extends Block {
	protected static final int FLOOR_LEVEL = 4;
	private static final VoxelShape SHAPE_INSIDE = Block.column(12.0, 4.0, 16.0);
	protected static final VoxelShape SHAPE = Util.make(
		() -> {
			int i = 4;
			int j = 3;
			int k = 2;
			return Shapes.join(
				Shapes.block(),
				Shapes.or(Block.column(16.0, 8.0, 0.0, 3.0), Block.column(8.0, 16.0, 0.0, 3.0), Block.column(12.0, 0.0, 3.0), SHAPE_INSIDE),
				BooleanOp.ONLY_FIRST
			);
		}
	);
	protected final CauldronInteraction.InteractionMap interactions;

	@Override
	protected abstract MapCodec<? extends AbstractCauldronBlock> codec();

	public AbstractCauldronBlock(BlockBehaviour.Properties properties, CauldronInteraction.InteractionMap interactionMap) {
		super(properties);
		this.interactions = interactionMap;
	}

	protected double getContentHeight(BlockState blockState) {
		return 0.0;
	}

	@Override
	protected InteractionResult useItemOn(
		ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult
	) {
		CauldronInteraction cauldronInteraction = this.interactions.map().get(itemStack.getItem());
		return cauldronInteraction.interact(blockState, level, blockPos, player, interactionHand, itemStack);
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getInteractionShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {
		return SHAPE_INSIDE;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState blockState) {
		return true;
	}

	@Override
	protected boolean isPathfindable(BlockState blockState, PathComputationType pathComputationType) {
		return false;
	}

	public abstract boolean isFull(BlockState blockState);

	@Override
	protected void tick(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, RandomSource randomSource) {
		BlockPos blockPos2 = PointedDripstoneBlock.findStalactiteTipAboveCauldron(serverLevel, blockPos);
		if (blockPos2 != null) {
			Fluid fluid = PointedDripstoneBlock.getCauldronFillFluidType(serverLevel, blockPos2);
			if (fluid != Fluids.EMPTY && this.canReceiveStalactiteDrip(fluid)) {
				this.receiveStalactiteDrip(blockState, serverLevel, blockPos, fluid);
			}
		}
	}

	protected boolean canReceiveStalactiteDrip(Fluid fluid) {
		return false;
	}

	protected void receiveStalactiteDrip(BlockState blockState, Level level, BlockPos blockPos, Fluid fluid) {
	}
}
