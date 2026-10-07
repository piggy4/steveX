package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.CreakingHeartBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.CreakingHeartState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;

public class CreakingHeartBlock extends BaseEntityBlock {
	public static final MapCodec<CreakingHeartBlock> CODEC = simpleCodec(CreakingHeartBlock::new);
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
	public static final EnumProperty<CreakingHeartState> STATE = BlockStateProperties.CREAKING_HEART_STATE;
	public static final BooleanProperty NATURAL = BlockStateProperties.NATURAL;

	@Override
	public MapCodec<CreakingHeartBlock> codec() {
		return CODEC;
	}

	public CreakingHeartBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.defaultBlockState().setValue(AXIS, Direction.Axis.Y).setValue(STATE, CreakingHeartState.UPROOTED).setValue(NATURAL, false));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
		return new CreakingHeartBlockEntity(blockPos, blockState);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> blockEntityType) {
		if (level.isClientSide()) {
			return null;
		} else {
			return blockState.getValue(STATE) != CreakingHeartState.UPROOTED
				? createTickerHelper(blockEntityType, BlockEntityType.CREAKING_HEART, CreakingHeartBlockEntity::serverTick)
				: null;
		}
	}

	@Override
	public void animateTick(BlockState blockState, Level level, BlockPos blockPos, RandomSource randomSource) {
		if (level.environmentAttributes().getValue(EnvironmentAttributes.CREAKING_ACTIVE, blockPos)) {
			if (blockState.getValue(STATE) != CreakingHeartState.UPROOTED) {
				if (randomSource.nextInt(16) == 0 && isSurroundedByLogs(level, blockPos)) {
					level.playLocalSound(blockPos.getX(), blockPos.getY(), blockPos.getZ(), SoundEvents.CREAKING_HEART_IDLE, SoundSource.BLOCKS, 1.0F, 1.0F, false);
				}
			}
		}
	}

	@Override
	protected BlockState updateShape(
		BlockState blockState,
		LevelReader levelReader,
		ScheduledTickAccess scheduledTickAccess,
		BlockPos blockPos,
		Direction direction,
		BlockPos blockPos2,
		BlockState blockState2,
		RandomSource randomSource
	) {
		scheduledTickAccess.scheduleTick(blockPos, this, 1);
		return super.updateShape(blockState, levelReader, scheduledTickAccess, blockPos, direction, blockPos2, blockState2, randomSource);
	}

	@Override
	protected void tick(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, RandomSource randomSource) {
		BlockState blockState2 = updateState(blockState, serverLevel, blockPos);
		if (blockState2 != blockState) {
			serverLevel.setBlock(blockPos, blockState2, 3);
		}
	}

	private static BlockState updateState(BlockState blockState, Level level, BlockPos blockPos) {
		boolean bl = hasRequiredLogs(blockState, level, blockPos);
		boolean bl2 = blockState.getValue(STATE) == CreakingHeartState.UPROOTED;
		return bl && bl2
			? blockState.setValue(
				STATE, level.environmentAttributes().getValue(EnvironmentAttributes.CREAKING_ACTIVE, blockPos) ? CreakingHeartState.AWAKE : CreakingHeartState.DORMANT
			)
			: blockState;
	}

	public static boolean hasRequiredLogs(BlockState blockState, LevelReader levelReader, BlockPos blockPos) {
		Direction.Axis axis = blockState.getValue(AXIS);

		for (Direction direction : axis.getDirections()) {
			BlockState blockState2 = levelReader.getBlockState(blockPos.relative(direction));
			if (!blockState2.is(BlockTags.PALE_OAK_LOGS) || blockState2.getValue(AXIS) != axis) {
				return false;
			}
		}

		return true;
	}

	private static boolean isSurroundedByLogs(LevelAccessor levelAccessor, BlockPos blockPos) {
		for (Direction direction : Direction.values()) {
			BlockPos blockPos2 = blockPos.relative(direction);
			BlockState blockState = levelAccessor.getBlockState(blockPos2);
			if (!blockState.is(BlockTags.PALE_OAK_LOGS)) {
				return false;
			}
		}

		return true;
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
		return updateState(
			this.defaultBlockState().setValue(AXIS, blockPlaceContext.getClickedFace().getAxis()), blockPlaceContext.getLevel(), blockPlaceContext.getClickedPos()
		);
	}

	@Override
	protected BlockState rotate(BlockState blockState, Rotation rotation) {
		return RotatedPillarBlock.rotatePillar(blockState, rotation);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS, STATE, NATURAL);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, boolean bl) {
		Containers.updateNeighboursAfterDestroy(blockState, serverLevel, blockPos);
	}

	@Override
	protected void onExplosionHit(
		BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, Explosion explosion, BiConsumer<ItemStack, BlockPos> biConsumer
	) {
		if (serverLevel.getBlockEntity(blockPos) instanceof CreakingHeartBlockEntity creakingHeartBlockEntity
			&& explosion instanceof ServerExplosion serverExplosion
			&& explosion.getBlockInteraction().shouldAffectBlocklikeEntities()) {
			creakingHeartBlockEntity.removeProtector(serverExplosion.getDamageSource());
			if (explosion.getIndirectSourceEntity() instanceof Player player && explosion.getBlockInteraction().shouldAffectBlocklikeEntities()) {
				this.tryAwardExperience(player, blockState, serverLevel, blockPos);
			}
		}

		super.onExplosionHit(blockState, serverLevel, blockPos, explosion, biConsumer);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos blockPos, BlockState blockState, Player player) {
		if (level.getBlockEntity(blockPos) instanceof CreakingHeartBlockEntity creakingHeartBlockEntity) {
			creakingHeartBlockEntity.removeProtector(player.damageSources().playerAttack(player));
			this.tryAwardExperience(player, blockState, level, blockPos);
		}

		return super.playerWillDestroy(level, blockPos, blockState, player);
	}

	private void tryAwardExperience(Player player, BlockState blockState, Level level, BlockPos blockPos) {
		if (!player.preventsBlockDrops() && !player.isSpectator() && blockState.getValue(NATURAL) && level instanceof ServerLevel serverLevel) {
			this.popExperience(serverLevel, blockPos, level.random.nextIntBetweenInclusive(20, 24));
		}
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState blockState) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState blockState, Level level, BlockPos blockPos, Direction direction) {
		if (blockState.getValue(STATE) == CreakingHeartState.UPROOTED) {
			return 0;
		} else {
			return level.getBlockEntity(blockPos) instanceof CreakingHeartBlockEntity creakingHeartBlockEntity ? creakingHeartBlockEntity.getAnalogOutputSignal() : 0;
		}
	}
}
