package net.minecraft.world.level.block.entity.trialspawner;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.jspecify.annotations.Nullable;

public class TrialSpawnerStateData {
	private static final String TAG_SPAWN_DATA = "spawn_data";
	private static final String TAG_NEXT_MOB_SPAWNS_AT = "next_mob_spawns_at";
	private static final int DELAY_BETWEEN_PLAYER_SCANS = 20;
	private static final int TRIAL_OMEN_PER_BAD_OMEN_LEVEL = 18000;
	final Set<UUID> detectedPlayers = new HashSet<>();
	final Set<UUID> currentMobs = new HashSet<>();
	long cooldownEndsAt;
	long nextMobSpawnsAt;
	int totalMobsSpawned;
	Optional<SpawnData> nextSpawnData = Optional.empty();
	Optional<ResourceKey<LootTable>> ejectingLootTable = Optional.empty();
	private @Nullable Entity displayEntity;
	private @Nullable WeightedList<ItemStack> dispensing;
	double spin;
	double oSpin;

	public TrialSpawnerStateData.Packed pack() {
		return new TrialSpawnerStateData.Packed(
			Set.copyOf(this.detectedPlayers),
			Set.copyOf(this.currentMobs),
			this.cooldownEndsAt,
			this.nextMobSpawnsAt,
			this.totalMobsSpawned,
			this.nextSpawnData,
			this.ejectingLootTable
		);
	}

	public void apply(TrialSpawnerStateData.Packed packed) {
		this.detectedPlayers.clear();
		this.detectedPlayers.addAll(packed.detectedPlayers);
		this.currentMobs.clear();
		this.currentMobs.addAll(packed.currentMobs);
		this.cooldownEndsAt = packed.cooldownEndsAt;
		this.nextMobSpawnsAt = packed.nextMobSpawnsAt;
		this.totalMobsSpawned = packed.totalMobsSpawned;
		this.nextSpawnData = packed.nextSpawnData;
		this.ejectingLootTable = packed.ejectingLootTable;
	}

	public void reset() {
		this.currentMobs.clear();
		this.nextSpawnData = Optional.empty();
		this.resetStatistics();
	}

	public void resetStatistics() {
		this.detectedPlayers.clear();
		this.totalMobsSpawned = 0;
		this.nextMobSpawnsAt = 0L;
		this.cooldownEndsAt = 0L;
	}

	public boolean hasMobToSpawn(TrialSpawner trialSpawner, RandomSource randomSource) {
		boolean bl = this.getOrCreateNextSpawnData(trialSpawner, randomSource).getEntityToSpawn().getString("id").isPresent();
		return bl || !trialSpawner.activeConfig().spawnPotentialsDefinition().isEmpty();
	}

	public boolean hasFinishedSpawningAllMobs(TrialSpawnerConfig trialSpawnerConfig, int i) {
		return this.totalMobsSpawned >= trialSpawnerConfig.calculateTargetTotalMobs(i);
	}

	public boolean haveAllCurrentMobsDied() {
		return this.currentMobs.isEmpty();
	}

	public boolean isReadyToSpawnNextMob(ServerLevel serverLevel, TrialSpawnerConfig trialSpawnerConfig, int i) {
		return serverLevel.getGameTime() >= this.nextMobSpawnsAt && this.currentMobs.size() < trialSpawnerConfig.calculateTargetSimultaneousMobs(i);
	}

	public int countAdditionalPlayers(BlockPos blockPos) {
		if (this.detectedPlayers.isEmpty()) {
			Util.logAndPauseIfInIde("Trial Spawner at " + blockPos + " has no detected players");
		}

		return Math.max(0, this.detectedPlayers.size() - 1);
	}

