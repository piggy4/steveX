package net.minecraft.world.entity.animal;

import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class Animal extends AgeableMob {
	protected static final int PARENT_AGE_AFTER_BREEDING = 6000;
	private static final int DEFAULT_IN_LOVE_TIME = 0;
	private int inLove = 0;
	private @Nullable EntityReference<ServerPlayer> loveCause;

	protected Animal(EntityType<? extends Animal> entityType, Level level) {
		super(entityType, level);
		this.setPathfindingMalus(PathType.DANGER_FIRE, 16.0F);
		this.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
	}

	public static AttributeSupplier.Builder createAnimalAttributes() {
		return Mob.createMobAttributes().add(Attributes.TEMPT_RANGE, 10.0);
	}

	@Override
	protected void customServerAiStep(ServerLevel serverLevel) {
		if (this.getAge() != 0) {
			this.inLove = 0;
		}

		super.customServerAiStep(serverLevel);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.getAge() != 0) {
			this.inLove = 0;
		}

		if (this.inLove > 0) {
			this.inLove--;
			if (this.inLove % 10 == 0) {
				double d = this.random.nextGaussian() * 0.02;
				double e = this.random.nextGaussian() * 0.02;
				double f = this.random.nextGaussian() * 0.02;
				this.level().addParticle(ParticleTypes.HEART, this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0), d, e, f);
			}
		}
	}

	@Override
	protected void actuallyHurt(ServerLevel serverLevel, DamageSource damageSource, float f) {
		this.resetLove();
		super.actuallyHurt(serverLevel, damageSource, f);
	}

	@Override
	public float getWalkTargetValue(BlockPos blockPos, LevelReader levelReader) {
		return levelReader.getBlockState(blockPos.below()).is(Blocks.GRASS_BLOCK) ? 10.0F : levelReader.getPathfindingCostFromLightLevels(blockPos);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.putInt("InLove", this.inLove);
		EntityReference.store(this.loveCause, valueOutput, "LoveCause");
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.inLove = valueInput.getIntOr("InLove", 0);
		this.loveCause = EntityReference.read(valueInput, "LoveCause");
	}

	public static boolean checkAnimalSpawnRules(
		EntityType<? extends Animal> entityType, LevelAccessor levelAccessor, EntitySpawnReason entitySpawnReason, BlockPos blockPos, RandomSource randomSource
	) {
		boolean bl = EntitySpawnReason.ignoresLightRequirements(entitySpawnReason) || isBrightEnoughToSpawn(levelAccessor, blockPos);
		return levelAccessor.getBlockState(blockPos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON) && bl;
	}

	protected static boolean isBrightEnoughToSpawn(BlockAndTintGetter blockAndTintGetter, BlockPos blockPos) {
		return blockAndTintGetter.getRawBrightness(blockPos, 0) > 8;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 120;
	}

	@Override
	public boolean removeWhenFarAway(double d) {
		return false;
	}

	@Override
	protected int getBaseExperienceReward(ServerLevel serverLevel) {
		return 1 + this.random.nextInt(3);
	}

	public abstract boolean isFood(ItemStack itemStack);

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand interactionHand) {
		ItemStack itemStack = player.getItemInHand(interactionHand);
		if (this.isFood(itemStack)) {
			int i = this.getAge();
			if (player instanceof ServerPlayer serverPlayer && i == 0 && this.canFallInLove()) {
				this.usePlayerItem(player, interactionHand, itemStack);
				this.setInLove(serverPlayer);
				this.playEatingSound();
				return InteractionResult.SUCCESS_SERVER;
			}

			if (this.isBaby()) {
				this.usePlayerItem(player, interactionHand, itemStack);
				this.ageUp(getSpeedUpSecondsWhenFeeding(-i), true);
				this.playEatingSound();
				return InteractionResult.SUCCESS;
			}

			if (this.level().isClientSide()) {
				return InteractionResult.CONSUME;
			}
		}

		return super.mobInteract(player, interactionHand);
	}

	protected void playEatingSound() {
	}

	public boolean canFallInLove() {
		return this.inLove <= 0;
	}

	public void setInLove(@Nullable Player player) {
		this.inLove = 600;
		if (player instanceof ServerPlayer serverPlayer) {
			this.loveCause = EntityReference.of(serverPlayer);
		}

		this.level().broadcastEntityEvent(this, (byte)18);
	}

	public void setInLoveTime(int i) {
		this.inLove = i;
	}

	public int getInLoveTime() {
		return this.inLove;
	}

	public @Nullable ServerPlayer getLoveCause() {
		return EntityReference.get(this.loveCause, this.level(), ServerPlayer.class);
	}

	public boolean isInLove() {
		return this.inLove > 0;
	}

	public void resetLove() {
		this.inLove = 0;
	}

	public boolean canMate(Animal animal) {
		if (animal == this) {
			return false;
		} else {
			return animal.getClass() != this.getClass() ? false : this.isInLove() && animal.isInLove();
		}
	}

	public void spawnChildFromBreeding(ServerLevel serverLevel, Animal animal) {
		AgeableMob ageableMob = this.getBreedOffspring(serverLevel, animal);
		if (ageableMob != null) {
			ageableMob.setBaby(true);
			ageableMob.snapTo(this.getX(), this.getY(), this.getZ(), 0.0F, 0.0F);
			this.finalizeSpawnChildFromBreeding(serverLevel, animal, ageableMob);
			serverLevel.addFreshEntityWithPassengers(ageableMob);
		}
	}

	public void finalizeSpawnChildFromBreeding(ServerLevel serverLevel, Animal animal, @Nullable AgeableMob ageableMob) {
		Optional.ofNullable(this.getLoveCause()).or(() -> Optional.ofNullable(animal.getLoveCause())).ifPresent(serverPlayer -> {
			serverPlayer.awardStat(Stats.ANIMALS_BRED);
			CriteriaTriggers.BRED_ANIMALS.trigger(serverPlayer, this, animal, ageableMob);
		});
		this.setAge(6000);
		animal.setAge(6000);
		this.resetLove();
		animal.resetLove();
		serverLevel.broadcastEntityEvent(this, (byte)18);
		if (serverLevel.getGameRules().get(GameRules.MOB_DROPS)) {
			serverLevel.addFreshEntity(new ExperienceOrb(serverLevel, this.getX(), this.getY(), this.getZ(), this.getRandom().nextInt(7) + 1));
		}
	}

	@Override
	public void handleEntityEvent(byte b) {
		if (b == 18) {
			for (int i = 0; i < 7; i++) {
				double d = this.random.nextGaussian() * 0.02;
				double e = this.random.nextGaussian() * 0.02;
				double f = this.random.nextGaussian() * 0.02;
				this.level().addParticle(ParticleTypes.HEART, this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0), d, e, f);
			}
		} else {
			super.handleEntityEvent(b);
		}
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity livingEntity) {
		Direction direction = this.getMotionDirection();
		if (direction.getAxis() == Direction.Axis.Y) {
			return super.getDismountLocationForPassenger(livingEntity);
		}

		int[][] is = DismountHelper.offsetsForDirection(direction);
		BlockPos blockPos = this.blockPosition();
		BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

		for (Pose pose : livingEntity.getDismountPoses()) {
			AABB aABB = livingEntity.getLocalBoundsForPose(pose);

			for (int[] js : is) {
				mutableBlockPos.set(blockPos.getX() + js[0], blockPos.getY(), blockPos.getZ() + js[1]);
				double d = this.level().getBlockFloorHeight(mutableBlockPos);
				if (DismountHelper.isBlockFloorValid(d)) {
					Vec3 vec3 = Vec3.upFromBottomCenterOf(mutableBlockPos, d);
					if (DismountHelper.canDismountTo(this.level(), livingEntity, aABB.move(vec3))) {
						livingEntity.setPose(pose);
						return vec3;
					}
				}
			}
		}

		return super.getDismountLocationForPassenger(livingEntity);
	}
}
