package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class RespawnAnchorBlock extends Block {
	public static final MapCodec<RespawnAnchorBlock> CODEC = simpleCodec(RespawnAnchorBlock::new);
	public static final int MIN_CHARGES = 0;
	public static final int MAX_CHARGES = 4;
	public static final IntegerProperty CHARGE = BlockStateProperties.RESPAWN_ANCHOR_CHARGES;
	private static final ImmutableList<Vec3i> RESPAWN_HORIZONTAL_OFFSETS = ImmutableList.of(
		new Vec3i(0, 0, -1),
		new Vec3i(-1, 0, 0),
		new Vec3i(0, 0, 1),
		new Vec3i(1, 0, 0),
		new Vec3i(-1, 0, -1),
		new Vec3i(1, 0, -1),
		new Vec3i(-1, 0, 1),
		new Vec3i(1, 0, 1)
	);
	private static final ImmutableList<Vec3i> RESPAWN_OFFSETS = new Builder<Vec3i>()
		.addAll(RESPAWN_HORIZONTAL_OFFSETS)
		.addAll(RESPAWN_HORIZONTAL_OFFSETS.stream().map(Vec3i::below).iterator())
		.addAll(RESPAWN_HORIZONTAL_OFFSETS.stream().map(Vec3i::above).iterator())
		.add(new Vec3i(0, 1, 0))
		.build();

	@Override
	public MapCodec<RespawnAnchorBlock> codec() {
		return CODEC;
	}

	public RespawnAnchorBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(CHARGE, 0));
	}

	@Override
	protected InteractionResult useItemOn(
		ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult
	) {
		if (isRespawnFuel(itemStack) && canBeCharged(blockState)) {
			charge(player, level, blockPos, blockState);
			itemStack.consume(1, player);
			return InteractionResult.SUCCESS;
		} else {
			return interactionHand == InteractionHand.MAIN_HAND && isRespawnFuel(player.getItemInHand(InteractionHand.OFF_HAND)) && canBeCharged(blockState)
				? InteractionResult.PASS
				: InteractionResult.TRY_WITH_EMPTY_HAND;
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState blockState, Level level, BlockPos blockPos, Player player, BlockHitResult blockHitResult) {
		if (blockState.getValue(CHARGE) == 0) {
			return InteractionResult.PASS;
		}

		if (level instanceof ServerLevel serverLevel) {
			if (!canSetSpawn(serverLevel, blockPos)) {
				this.explode(blockState, serverLevel, blockPos);
				return InteractionResult.SUCCESS_SERVER;
			}

			if (player instanceof ServerPlayer serverPlayer) {
				ServerPlayer.RespawnConfig respawnConfig = serverPlayer.getRespawnConfig();
				ServerPlayer.RespawnConfig respawnConfig2 = new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(serverLevel.dimension(), blockPos, 0.0F, 0.0F), false);
				if (respawnConfig == null || !respawnConfig.isSamePosition(respawnConfig2)) {
					serverPlayer.setRespawnPosition(respawnConfig2, true);
					serverLevel.playSound(
						null, blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 1.0F, 1.0F
					);
					return InteractionResult.SUCCESS_SERVER;
				}
			}

			return InteractionResult.CONSUME;
		} else {
			return InteractionResult.CONSUME;
		}
	}

	private static boolean isRespawnFuel(ItemStack itemStack) {
		return itemStack.is(Items.GLOWSTONE);
	}

	private static boolean canBeCharged(BlockState blockState) {
		return blockState.getValue(CHARGE) < 4;
	}

	private static boolean isWaterThatWouldFlow(BlockPos blockPos, Level level) {
		FluidState fluidState = level.getFluidState(blockPos);
		if (!fluidState.is(FluidTags.WATER)) {
			return false;
		}

		if (fluidState.isSource()) {
			return true;
		}

		float f = fluidState.getAmount();
		if (f < 2.0F) {
			return false;
		}

		FluidState fluidState2 = level.getFluidState(blockPos.below());
		return !fluidState2.is(FluidTags.WATER);
	}

	private void explode(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos) {
		serverLevel.removeBlock(blockPos, false);
		boolean bl = Direction.Plane.HORIZONTAL.stream().map(blockPos::relative).anyMatch(blockPosx -> isWaterThatWouldFlow(blockPosx, serverLevel));
		final boolean bl2 = bl || serverLevel.getFluidState(blockPos.above()).is(FluidTags.WATER);
		ExplosionDamageCalculator explosionDamageCalculator = new ExplosionDamageCalculator() {
			@Override
			public Optional<Float> getBlockExplosionResistance(
				Explosion explosion, BlockGetter blockGetter, BlockPos blockPos, BlockState blockStatex, FluidState fluidState
			) {
				return blockPos.equals(blockPos) && bl2
					? Optional.of(Blocks.WATER.getExplosionResistance())
					: super.getBlockExplosionResistance(explosion, blockGetter, blockPos, blockStatex, fluidState);
			}
		};
		Vec3 vec3 = blockPos.getCenter();
		serverLevel.explode(
			null, serverLevel.damageSources().badRespawnPointExplosion(vec3), explosionDamageCalculator, vec3, 5.0F, true, Level.ExplosionInteraction.BLOCK
		);
	}

	public static boolean canSetSpawn(ServerLevel serverLevel, BlockPos blockPos) {
		return serverLevel.environmentAttributes().getValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, blockPos);
	}

	public static void charge(@Nullable Entity entity, Level level, BlockPos blockPos, BlockState blockState) {
		BlockState blockState2 = blockState.setValue(CHARGE, blockState.getValue(CHARGE) + 1);
		level.setBlock(blockPos, blockState2, 3);
		level.gameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Context.of(entity, blockState2));
		level.playSound(null, blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.0F, 1.0F);
	}

	@Override
	public void animateTick(BlockState blockState, Level level, BlockPos blockPos, RandomSource randomSource) {
		if (blockState.getValue(CHARGE) != 0) {
			if (randomSource.nextInt(100) == 0) {
				level.playLocalSound(blockPos, SoundEvents.RESPAWN_ANCHOR_AMBIENT, SoundSource.BLOCKS, 1.0F, 1.0F, false);
			}

			double d = blockPos.getX() + 0.5 + (0.5 - randomSource.nextDouble());
			double e = blockPos.getY() + 1.0;
			double f = blockPos.getZ() + 0.5 + (0.5 - randomSource.nextDouble());
			double g = randomSource.nextFloat() * 0.04;
			level.addParticle(ParticleTypes.REVERSE_PORTAL, d, e, f, 0.0, g, 0.0);
		}
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(CHARGE);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState blockState) {
		return true;
	}

	public static int getScaledChargeLevel(BlockState blockState, int i) {
		return Mth.floor((blockState.getValue(CHARGE) - 0) / 4.0F * i);
	}

	@Override
	protected int getAnalogOutputSignal(BlockState blockState, Level level, BlockPos blockPos, Direction direction) {
		return getScaledChargeLevel(blockState, 15);
	}

	public static Optional<Vec3> findStandUpPosition(EntityType<?> entityType, CollisionGetter collisionGetter, BlockPos blockPos) {
		Optional<Vec3> optional = findStandUpPosition(entityType, collisionGetter, blockPos, true);
		return optional.isPresent() ? optional : findStandUpPosition(entityType, collisionGetter, blockPos, false);
	}

	private static Optional<Vec3> findStandUpPosition(EntityType<?> entityType, CollisionGetter collisionGetter, BlockPos blockPos, boolean bl) {
		BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

		for (Vec3i vec3i : RESPAWN_OFFSETS) {
			mutableBlockPos.set(blockPos).move(vec3i);
			Vec3 vec3 = DismountHelper.findSafeDismountLocation(entityType, collisionGetter, mutableBlockPos, bl);
			if (vec3 != null) {
				return Optional.of(vec3);
			}
		}

		return Optional.empty();
	}

	@Override
	protected boolean isPathfindable(BlockState blockState, PathComputationType pathComputationType) {
		return false;
	}
}
