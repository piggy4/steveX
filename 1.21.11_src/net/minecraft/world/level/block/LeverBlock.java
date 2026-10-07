package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class LeverBlock extends FaceAttachedHorizontalDirectionalBlock {
	public static final MapCodec<LeverBlock> CODEC = simpleCodec(LeverBlock::new);
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	private final Function<BlockState, VoxelShape> shapes;

	@Override
	public MapCodec<LeverBlock> codec() {
		return CODEC;
	}

	public LeverBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false).setValue(FACE, AttachFace.WALL));
		this.shapes = this.makeShapes();
	}

	private Function<BlockState, VoxelShape> makeShapes() {
		Map<AttachFace, Map<Direction, VoxelShape>> map = Shapes.rotateAttachFace(Block.boxZ(6.0, 8.0, 10.0, 16.0));
		return this.getShapeForEachState(blockState -> map.get(blockState.getValue(FACE)).get(blockState.getValue(FACING)), POWERED);
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return this.shapes.apply(blockState);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState blockState, Level level, BlockPos blockPos, Player player, BlockHitResult blockHitResult) {
		if (level.isClientSide()) {
			BlockState blockState2 = blockState.cycle(POWERED);
			if (blockState2.getValue(POWERED)) {
				makeParticle(blockState2, level, blockPos, 1.0F);
			}
		} else {
			this.pull(blockState, level, blockPos, null);
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	protected void onExplosionHit(
		BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, Explosion explosion, BiConsumer<ItemStack, BlockPos> biConsumer
	) {
		if (explosion.canTriggerBlocks()) {
			this.pull(blockState, serverLevel, blockPos, null);
		}

		super.onExplosionHit(blockState, serverLevel, blockPos, explosion, biConsumer);
	}

	public void pull(BlockState blockState, Level level, BlockPos blockPos, @Nullable Player player) {
		blockState = blockState.cycle(POWERED);
		level.setBlock(blockPos, blockState, 3);
		this.updateNeighbours(blockState, level, blockPos);
		playSound(player, level, blockPos, blockState);
		level.gameEvent(player, blockState.getValue(POWERED) ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, blockPos);
	}

	protected static void playSound(@Nullable Player player, LevelAccessor levelAccessor, BlockPos blockPos, BlockState blockState) {
		float f = blockState.getValue(POWERED) ? 0.6F : 0.5F;
		levelAccessor.playSound(player, blockPos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, f);
	}

	private static void makeParticle(BlockState blockState, LevelAccessor levelAccessor, BlockPos blockPos, float f) {
		Direction direction = blockState.getValue(FACING).getOpposite();
		Direction direction2 = getConnectedDirection(blockState).getOpposite();
		double d = blockPos.getX() + 0.5 + 0.1 * direction.getStepX() + 0.2 * direction2.getStepX();
		double e = blockPos.getY() + 0.5 + 0.1 * direction.getStepY() + 0.2 * direction2.getStepY();
		double g = blockPos.getZ() + 0.5 + 0.1 * direction.getStepZ() + 0.2 * direction2.getStepZ();
		levelAccessor.addParticle(new DustParticleOptions(16711680, f), d, e, g, 0.0, 0.0, 0.0);
	}

	@Override
	public void animateTick(BlockState blockState, Level level, BlockPos blockPos, RandomSource randomSource) {
		if (blockState.getValue(POWERED) && randomSource.nextFloat() < 0.25F) {
			makeParticle(blockState, level, blockPos, 0.5F);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, boolean bl) {
		if (!bl && blockState.getValue(POWERED)) {
			this.updateNeighbours(blockState, serverLevel, blockPos);
		}
	}

	@Override
	protected int getSignal(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, Direction direction) {
		return blockState.getValue(POWERED) ? 15 : 0;
	}

	@Override
	protected int getDirectSignal(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, Direction direction) {
		return blockState.getValue(POWERED) && getConnectedDirection(blockState) == direction ? 15 : 0;
	}

	@Override
	protected boolean isSignalSource(BlockState blockState) {
		return true;
	}

	private void updateNeighbours(BlockState blockState, Level level, BlockPos blockPos) {
		Direction direction = getConnectedDirection(blockState).getOpposite();
		Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(
			level, direction, direction.getAxis().isHorizontal() ? Direction.UP : blockState.getValue(FACING)
		);
		level.updateNeighborsAt(blockPos, this, orientation);
		level.updateNeighborsAt(blockPos.relative(direction), this, orientation);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACE, FACING, POWERED);
	}
}
