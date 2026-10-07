package net.minecraft.world.entity;

import com.google.common.collect.Sets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class LightningBolt extends Entity {
	private static final int START_LIFE = 2;
	private static final double DAMAGE_RADIUS = 3.0;
	private static final double DETECTION_RADIUS = 15.0;
	private int life;
	public long seed;
	private int flashes;
	private boolean visualOnly;
	private @Nullable ServerPlayer cause;
	private final Set<Entity> hitEntities = Sets.newHashSet();
	private int blocksSetOnFire;

	public LightningBolt(EntityType<? extends LightningBolt> entityType, Level level) {
		super(entityType, level);
		this.life = 2;
		this.seed = this.random.nextLong();
		this.flashes = this.random.nextInt(3) + 1;
	}

	public void setVisualOnly(boolean bl) {
		this.visualOnly = bl;
	}

	@Override
	public SoundSource getSoundSource() {
		return SoundSource.WEATHER;
	}

	public @Nullable ServerPlayer getCause() {
		return this.cause;
	}

	public void setCause(@Nullable ServerPlayer serverPlayer) {
		this.cause = serverPlayer;
	}

	private void powerLightningRod() {
		BlockPos blockPos = this.getStrikePosition();
		BlockState blockState = this.level().getBlockState(blockPos);
		if (blockState.getBlock() instanceof LightningRodBlock lightningRodBlock) {
			lightningRodBlock.onLightningStrike(blockState, this.level(), blockPos);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.life == 2) {
			if (this.level().isClientSide()) {
				this.level()
					.playLocalSound(
						this.getX(), this.getY(), this.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 10000.0F, 0.8F + this.random.nextFloat() * 0.2F, false
					);
				this.level()
					.playLocalSound(
						this.getX(), this.getY(), this.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 2.0F, 0.5F + this.random.nextFloat() * 0.2F, false
					);
			} else {
				Difficulty difficulty = this.level().getDifficulty();
				if (difficulty == Difficulty.NORMAL || difficulty == Difficulty.HARD) {
					this.spawnFire(4);
				}

				this.powerLightningRod();
				clearCopperOnLightningStrike(this.level(), this.getStrikePosition());
				this.gameEvent(GameEvent.LIGHTNING_STRIKE);
			}
		}

		this.life--;
		if (this.life < 0) {
			if (this.flashes == 0) {
				if (this.level() instanceof ServerLevel) {
					List<Entity> list = this.level()
						.getEntities(
							this,
							new AABB(this.getX() - 15.0, this.getY() - 15.0, this.getZ() - 15.0, this.getX() + 15.0, this.getY() + 6.0 + 15.0, this.getZ() + 15.0),
							entityx -> entityx.isAlive() && !this.hitEntities.contains(entityx)
						);

					for (ServerPlayer serverPlayer : ((ServerLevel)this.level()).getPlayers(serverPlayerx -> serverPlayerx.distanceTo(this) < 256.0F)) {
						CriteriaTriggers.LIGHTNING_STRIKE.trigger(serverPlayer, this, list);
					}
				}

				this.discard();
			} else if (this.life < -this.random.nextInt(10)) {
				this.flashes--;
				this.life = 1;
				this.seed = this.random.nextLong();
				this.spawnFire(0);
			}
		}

		if (this.life >= 0) {
			if (!(this.level() instanceof ServerLevel)) {
				this.level().setSkyFlashTime(2);
			} else if (!this.visualOnly) {
				List<Entity> list = this.level()
					.getEntities(
						this, new AABB(this.getX() - 3.0, this.getY() - 3.0, this.getZ() - 3.0, this.getX() + 3.0, this.getY() + 6.0 + 3.0, this.getZ() + 3.0), Entity::isAlive
					);

				for (Entity entity : list) {
					entity.thunderHit((ServerLevel)this.level(), this);
				}

				this.hitEntities.addAll(list);
				if (this.cause != null) {
					CriteriaTriggers.CHANNELED_LIGHTNING.trigger(this.cause, list);
				}
			}
		}
	}

	private BlockPos getStrikePosition() {
		Vec3 vec3 = this.position();
		return BlockPos.containing(vec3.x, vec3.y - 1.0E-6, vec3.z);
	}

	private void spawnFire(int i) {
		if (!this.visualOnly && this.level() instanceof ServerLevel serverLevel) {
			BlockPos var7 = this.blockPosition();
			if (serverLevel.canSpreadFireAround(var7)) {
				BlockState blockState = BaseFireBlock.getState(serverLevel, var7);
				if (serverLevel.getBlockState(var7).isAir() && blockState.canSurvive(serverLevel, var7)) {
					serverLevel.setBlockAndUpdate(var7, blockState);
					this.blocksSetOnFire++;
				}

				for (int j = 0; j < i; j++) {
					BlockPos blockPos2 = var7.offset(this.random.nextInt(3) - 1, this.random.nextInt(3) - 1, this.random.nextInt(3) - 1);
					blockState = BaseFireBlock.getState(serverLevel, blockPos2);
					if (serverLevel.getBlockState(blockPos2).isAir() && blockState.canSurvive(serverLevel, blockPos2)) {
						serverLevel.setBlockAndUpdate(blockPos2, blockState);
						this.blocksSetOnFire++;
					}
				}
			}
		}
	}

	private static void clearCopperOnLightningStrike(Level level, BlockPos blockPos) {
		BlockState blockState = level.getBlockState(blockPos);
		boolean bl = HoneycombItem.WAX_OFF_BY_BLOCK.get().get(blockState.getBlock()) != null;
		boolean bl2 = blockState.getBlock() instanceof WeatheringCopper;
		if (bl2 || bl) {
			if (bl2) {
				level.setBlockAndUpdate(blockPos, WeatheringCopper.getFirst(level.getBlockState(blockPos)));
			}

			BlockPos.MutableBlockPos mutableBlockPos = blockPos.mutable();
			int i = level.random.nextInt(3) + 3;

			for (int j = 0; j < i; j++) {
				int k = level.random.nextInt(8) + 1;
				randomWalkCleaningCopper(level, blockPos, mutableBlockPos, k);
			}
		}
	}

	private static void randomWalkCleaningCopper(Level level, BlockPos blockPos, BlockPos.MutableBlockPos mutableBlockPos, int i) {
		mutableBlockPos.set(blockPos);

		for (int j = 0; j < i; j++) {
			Optional<BlockPos> optional = randomStepCleaningCopper(level, mutableBlockPos);
			if (optional.isEmpty()) {
				break;
			}

			mutableBlockPos.set(optional.get());
		}
	}

	private static Optional<BlockPos> randomStepCleaningCopper(Level level, BlockPos blockPos) {
		for (BlockPos blockPos2 : BlockPos.randomInCube(level.random, 10, blockPos, 1)) {
			BlockState blockState = level.getBlockState(blockPos2);
			if (blockState.getBlock() instanceof WeatheringCopper) {
				WeatheringCopper.getPrevious(blockState).ifPresent(blockStatex -> level.setBlockAndUpdate(blockPos2, blockStatex));
				level.levelEvent(3002, blockPos2, -1);
				return Optional.of(blockPos2);
			}
		}

		return Optional.empty();
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		double e = 64.0 * getViewScale();
		return d < e * e;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
	}

	public int getBlocksSetOnFire() {
		return this.blocksSetOnFire;
	}

	public Stream<Entity> getHitEntities() {
		return this.hitEntities.stream().filter(Entity::isAlive);
	}

	@Override
	public final boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float f) {
		return false;
	}
}
