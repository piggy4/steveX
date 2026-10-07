package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class BaseFireBlock extends Block {
	private static final int SECONDS_ON_FIRE = 8;
	private static final int MIN_FIRE_TICKS_TO_ADD = 1;
	private static final int MAX_FIRE_TICKS_TO_ADD = 3;
	private final float fireDamage;
	protected static final VoxelShape SHAPE = Block.column(16.0, 0.0, 1.0);

	public BaseFireBlock(BlockBehaviour.Properties properties, float f) {
		super(properties);
		this.fireDamage = f;
	}

	@Override
	protected abstract MapCodec<? extends BaseFireBlock> codec();

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
		return getState(blockPlaceContext.getLevel(), blockPlaceContext.getClickedPos());
	}

	public static BlockState getState(BlockGetter blockGetter, BlockPos blockPos) {
		BlockPos blockPos2 = blockPos.below();
		BlockState blockState = blockGetter.getBlockState(blockPos2);
		return SoulFireBlock.canSurviveOnBlock(blockState)
			? Blocks.SOUL_FIRE.defaultBlockState()
			: ((FireBlock)Blocks.FIRE).getStateForPlacement(blockGetter, blockPos);
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return SHAPE;
	}

	@Override
	public void animateTick(BlockState blockState, Level level, BlockPos blockPos, RandomSource randomSource) {
		if (randomSource.nextInt(24) == 0) {
			level.playLocalSound(
				blockPos.getX() + 0.5,
				blockPos.getY() + 0.5,
				blockPos.getZ() + 0.5,
				SoundEvents.FIRE_AMBIENT,
				SoundSource.BLOCKS,
				1.0F + randomSource.nextFloat(),
				randomSource.nextFloat() * 0.7F + 0.3F,
				false
			);
		}

		BlockPos blockPos2 = blockPos.below();
		BlockState blockState2 = level.getBlockState(blockPos2);
		if (!this.canBurn(blockState2) && !blockState2.isFaceSturdy(level, blockPos2, Direction.UP)) {
			if (this.canBurn(level.getBlockState(blockPos.west()))) {
				for (int i = 0; i < 2; i++) {
					double d = blockPos.getX() + randomSource.nextDouble() * 0.1F;
					double e = blockPos.getY() + randomSource.nextDouble();
					double f = blockPos.getZ() + randomSource.nextDouble();
					level.addParticle(ParticleTypes.LARGE_SMOKE, d, e, f, 0.0, 0.0, 0.0);
				}
			}

			if (this.canBurn(level.getBlockState(blockPos.east()))) {
				for (int i = 0; i < 2; i++) {
					double d = blockPos.getX() + 1 - randomSource.nextDouble() * 0.1F;
					double e = blockPos.getY() + randomSource.nextDouble();
					double f = blockPos.getZ() + randomSource.nextDouble();
					level.addParticle(ParticleTypes.LARGE_SMOKE, d, e, f, 0.0, 0.0, 0.0);
				}
			}

			if (this.canBurn(level.getBlockState(blockPos.north()))) {
				for (int i = 0; i < 2; i++) {
					double d = blockPos.getX() + randomSource.nextDouble();
					double e = blockPos.getY() + randomSource.nextDouble();
					double f = blockPos.getZ() + randomSource.nextDouble() * 0.1F;
					level.addParticle(ParticleTypes.LARGE_SMOKE, d, e, f, 0.0, 0.0, 0.0);
				}
			}

			if (this.canBurn(level.getBlockState(blockPos.south()))) {
				for (int i = 0; i < 2; i++) {
					double d = blockPos.getX() + randomSource.nextDouble();
					double e = blockPos.getY() + randomSource.nextDouble();
					double f = blockPos.getZ() + 1 - randomSource.nextDouble() * 0.1F;
					level.addParticle(ParticleTypes.LARGE_SMOKE, d, e, f, 0.0, 0.0, 0.0);
				}
			}

			if (this.canBurn(level.getBlockState(blockPos.above()))) {
				for (int i = 0; i < 2; i++) {
					double d = blockPos.getX() + randomSource.nextDouble();
					double e = blockPos.getY() + 1 - randomSource.nextDouble() * 0.1F;
					double f = blockPos.getZ() + randomSource.nextDouble();
					level.addParticle(ParticleTypes.LARGE_SMOKE, d, e, f, 0.0, 0.0, 0.0);
				}
			}
		} else {
			for (int i = 0; i < 3; i++) {
				double d = blockPos.getX() + randomSource.nextDouble();
				double e = blockPos.getY() + randomSource.nextDouble() * 0.5 + 0.5;
				double f = blockPos.getZ() + randomSource.nextDouble();
				level.addParticle(ParticleTypes.LARGE_SMOKE, d, e, f, 0.0, 0.0, 0.0);
			}
		}
	}

	protected abstract boolean canBurn(BlockState blockState);

	@Override
	protected void entityInside(
		BlockState blockState, Level level, BlockPos blockPos, Entity entity, InsideBlockEffectApplier insideBlockEffectApplier, boolean bl
	) {
		insideBlockEffectApplier.apply(InsideBlockEffectType.CLEAR_FREEZE);
		insideBlockEffectApplier.apply(InsideBlockEffectType.FIRE_IGNITE);
		insideBlockEffectApplier.runAfter(InsideBlockEffectType.FIRE_IGNITE, entityx -> entityx.hurt(entityx.level().damageSources().inFire(), this.fireDamage));
	}

	public static void fireIgnite(Entity entity) {
		if (!entity.fireImmune()) {
			if (entity.getRemainingFireTicks() < 0) {
				entity.setRemainingFireTicks(entity.getRemainingFireTicks() + 1);
			} else if (entity instanceof ServerPlayer) {
				int i = entity.level().getRandom().nextInt(1, 3);
				entity.setRemainingFireTicks(entity.getRemainingFireTicks() + i);
			}

			if (entity.getRemainingFireTicks() >= 0) {
				entity.igniteForSeconds(8.0F);
			}
		}
	}

	@Override
	protected void onPlace(BlockState blockState, Level level, BlockPos blockPos, BlockState blockState2, boolean bl) {
		if (!blockState2.is(blockState.getBlock())) {
			if (inPortalDimension(level)) {
				Optional<PortalShape> optional = PortalShape.findEmptyPortalShape(level, blockPos, Direction.Axis.X);
				if (optional.isPresent()) {
					optional.get().createPortalBlocks(level);
					return;
				}
			}

			if (!blockState.canSurvive(level, blockPos)) {
				level.removeBlock(blockPos, false);
			}
		}
	}

	private static boolean inPortalDimension(Level level) {
		return level.dimension() == Level.OVERWORLD || level.dimension() == Level.NETHER;
	}

	@Override
	protected void spawnDestroyParticles(Level level, Player player, BlockPos blockPos, BlockState blockState) {
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos blockPos, BlockState blockState, Player player) {
		if (!level.isClientSide()) {
			level.levelEvent(null, 1009, blockPos, 0);
		}

		return super.playerWillDestroy(level, blockPos, blockState, player);
	}

	public static boolean canBePlacedAt(Level level, BlockPos blockPos, Direction direction) {
		BlockState blockState = level.getBlockState(blockPos);
		return !blockState.isAir() ? false : getState(level, blockPos).canSurvive(level, blockPos) || isPortal(level, blockPos, direction);
	}

	private static boolean isPortal(Level level, BlockPos blockPos, Direction direction) {
		if (!inPortalDimension(level)) {
			return false;
		}

		BlockPos.MutableBlockPos mutableBlockPos = blockPos.mutable();
		boolean bl = false;

		for (Direction direction2 : Direction.values()) {
			if (level.getBlockState(mutableBlockPos.set(blockPos).move(direction2)).is(Blocks.OBSIDIAN)) {
				bl = true;
				break;
			}
		}

		if (!bl) {
			return false;
		}

		Direction.Axis axis = direction.getAxis().isHorizontal() ? direction.getCounterClockWise().getAxis() : Direction.Plane.HORIZONTAL.getRandomAxis(level.random);
		return PortalShape.findEmptyPortalShape(level, blockPos, axis).isPresent();
	}
}
