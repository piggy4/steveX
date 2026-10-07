package net.minecraft.world.entity.ai.village;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class VillageSiege implements CustomSpawner {
	private static final Logger LOGGER = LogUtils.getLogger();
	private boolean hasSetupSiege;
	private VillageSiege.State siegeState = VillageSiege.State.SIEGE_DONE;
	private int zombiesToSpawn;
	private int nextSpawnTime;
	private int spawnX;
	private int spawnY;
	private int spawnZ;

	@Override
	public void tick(ServerLevel serverLevel, boolean bl) {
		if (!serverLevel.isBrightOutside() && bl) {
			long l = serverLevel.getDayTime() % 24000L;
			if (l == 18000L) {
				this.siegeState = serverLevel.random.nextInt(10) == 0 ? VillageSiege.State.SIEGE_TONIGHT : VillageSiege.State.SIEGE_DONE;
			}

			if (this.siegeState != VillageSiege.State.SIEGE_DONE) {
				if (!this.hasSetupSiege) {
					if (!this.tryToSetupSiege(serverLevel)) {
						return;
					}

					this.hasSetupSiege = true;
				}

				if (this.nextSpawnTime > 0) {
					this.nextSpawnTime--;
				} else {
					this.nextSpawnTime = 2;
					if (this.zombiesToSpawn > 0) {
						this.trySpawn(serverLevel);
						this.zombiesToSpawn--;
					} else {
						this.siegeState = VillageSiege.State.SIEGE_DONE;
					}
				}
			}
		} else {
			this.siegeState = VillageSiege.State.SIEGE_DONE;
			this.hasSetupSiege = false;
		}
	}

	private boolean tryToSetupSiege(ServerLevel serverLevel) {
		for (Player player : serverLevel.players()) {
			if (!player.isSpectator()) {
				BlockPos blockPos = player.blockPosition();
				if (serverLevel.isVillage(blockPos) && !serverLevel.getBiome(blockPos).is(BiomeTags.WITHOUT_ZOMBIE_SIEGES)) {
					for (int i = 0; i < 10; i++) {
						float f = serverLevel.random.nextFloat() * (float) (Math.PI * 2);
						this.spawnX = blockPos.getX() + Mth.floor(Mth.cos(f) * 32.0F);
						this.spawnY = blockPos.getY();
						this.spawnZ = blockPos.getZ() + Mth.floor(Mth.sin(f) * 32.0F);
						if (this.findRandomSpawnPos(serverLevel, new BlockPos(this.spawnX, this.spawnY, this.spawnZ)) != null) {
							this.nextSpawnTime = 0;
							this.zombiesToSpawn = 20;
							break;
						}
					}

					return true;
				}
			}
		}

		return false;
	}

	private void trySpawn(ServerLevel serverLevel) {
		Vec3 vec3 = this.findRandomSpawnPos(serverLevel, new BlockPos(this.spawnX, this.spawnY, this.spawnZ));
		if (vec3 != null) {
			Zombie zombie;
			try {
				zombie = new Zombie(serverLevel);
				zombie.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(zombie.blockPosition()), EntitySpawnReason.EVENT, null);
			} catch (Exception exception) {
				LOGGER.warn("Failed to create zombie for village siege at {}", vec3, exception);
				return;
			}

			zombie.snapTo(vec3.x, vec3.y, vec3.z, serverLevel.random.nextFloat() * 360.0F, 0.0F);
			serverLevel.addFreshEntityWithPassengers(zombie);
		}
	}

	private @Nullable Vec3 findRandomSpawnPos(ServerLevel serverLevel, BlockPos blockPos) {
		for (int i = 0; i < 10; i++) {
			int j = blockPos.getX() + serverLevel.random.nextInt(16) - 8;
			int k = blockPos.getZ() + serverLevel.random.nextInt(16) - 8;
			int l = serverLevel.getHeight(Heightmap.Types.WORLD_SURFACE, j, k);
			BlockPos blockPos2 = new BlockPos(j, l, k);
			if (serverLevel.isVillage(blockPos2)
				&& Monster.checkMonsterSpawnRules(EntityType.ZOMBIE, serverLevel, EntitySpawnReason.EVENT, blockPos2, serverLevel.random)) {
				return Vec3.atBottomCenterOf(blockPos2);
			}
		}

		return null;
	}

	enum State {
		SIEGE_CAN_ACTIVATE,
		SIEGE_TONIGHT,
		SIEGE_DONE;
	}
}