	public void tryDetectPlayers(ServerLevel serverLevel, BlockPos blockPos, TrialSpawner trialSpawner) {
		boolean bl = (blockPos.asLong() + serverLevel.getGameTime()) % 20L != 0L;
		if (!bl) {
			if (!trialSpawner.getState().equals(TrialSpawnerState.COOLDOWN) || !trialSpawner.isOminous()) {
				List<UUID> list = trialSpawner.getPlayerDetector()
					.detect(serverLevel, trialSpawner.getEntitySelector(), blockPos, trialSpawner.getRequiredPlayerRange(), true);
				boolean bl2;
				if (!trialSpawner.isOminous() && !list.isEmpty()) {
					Optional<Pair<Player, Holder<MobEffect>>> optional = findPlayerWithOminousEffect(serverLevel, list);
					optional.ifPresent(pair -> {
						Player player = pair.getFirst();
						if (pair.getSecond() == MobEffects.BAD_OMEN) {
							transformBadOmenIntoTrialOmen(player);
						}

						serverLevel.levelEvent(3020, BlockPos.containing(player.getEyePosition()), 0);
						trialSpawner.applyOminous(serverLevel, blockPos);
					});
					bl2 = optional.isPresent();
				} else {
					bl2 = false;
				}

				if (!trialSpawner.getState().equals(TrialSpawnerState.COOLDOWN) || bl2) {
					boolean bl3 = trialSpawner.getStateData().detectedPlayers.isEmpty();
					List<UUID> list2 = bl3
						? list
						: trialSpawner.getPlayerDetector().detect(serverLevel, trialSpawner.getEntitySelector(), blockPos, trialSpawner.getRequiredPlayerRange(), false);
					if (this.detectedPlayers.addAll(list2)) {
						this.nextMobSpawnsAt = Math.max(serverLevel.getGameTime() + 40L, this.nextMobSpawnsAt);
						if (!bl2) {
							int i = trialSpawner.isOminous() ? 3019 : 3013;
							serverLevel.levelEvent(i, blockPos, this.detectedPlayers.size());
						}
					}
				}
			}
		}
	}

	private static Optional<Pair<Player, Holder<MobEffect>>> findPlayerWithOminousEffect(ServerLevel serverLevel, List<UUID> list) {
		Player player = null;

		for (UUID uUID : list) {
			Player player2 = serverLevel.getPlayerByUUID(uUID);
			if (player2 != null) {
				Holder<MobEffect> holder = MobEffects.TRIAL_OMEN;
				if (player2.hasEffect(holder)) {
					return Optional.of(Pair.of(player2, holder));
				}

				if (player2.hasEffect(MobEffects.BAD_OMEN)) {
					player = player2;
				}
			}
		}

		return Optional.ofNullable(player).map(playerx -> Pair.of(playerx, MobEffects.BAD_OMEN));
	}

	public void resetAfterBecomingOminous(TrialSpawner trialSpawner, ServerLevel serverLevel) {
		this.currentMobs.stream().map(serverLevel::getEntity).forEach(entity -> {
			if (entity != null) {
				serverLevel.levelEvent(3012, entity.blockPosition(), TrialSpawner.FlameParticle.NORMAL.encode());
				if (entity instanceof Mob mob) {
					mob.dropPreservedEquipment(serverLevel);
				}

				entity.remove(Entity.RemovalReason.DISCARDED);
			}
		});
		if (!trialSpawner.ominousConfig().spawnPotentialsDefinition().isEmpty()) {
			this.nextSpawnData = Optional.empty();
		}

		this.totalMobsSpawned = 0;
		this.currentMobs.clear();
		this.nextMobSpawnsAt = serverLevel.getGameTime() + trialSpawner.ominousConfig().ticksBetweenSpawn();
		trialSpawner.markUpdated();
		this.cooldownEndsAt = serverLevel.getGameTime() + trialSpawner.ominousConfig().ticksBetweenItemSpawners();
	}

	private static void transformBadOmenIntoTrialOmen(Player player) {
		MobEffectInstance mobEffectInstance = player.getEffect(MobEffects.BAD_OMEN);
		if (mobEffectInstance != null) {
			int i = mobEffectInstance.getAmplifier() + 1;
			int j = 18000 * i;
			player.removeEffect(MobEffects.BAD_OMEN);
			player.addEffect(new MobEffectInstance(MobEffects.TRIAL_OMEN, j, 0));
		}
	}

	public boolean isReadyToOpenShutter(ServerLevel serverLevel, float f, int i) {
		long l = this.cooldownEndsAt - i;
		return (float)serverLevel.getGameTime() >= (float)l + f;
	}

	public boolean isReadyToEjectItems(ServerLevel serverLevel, float f, int i) {
		long l = this.cooldownEndsAt - i;
		return (float)(serverLevel.getGameTime() - l) % f == 0.0F;
	}

	public boolean isCooldownFinished(ServerLevel serverLevel) {
		return serverLevel.getGameTime() >= this.cooldownEndsAt;
	}

	protected SpawnData getOrCreateNextSpawnData(TrialSpawner trialSpawner, RandomSource randomSource) {
		if (this.nextSpawnData.isPresent()) {
			return this.nextSpawnData.get();
		}

		WeightedList<SpawnData> weightedList = trialSpawner.activeConfig().spawnPotentialsDefinition();
		Optional<SpawnData> optional = weightedList.isEmpty() ? this.nextSpawnData : weightedList.getRandom(randomSource);
		this.nextSpawnData = Optional.of(optional.orElseGet(SpawnData::new));
		trialSpawner.markUpdated();
		return this.nextSpawnData.get();
	}

