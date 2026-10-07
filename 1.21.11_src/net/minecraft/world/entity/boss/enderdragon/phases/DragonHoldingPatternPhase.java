package net.minecraft.world.entity.boss.enderdragon.phases;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class DragonHoldingPatternPhase extends AbstractDragonPhaseInstance {
	private static final TargetingConditions NEW_TARGET_TARGETING = TargetingConditions.forCombat().ignoreLineOfSight();
	private @Nullable Path currentPath;
	private @Nullable Vec3 targetLocation;
	private boolean clockwise;

	public DragonHoldingPatternPhase(EnderDragon enderDragon) {
		super(enderDragon);
	}

	@Override
	public EnderDragonPhase<DragonHoldingPatternPhase> getPhase() {
		return EnderDragonPhase.HOLDING_PATTERN;
	}

	@Override
	public void doServerTick(ServerLevel serverLevel) {
		double d = this.targetLocation == null ? 0.0 : this.targetLocation.distanceToSqr(this.dragon.getX(), this.dragon.getY(), this.dragon.getZ());
		if (d < 100.0 || d > 22500.0 || this.dragon.horizontalCollision || this.dragon.verticalCollision) {
			this.findNewTarget(serverLevel);
		}
	}

	@Override
	public void begin() {
		this.currentPath = null;
		this.targetLocation = null;
	}

	@Override
	public @Nullable Vec3 getFlyTargetLocation() {
		return this.targetLocation;
	}

	private void findNewTarget(ServerLevel serverLevel) {
		if (this.currentPath != null && this.currentPath.isDone()) {
			BlockPos blockPos = serverLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EndPodiumFeature.getLocation(this.dragon.getFightOrigin()));
			int i = this.dragon.getDragonFight() == null ? 0 : this.dragon.getDragonFight().getCrystalsAlive();
			if (this.dragon.getRandom().nextInt(i + 3) == 0) {
				this.dragon.getPhaseManager().setPhase(EnderDragonPhase.LANDING_APPROACH);
				return;
			}

			Player player = serverLevel.getNearestPlayer(NEW_TARGET_TARGETING, this.dragon, blockPos.getX(), blockPos.getY(), blockPos.getZ());
			double d;
			if (player != null) {
				d = blockPos.distToCenterSqr(player.position()) / 512.0;
			} else {
				d = 64.0;
			}

			if (player != null && (this.dragon.getRandom().nextInt((int)(d + 2.0)) == 0 || this.dragon.getRandom().nextInt(i + 2) == 0)) {
				this.strafePlayer(player);
				return;
			}
		}

		if (this.currentPath == null || this.currentPath.isDone()) {
			int j = this.dragon.findClosestNode();
			int i = j;
			if (this.dragon.getRandom().nextInt(8) == 0) {
				this.clockwise = !this.clockwise;
				i += 6;
			}

			if (this.clockwise) {
				i++;
			} else {
				i--;
			}

			if (this.dragon.getDragonFight() != null && this.dragon.getDragonFight().getCrystalsAlive() >= 0) {
				i %= 12;
				if (i < 0) {
					i += 12;
				}
			} else {
				i -= 12;
				i &= 7;
				i += 12;
			}

			this.currentPath = this.dragon.findPath(j, i, null);
			if (this.currentPath != null) {
				this.currentPath.advance();
			}
		}

		this.navigateToNextPathNode();
	}

	private void strafePlayer(Player player) {
		this.dragon.getPhaseManager().setPhase(EnderDragonPhase.STRAFE_PLAYER);
		this.dragon.getPhaseManager().getPhase(EnderDragonPhase.STRAFE_PLAYER).setTarget(player);
	}

	private void navigateToNextPathNode() {
		if (this.currentPath != null && !this.currentPath.isDone()) {
			Vec3i vec3i = this.currentPath.getNextNodePos();
			this.currentPath.advance();
			double d = vec3i.getX();
			double e = vec3i.getZ();

			double f;
			do {
				f = vec3i.getY() + this.dragon.getRandom().nextFloat() * 20.0F;
			} while (f < vec3i.getY());

			this.targetLocation = new Vec3(d, f, e);
		}
	}

	@Override
	public void onCrystalDestroyed(EndCrystal endCrystal, BlockPos blockPos, DamageSource damageSource, @Nullable Player player) {
		if (player != null && this.dragon.canAttack(player)) {
			this.strafePlayer(player);
		}
	}
}
