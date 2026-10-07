package net.minecraft.client.multiplayer;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class ClientExplosionTracker {
	private static final int MAX_PARTICLES_PER_TICK = 512;
	private final List<ClientExplosionTracker.ExplosionInfo> explosions = new ArrayList<>();

	public void track(Vec3 vec3, float f, int i, WeightedList<ExplosionParticleInfo> weightedList) {
		if (!weightedList.isEmpty()) {
			this.explosions.add(new ClientExplosionTracker.ExplosionInfo(vec3, f, i, weightedList));
		}
	}

	public void tick(ClientLevel clientLevel) {
		if (Minecraft.getInstance().options.particles().get() != ParticleStatus.ALL) {
			this.explosions.clear();
		} else {
			int i = WeightedRandom.getTotalWeight(this.explosions, ClientExplosionTracker.ExplosionInfo::blockCount);
			int j = Math.min(i, 512);

			for (int k = 0; k < j; k++) {
				WeightedRandom.getRandomItem(clientLevel.getRandom(), this.explosions, i, ClientExplosionTracker.ExplosionInfo::blockCount)
					.ifPresent(explosionInfo -> this.addParticle(clientLevel, explosionInfo));
			}

			this.explosions.clear();
		}
	}

	private void addParticle(ClientLevel clientLevel, ClientExplosionTracker.ExplosionInfo explosionInfo) {
		RandomSource randomSource = clientLevel.getRandom();
		Vec3 vec3 = explosionInfo.center();
		Vec3 vec32 = new Vec3(randomSource.nextFloat() * 2.0F - 1.0F, randomSource.nextFloat() * 2.0F - 1.0F, randomSource.nextFloat() * 2.0F - 1.0F).normalize();
		float f = (float)Math.cbrt(randomSource.nextFloat()) * explosionInfo.radius();
		Vec3 vec33 = vec32.scale(f);
		Vec3 vec34 = vec3.add(vec33);
		if (clientLevel.getBlockState(BlockPos.containing(vec34)).isAir()) {
			float g = 0.5F / (f / explosionInfo.radius() + 0.1F) * randomSource.nextFloat() * randomSource.nextFloat() + 0.3F;
			ExplosionParticleInfo explosionParticleInfo = explosionInfo.blockParticles.getRandomOrThrow(randomSource);
			Vec3 vec35 = vec3.add(vec33.scale(explosionParticleInfo.scaling()));
			Vec3 vec36 = vec32.scale(g * explosionParticleInfo.speed());
			clientLevel.addParticle(explosionParticleInfo.particle(), vec35.x(), vec35.y(), vec35.z(), vec36.x(), vec36.y(), vec36.z());
		}
	}

	@Environment(EnvType.CLIENT)
	record ExplosionInfo(Vec3 center, float radius, int blockCount, WeightedList<ExplosionParticleInfo> blockParticles) {
	}
}