	public @Nullable Entity getOrCreateDisplayEntity(TrialSpawner trialSpawner, Level level, TrialSpawnerState trialSpawnerState) {
		if (!trialSpawnerState.hasSpinningMob()) {
			return null;
		}

		if (this.displayEntity == null) {
			CompoundTag compoundTag = this.getOrCreateNextSpawnData(trialSpawner, level.getRandom()).getEntityToSpawn();
			if (compoundTag.getString("id").isPresent()) {
				this.displayEntity = EntityType.loadEntityRecursive(compoundTag, level, EntitySpawnReason.TRIAL_SPAWNER, EntityProcessor.NOP);
			}
		}

		return this.displayEntity;
	}

	public CompoundTag getUpdateTag(TrialSpawnerState trialSpawnerState) {
		CompoundTag compoundTag = new CompoundTag();
		if (trialSpawnerState == TrialSpawnerState.ACTIVE) {
			compoundTag.putLong("next_mob_spawns_at", this.nextMobSpawnsAt);
		}

		this.nextSpawnData.ifPresent(spawnData -> compoundTag.store("spawn_data", SpawnData.CODEC, spawnData));
		return compoundTag;
	}

	public double getSpin() {
		return this.spin;
	}

	public double getOSpin() {
		return this.oSpin;
	}

	WeightedList<ItemStack> getDispensingItems(ServerLevel serverLevel, TrialSpawnerConfig trialSpawnerConfig, BlockPos blockPos) {
		if (this.dispensing != null) {
			return this.dispensing;
		}

		LootTable lootTable = serverLevel.getServer().reloadableRegistries().getLootTable(trialSpawnerConfig.itemsToDropWhenOminous());
		LootParams lootParams = new LootParams.Builder(serverLevel).create(LootContextParamSets.EMPTY);
		long l = lowResolutionPosition(serverLevel, blockPos);
		ObjectArrayList<ItemStack> objectArrayList = lootTable.getRandomItems(lootParams, l);
		if (objectArrayList.isEmpty()) {
			return WeightedList.of();
		}

		WeightedList.Builder<ItemStack> builder = WeightedList.builder();

		for (ItemStack itemStack : objectArrayList) {
			builder.add(itemStack.copyWithCount(1), itemStack.getCount());
		}

		this.dispensing = builder.build();
		return this.dispensing;
	}

	private static long lowResolutionPosition(ServerLevel serverLevel, BlockPos blockPos) {
		BlockPos blockPos2 = new BlockPos(Mth.floor(blockPos.getX() / 30.0F), Mth.floor(blockPos.getY() / 20.0F), Mth.floor(blockPos.getZ() / 30.0F));
		return serverLevel.getSeed() + blockPos2.asLong();
	}

	public record Packed(
		Set<UUID> detectedPlayers,
		Set<UUID> currentMobs,
		long cooldownEndsAt,
		long nextMobSpawnsAt,
		int totalMobsSpawned,
		Optional<SpawnData> nextSpawnData,
		Optional<ResourceKey<LootTable>> ejectingLootTable
	) {
		public static final MapCodec<TrialSpawnerStateData.Packed> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					UUIDUtil.CODEC_SET.lenientOptionalFieldOf("registered_players", Set.of()).forGetter(TrialSpawnerStateData.Packed::detectedPlayers),
					UUIDUtil.CODEC_SET.lenientOptionalFieldOf("current_mobs", Set.of()).forGetter(TrialSpawnerStateData.Packed::currentMobs),
					Codec.LONG.lenientOptionalFieldOf("cooldown_ends_at", 0L).forGetter(TrialSpawnerStateData.Packed::cooldownEndsAt),
					Codec.LONG.lenientOptionalFieldOf("next_mob_spawns_at", 0L).forGetter(TrialSpawnerStateData.Packed::nextMobSpawnsAt),
					Codec.intRange(0, Integer.MAX_VALUE).lenientOptionalFieldOf("total_mobs_spawned", 0).forGetter(TrialSpawnerStateData.Packed::totalMobsSpawned),
					SpawnData.CODEC.lenientOptionalFieldOf("spawn_data").forGetter(TrialSpawnerStateData.Packed::nextSpawnData),
					LootTable.KEY_CODEC.lenientOptionalFieldOf("ejecting_loot_table").forGetter(TrialSpawnerStateData.Packed::ejectingLootTable)
				)
				.apply(instance, TrialSpawnerStateData.Packed::new)
		);
	}
}
