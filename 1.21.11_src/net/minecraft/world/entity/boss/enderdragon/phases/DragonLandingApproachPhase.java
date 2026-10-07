package net.minecraft.world.entity.boss.enderdragon.phases;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class DragonLandingApproachPhase extends AbstractDragonPhaseInstance {
	private static final TargetingConditions NEAR_EGG_TARGETING = TargetingConditions.forCombat().ignoreLineOfSight();
	private @Nullable Path currentPath;
	private @Nullable Vec3 targetLocation;

	public DragonLandingApproachPhase(EnderDragon enderDragon) {
		super(enderDragon);
	}

	@Override
	public EnderDragonPhase<DragonLandingApproachPhase> getPhase() {
		return EnderDragonPhase.LANDING_APPROACH;
	}

	@Override
	public void begin() {
		this.currentPath = null;
		this.targetLocation = null;
	}

	@Override
	public void doServerTick(ServerLevel serverLevel) {
		double d = this.targetLocation == null ? 0.0 : this.targetLocation.distanceToSqr(this.dragon.getX(), this.dragon.getY(), this.dragon.getZ());
		if (d < 100.0 || d > 22500.0 || this.dragon.horizontalCollision || this.dragon.verticalCollision) {
			this.findNewTarget(serverLevel);
		}
	}

	@Override
	public @Nullable Vec3 getFlyTargetLocation() {
		return this.targetLocation;
	}

	private void findNewTarget(ServerLevel serverLevel) {
		if (this.currentPath == null || this.currentPath.isDone()) {
			int i = this.dragon.findClosestNode();
			BlockPos blockPos = serverLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EndPodiumFeature.getLocation(this.dragon.getFightOrigin()));
			Player player = serverLevel.getNearestPlayer(NEAR_EGG_TARGETING, this.dragon, blockPos.getX(), blockPos.getY(), blockPos.getZ());
			int j;
			if (player != null) {
				Vec3 vec3 = new Vec3(player.getX(), 0.0, player.getZ()).normalize();
				j = this.dragon.findClosestNode(-vec3.x * 40.0, 105.0, -vec3.z * 40.0);
			} else {
				j = this.dragon.findClosestNode(40.0, blockPos.getY(), 0.0);
			}

			Node node = new Node(blockPos.getX(), blockPos.getY(), blockPos.getZ());
			this.currentPath = this.dragon.findPath(i, j, node);
			if (this.currentPath != null) {
				this.currentPath.advance();
			}
		}

		this.navigateToNextPathNode();
		if (this.currentPath != null && this.currentPath.isDone()) {
			this.dragon.getPhaseManager().setPhase(EnderDragonPhase.LANDING);
		}
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
}
