package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class PanicGoal extends Goal {
	public static final int WATER_CHECK_DISTANCE_VERTICAL = 1;
	protected final PathfinderMob mob;
	protected final double speedModifier;
	protected double posX;
	protected double posY;
	protected double posZ;
	protected boolean isRunning;
	private final Function<PathfinderMob, TagKey<DamageType>> panicCausingDamageTypes;

	public PanicGoal(PathfinderMob pathfinderMob, double d) {
		this(pathfinderMob, d, DamageTypeTags.PANIC_CAUSES);
	}

	public PanicGoal(PathfinderMob pathfinderMob, double d, TagKey<DamageType> tagKey) {
		this(pathfinderMob, d, pathfinderMobx -> tagKey);
	}

	public PanicGoal(PathfinderMob pathfinderMob, double d, Function<PathfinderMob, TagKey<DamageType>> function) {
		this.mob = pathfinderMob;
		this.speedModifier = d;
		this.panicCausingDamageTypes = function;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (!this.shouldPanic()) {
			return false;
		}

		if (this.mob.isOnFire()) {
			BlockPos blockPos = this.lookForWater(this.mob.level(), this.mob, 5);
			if (blockPos != null) {
				this.posX = blockPos.getX();
				this.posY = blockPos.getY();
				this.posZ = blockPos.getZ();
				return true;
			}
		}

		return this.findRandomPosition();
	}

	protected boolean shouldPanic() {
		return this.mob.getLastDamageSource() != null && this.mob.getLastDamageSource().is(this.panicCausingDamageTypes.apply(this.mob));
	}

	protected boolean findRandomPosition() {
		Vec3 vec3 = DefaultRandomPos.getPos(this.mob, 5, 4);
		if (vec3 == null) {
			return false;
		}

		this.posX = vec3.x;
		this.posY = vec3.y;
		this.posZ = vec3.z;
		return true;
	}

	public boolean isRunning() {
		return this.isRunning;
	}

	@Override
	public void start() {
		this.mob.getNavigation().moveTo(this.posX, this.posY, this.posZ, this.speedModifier);
		this.isRunning = true;
	}

	@Override
	public void stop() {
		this.isRunning = false;
	}

	@Override
	public boolean canContinueToUse() {
		return !this.mob.getNavigation().isDone();
	}

	protected @Nullable BlockPos lookForWater(BlockGetter blockGetter, Entity entity, int i) {
		BlockPos blockPos = entity.blockPosition();
		return !blockGetter.getBlockState(blockPos).getCollisionShape(blockGetter, blockPos).isEmpty()
			? null
			: BlockPos.findClosestMatch(entity.blockPosition(), i, 1, blockPosx -> blockGetter.getFluidState(blockPosx).is(FluidTags.WATER)).orElse(null);
	}
}
