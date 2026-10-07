package net.minecraft.world.entity.raid;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Raid {
	public static final SpawnPlacementType RAVAGER_SPAWN_PLACEMENT_TYPE = SpawnPlacements.getPlacementType(EntityType.RAVAGER);
	public static final MapCodec<Raid> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				Codec.BOOL.fieldOf("started").forGetter(raid -> raid.started),
				Codec.BOOL.fieldOf("active").forGetter(raid -> raid.active),
				Codec.LONG.fieldOf("ticks_active").forGetter(raid -> raid.ticksActive),
				Codec.INT.fieldOf("raid_omen_level").forGetter(raid -> raid.raidOmenLevel),
				Codec.INT.fieldOf("groups_spawned").forGetter(raid -> raid.groupsSpawned),
				Codec.INT.fieldOf("cooldown_ticks").forGetter(raid -> raid.raidCooldownTicks),
				Codec.INT.fieldOf("post_raid_ticks").forGetter(raid -> raid.postRaidTicks),
				Codec.FLOAT.fieldOf("total_health").forGetter(raid -> raid.totalHealth),
				Codec.INT.fieldOf("group_count").forGetter(raid -> raid.numGroups),
				Raid.RaidStatus.CODEC.fieldOf("status").forGetter(raid -> raid.status),
				BlockPos.CODEC.fieldOf("center").forGetter(raid -> raid.center),
				UUIDUtil.CODEC_SET.fieldOf("heroes_of_the_village").forGetter(raid -> raid.heroesOfTheVillage)
			)
			.apply(instance, Raid::new)
	);
	private static final int ALLOW_SPAWNING_WITHIN_VILLAGE_SECONDS_THRESHOLD = 7;
	private static final int SECTION_RADIUS_FOR_FINDING_NEW_VILLAGE_CENTER = 2;
	private static final int VILLAGE_SEARCH_RADIUS = 32;
	private static final int RAID_TIMEOUT_TICKS = 48000;
	private static final int NUM_SPAWN_ATTEMPTS = 5;
	private static final Component OMINOUS_BANNER_PATTERN_NAME = Component.translatable("block.minecraft.ominous_banner");
	private static final String RAIDERS_REMAINING = "event.minecraft.raid.raiders_remaining";
	public static final int VILLAGE_RADIUS_BUFFER = 16;
	private static final int POST_RAID_TICK_LIMIT = 40;
	private static final int DEFAULT_PRE_RAID_TICKS = 300;
	public static final int MAX_NO_ACTION_TIME = 2400;
	public static final int MAX_CELEBRATION_TICKS = 600;
	private static final int OUTSIDE_RAID_BOUNDS_TIMEOUT = 30;
	public static final int DEFAULT_MAX_RAID_OMEN_LEVEL = 5;
	private static final int LOW_MOB_THRESHOLD = 2;
	private static final Component RAID_NAME_COMPONENT = Component.translatable("event.minecraft.raid");
	private static final Component RAID_BAR_VICTORY_COMPONENT = Component.translatable("event.minecraft.raid.victory.full");
	private static final Component RAID_BAR_DEFEAT_COMPONENT = Component.translatable("event.minecraft.raid.defeat.full");
	private static final int HERO_OF_THE_VILLAGE_DURATION = 48000;
	private static final int VALID_RAID_RADIUS = 96;
	public static final int VALID_RAID_RADIUS_SQR = 9216;
	public static final int RAID_REMOVAL_THRESHOLD_SQR = 12544;
	private final Map<Integer, Raider> groupToLeaderMap = Maps.newHashMap();
	private final Map<Integer, Set<Raider>> groupRaiderMap = Maps.newHashMap();
	private final Set<UUID> heroesOfTheVillage = Sets.newHashSet();
	private long ticksActive;
	private BlockPos center;
	private boolean started;
	private float totalHealth;
	private int raidOmenLevel;
	private boolean active;
	private int groupsSpawned;
	private final ServerBossEvent raidEvent = new ServerBossEvent(RAID_NAME_COMPONENT, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
	private int postRaidTicks;
	private int raidCooldownTicks;
	private final RandomSource random = RandomSource.create();
	private final int numGroups;
	private Raid.RaidStatus status;
	private int celebrationTicks;
	private Optional<BlockPos> waveSpawnPos = Optional.empty();

	public Raid(BlockPos blockPos, Difficulty difficulty) {
		this.active = true;
		this.raidCooldownTicks = 300;
		this.raidEvent.setProgress(0.0F);
		this.center = blockPos;
		this.numGroups = this.getNumGroups(difficulty);
		this.status = Raid.RaidStatus.ONGOING;
	}

	private Raid(boolean bl, boolean bl2, long l, int i, int j, int k, int m, float f, int n, Raid.RaidStatus raidStatus, BlockPos blockPos, Set<UUID> set) {
		this.started = bl;
		this.active = bl2;
		this.ticksActive = l;
		this.raidOmenLevel = i;
		this.groupsSpawned = j;
		this.raidCooldownTicks = k;
		this.postRaidTicks = m;
		this.totalHealth = f;
		this.center = blockPos;
		this.numGroups = n;
		this.status = raidStatus;
		this.heroesOfTheVillage.addAll(set);
	}

	public boolean isOver() {
		return this.isVictory() || this.isLoss();
	}

	public boolean isBetweenWaves() {
		return this.hasFirstWaveSpawned() && this.getTotalRaidersAlive() == 0 && this.raidCooldownTicks > 0;
	}

	public boolean hasFirstWaveSpawned() {
		return this.groupsSpawned > 0;
	}

	public boolean isStopped() {
		return this.status == Raid.RaidStatus.STOPPED;
	}

	public boolean isVictory() {
		return this.status == Raid.RaidStatus.VICTORY;
	}

	public boolean isLoss() {
		return this.status == Raid.RaidStatus.LOSS;
	}

	public float getTotalHealth() {
		return this.totalHealth;
	}

	public Set<Raider> getAllRaiders() {
		Set<Raider> set = Sets.newHashSet();

		for (Set<Raider> set2 : this.groupRaiderMap.values()) {
			set.addAll(set2);
		}

		return set;
	}

	public boolean isStarted() {
		return this.started;
	}

	public int getGroupsSpawned() {
		return this.groupsSpawned;
	}

	private Predicate<ServerPlayer> validPlayer() {
		return serverPlayer -> {
			BlockPos blockPos = serverPlayer.blockPosition();
			return serverPlayer.isAlive() && serverPlayer.level().getRaidAt(blockPos) == this;
		};
	}

	private void updatePlayers(ServerLevel serverLevel) {
		Set<ServerPlayer> set = Sets.newHashSet(this.raidEvent.getPlayers());
		List<ServerPlayer> list = serverLevel.getPlayers(this.validPlayer());

		for (ServerPlayer serverPlayer : list) {
			if (!set.contains(serverPlayer)) {
				this.raidEvent.addPlayer(serverPlayer);
			}
		}

		for (ServerPlayer serverPlayer : set) {
			if (!list.contains(serverPlayer)) {
				this.raidEvent.removePlayer(serverPlayer);
			}
		}
	}

	public int getMaxRaidOmenLevel() {
		return 5;
	}

	public int getRaidOmenLevel() {
		return this.raidOmenLevel;
	}

	public void setRaidOmenLevel(int i) {
		this.raidOmenLevel = i;
	}

	public boolean absorbRaidOmen(ServerPlayer serverPlayer) {
		MobEffectInstance mobEffectInstance = serverPlayer.getEffect(MobEffects.RAID_OMEN);
		if (mobEffectInstance == null) {
			return false;
		}

		this.raidOmenLevel = this.raidOmenLevel + mobEffectInstance.getAmplifier() + 1;
		this.raidOmenLevel = Mth.clamp(this.raidOmenLevel, 0, this.getMaxRaidOmenLevel());
		if (!this.hasFirstWaveSpawned()) {
			serverPlayer.awardStat(Stats.RAID_TRIGGER);
			CriteriaTriggers.RAID_OMEN.trigger(serverPlayer);
		}

		return true;
	}

	public void stop() {
		this.active = false;
		this.raidEvent.removeAllPlayers();
		this.status = Raid.RaidStatus.STOPPED;
	}

	public void tick(ServerLevel serverLevel) {
		if (!this.isStopped()) {
			if (this.status == Raid.RaidStatus.ONGOING) {
				boolean bl = this.active;
				this.active = serverLevel.hasChunkAt(this.center);
				if (serverLevel.getDifficulty() == Difficulty.PEACEFUL) {
					this.stop();
					return;
				}

				if (bl != this.active) {
					this.raidEvent.setVisible(this.active);
				}

				if (!this.active) {
					return;
				}

				if (!serverLevel.isVillage(this.center)) {
					this.moveRaidCenterToNearbyVillageSection(serverLevel);
				}

				if (!serverLevel.isVillage(this.center)) {
					if (this.groupsSpawned > 0) {
						this.status = Raid.RaidStatus.LOSS;
					} else {
						this.stop();
					}
				}

				this.ticksActive++;
				if (this.ticksActive >= 48000L) {
					this.stop();
					return;
				}

				int i = this.getTotalRaidersAlive();
				if (i == 0 && this.hasMoreWaves()) {
					if (this.raidCooldownTicks <= 0) {
						if (this.raidCooldownTicks == 0 && this.groupsSpawned > 0) {
							this.raidCooldownTicks = 300;
							this.raidEvent.setName(RAID_NAME_COMPONENT);
							return;
						}
					} else {
						boolean bl2 = this.waveSpawnPos.isPresent();
						boolean bl3 = !bl2 && this.raidCooldownTicks % 5 == 0;
						if (bl2 && !serverLevel.isPositionEntityTicking(this.waveSpawnPos.get())) {
							bl3 = true;
						}

						if (bl3) {
							this.waveSpawnPos = this.getValidSpawnPos(serverLevel);
						}

						if (this.raidCooldownTicks == 300 || this.raidCooldownTicks % 20 == 0) {
							this.updatePlayers(serverLevel);
						}

						this.raidCooldownTicks--;
						this.raidEvent.setProgress(Mth.clamp((300 - this.raidCooldownTicks) / 300.0F, 0.0F, 1.0F));
					}
				}

				if (this.ticksActive % 20L == 0L) {
					this.updatePlayers(serverLevel);
					this.updateRaiders(serverLevel);
					if (i > 0) {
						if (i <= 2) {
							this.raidEvent.setName(RAID_NAME_COMPONENT.copy().append(" - ").append(Component.translatable("event.minecraft.raid.raiders_remaining", i)));
						} else {
							this.raidEvent.setName(RAID_NAME_COMPONENT);
						}
					} else {
						this.raidEvent.setName(RAID_NAME_COMPONENT);
					}
				}

				if (SharedConstants.DEBUG_RAIDS) {
					this.raidEvent
						.setName(
							RAID_NAME_COMPONENT.copy()
								.append(" wave: ")
								.append(this.groupsSpawned + "")
								.append(CommonComponents.SPACE)
								.append("Raiders alive: ")
								.append(this.getTotalRaidersAlive() + "")
								.append(CommonComponents.SPACE)
								.append(this.getHealthOfLivingRaiders() + "")
								.append(" / ")
								.append(this.totalHealth + "")
								.append(" Is bonus? ")
								.append((this.hasBonusWave() && this.hasSpawnedBonusWave()) + "")
								.append(" Status: ")
								.append(this.status.getSerializedName())
						);
				}

				boolean bl2 = false;
				int j = 0;

				while (this.shouldSpawnGroup()) {
					BlockPos blockPos = this.waveSpawnPos.orElseGet(() -> this.findRandomSpawnPos(serverLevel, 20));
					if (blockPos != null) {
						this.started = true;
						this.spawnGroup(serverLevel, blockPos);
						if (!bl2) {
							this.playSound(serverLevel, blockPos);
							bl2 = true;
						}
					} else {
						j++;
					}

					if (j > 5) {
						this.stop();
						break;
					}
				}

				if (this.isStarted() && !this.hasMoreWaves() && i == 0) {
					if (this.postRaidTicks < 40) {
						this.postRaidTicks++;
					} else {
						this.status = Raid.RaidStatus.VICTORY;

						for (UUID uUID : this.heroesOfTheVillage) {
							Entity entity = serverLevel.getEntity(uUID);
							if (entity instanceof LivingEntity livingEntity && !entity.isSpectator()) {
								livingEntity.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 48000, this.raidOmenLevel - 1, false, false, true));
								if (livingEntity instanceof ServerPlayer serverPlayer) {
									serverPlayer.awardStat(Stats.RAID_WIN);
									CriteriaTriggers.RAID_WIN.trigger(serverPlayer);
								}
							}
						}
					}
				}

				this.setDirty(serverLevel);
			} else if (this.isOver()) {
				this.celebrationTicks++;
				if (this.celebrationTicks >= 600) {
					this.stop();
					return;
				}

				if (this.celebrationTicks % 20 == 0) {
					this.updatePlayers(serverLevel);
					this.raidEvent.setVisible(true);
					if (this.isVictory()) {
						this.raidEvent.setProgress(0.0F);
						this.raidEvent.setName(RAID_BAR_VICTORY_COMPONENT);
					} else {
						this.raidEvent.setName(RAID_BAR_DEFEAT_COMPONENT);
					}
				}
			}
		}
	}

	private void moveRaidCenterToNearbyVillageSection(ServerLevel serverLevel) {
		Stream<SectionPos> stream = SectionPos.cube(SectionPos.of(this.center), 2);
		stream.filter(serverLevel::isVillage)
			.map(SectionPos::center)
			.min(Comparator.comparingDouble(blockPos -> blockPos.distSqr(this.center)))
			.ifPresent(this::setCenter);
	}

	private Optional<BlockPos> getValidSpawnPos(ServerLevel serverLevel) {
		BlockPos blockPos = this.findRandomSpawnPos(serverLevel, 8);
		return blockPos != null ? Optional.of(blockPos) : Optional.empty();
	}

	private boolean hasMoreWaves() {
		return this.hasBonusWave() ? !this.hasSpawnedBonusWave() : !this.isFinalWave();
	}

	private boolean isFinalWave() {
		return this.getGroupsSpawned() == this.numGroups;
	}

	private boolean hasBonusWave() {
		return this.raidOmenLevel > 1;
	}

	private boolean hasSpawnedBonusWave() {
		return this.getGroupsSpawned() > this.numGroups;
	}

	private boolean shouldSpawnBonusGroup() {
		return this.isFinalWave() && this.getTotalRaidersAlive() == 0 && this.hasBonusWave();
	}

	private void updateRaiders(ServerLevel serverLevel) {
		Iterator<Set<Raider>> iterator = this.groupRaiderMap.values().iterator();
		Set<Raider> set = Sets.newHashSet();

		while (iterator.hasNext()) {
			Set<Raider> set2 = iterator.next();

			for (Raider raider : set2) {
				BlockPos blockPos = raider.blockPosition();
				if (raider.isRemoved() || raider.level().dimension() != serverLevel.dimension() || this.center.distSqr(blockPos) >= 12544.0) {
					set.add(raider);
				} else if (raider.tickCount > 600) {
					if (serverLevel.getEntity(raider.getUUID()) == null) {
						set.add(raider);
					}

					if (!serverLevel.isVillage(blockPos) && raider.getNoActionTime() > 2400) {
						raider.setTicksOutsideRaid(raider.getTicksOutsideRaid() + 1);
					}

					if (raider.getTicksOutsideRaid() >= 30) {
						set.add(raider);
					}
				}
			}
		}

		for (Raider raider2 : set) {
			this.removeFromRaid(serverLevel, raider2, true);
			if (raider2.isPatrolLeader()) {
				this.removeLeader(raider2.getWave());
			}
		}
	}

	private void playSound(ServerLevel serverLevel, BlockPos blockPos) {
		float f = 13.0F;
		int i = 64;
		Collection<ServerPlayer> collection = this.raidEvent.getPlayers();
		long l = this.random.nextLong();

		for (ServerPlayer serverPlayer : serverLevel.players()) {
			Vec3 vec3 = serverPlayer.position();
			Vec3 vec32 = Vec3.atCenterOf(blockPos);
			double d = Math.sqrt((vec32.x - vec3.x) * (vec32.x - vec3.x) + (vec32.z - vec3.z) * (vec32.z - vec3.z));
			double e = vec3.x + 13.0 / d * (vec32.x - vec3.x);
			double g = vec3.z + 13.0 / d * (vec32.z - vec3.z);
			if (d <= 64.0 || collection.contains(serverPlayer)) {
				serverPlayer.connection.send(new ClientboundSoundPacket(SoundEvents.RAID_HORN, SoundSource.NEUTRAL, e, serverPlayer.getY(), g, 64.0F, 1.0F, l));
			}
		}
	}

	private void spawnGroup(ServerLevel serverLevel, BlockPos blockPos) {
		boolean bl = false;
		int i = this.groupsSpawned + 1;
		this.totalHealth = 0.0F;
		DifficultyInstance difficultyInstance = serverLevel.getCurrentDifficultyAt(blockPos);
		boolean bl2 = this.shouldSpawnBonusGroup();

		for (Raid.RaiderType raiderType : Raid.RaiderType.VALUES) {
			int j = this.getDefaultNumSpawns(raiderType, i, bl2) + this.getPotentialBonusSpawns(raiderType, this.random, i, difficultyInstance, bl2);
			int k = 0;

			for (int l = 0; l < j; l++) {
				Raider raider = raiderType.entityType.create(serverLevel, EntitySpawnReason.EVENT);
				if (raider == null) {
					break;
				}

				if (!bl && raider.canBeLeader()) {
					raider.setPatrolLeader(true);
					this.setLeader(i, raider);
					bl = true;
				}

				this.joinRaid(serverLevel, i, raider, blockPos, false);
				if (raiderType.entityType == EntityType.RAVAGER) {
					Raider raider2 = null;
					if (i == this.getNumGroups(Difficulty.NORMAL)) {
						raider2 = EntityType.PILLAGER.create(serverLevel, EntitySpawnReason.EVENT);
					} else if (i >= this.getNumGroups(Difficulty.HARD)) {
						if (k == 0) {
							raider2 = EntityType.EVOKER.create(serverLevel, EntitySpawnReason.EVENT);
						} else {
							raider2 = EntityType.VINDICATOR.create(serverLevel, EntitySpawnReason.EVENT);
						}
					}

					k++;
					if (raider2 != null) {
						this.joinRaid(serverLevel, i, raider2, blockPos, false);
						raider2.snapTo(blockPos, 0.0F, 0.0F);
						raider2.startRiding(raider, false, false);
					}
				}
			}
		}

		this.waveSpawnPos = Optional.empty();
		this.groupsSpawned++;
		this.updateBossbar();
		this.setDirty(serverLevel);
	}

	public void joinRaid(ServerLevel serverLevel, int i, Raider raider, @Nullable BlockPos blockPos, boolean bl) {
		boolean bl2 = this.addWaveMob(serverLevel, i, raider);
		if (bl2) {
			raider.setCurrentRaid(this);
			raider.setWave(i);
			raider.setCanJoinRaid(true);
			raider.setTicksOutsideRaid(0);
			if (!bl && blockPos != null) {
				raider.setPos(blockPos.getX() + 0.5, blockPos.getY() + 1.0, blockPos.getZ() + 0.5);
				raider.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(blockPos), EntitySpawnReason.EVENT, null);
				raider.applyRaidBuffs(serverLevel, i, false);
				raider.setOnGround(true);
				serverLevel.addFreshEntityWithPassengers(raider);
			}
		}
	}

	public void updateBossbar() {
		this.raidEvent.setProgress(Mth.clamp(this.getHealthOfLivingRaiders() / this.totalHealth, 0.0F, 1.0F));
	}

	public float getHealthOfLivingRaiders() {
		float f = 0.0F;

		for (Set<Raider> set : this.groupRaiderMap.values()) {
			for (Raider raider : set) {
				f += raider.getHealth();
			}
		}

		return f;
	}

	private boolean shouldSpawnGroup() {
		return this.raidCooldownTicks == 0 && (this.groupsSpawned < this.numGroups || this.shouldSpawnBonusGroup()) && this.getTotalRaidersAlive() == 0;
	}

	public int getTotalRaidersAlive() {
		return this.groupRaiderMap.values().stream().mapToInt(Set::size).sum();
	}

	public void removeFromRaid(ServerLevel serverLevel, Raider raider, boolean bl) {
		Set<Raider> set = this.groupRaiderMap.get(raider.getWave());
		if (set != null) {
			boolean bl2 = set.remove(raider);
			if (bl2) {
				if (bl) {
					this.totalHealth = this.totalHealth - raider.getHealth();
				}

				raider.setCurrentRaid(null);
				this.updateBossbar();
				this.setDirty(serverLevel);
			}
		}
	}

	private void setDirty(ServerLevel serverLevel) {
		serverLevel.getRaids().setDirty();
	}

	public static ItemStack getOminousBannerInstance(HolderGetter<BannerPattern> holderGetter) {
		ItemStack itemStack = new ItemStack(Items.WHITE_BANNER);
		BannerPatternLayers bannerPatternLayers = new BannerPatternLayers.Builder()
			.addIfRegistered(holderGetter, BannerPatterns.RHOMBUS_MIDDLE, DyeColor.CYAN)
			.addIfRegistered(holderGetter, BannerPatterns.STRIPE_BOTTOM, DyeColor.LIGHT_GRAY)
			.addIfRegistered(holderGetter, BannerPatterns.STRIPE_CENTER, DyeColor.GRAY)
			.addIfRegistered(holderGetter, BannerPatterns.BORDER, DyeColor.LIGHT_GRAY)
			.addIfRegistered(holderGetter, BannerPatterns.STRIPE_MIDDLE, DyeColor.BLACK)
			.addIfRegistered(holderGetter, BannerPatterns.HALF_HORIZONTAL, DyeColor.LIGHT_GRAY)
			.addIfRegistered(holderGetter, BannerPatterns.CIRCLE_MIDDLE, DyeColor.LIGHT_GRAY)
			.addIfRegistered(holderGetter, BannerPatterns.BORDER, DyeColor.BLACK)
			.build();
		itemStack.set(DataComponents.BANNER_PATTERNS, bannerPatternLayers);
		itemStack.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.BANNER_PATTERNS, true));
		itemStack.set(DataComponents.ITEM_NAME, OMINOUS_BANNER_PATTERN_NAME);
		itemStack.set(DataComponents.RARITY, Rarity.UNCOMMON);
		return itemStack;
	}

	public @Nullable Raider getLeader(int i) {
		return this.groupToLeaderMap.get(i);
	}

	private @Nullable BlockPos findRandomSpawnPos(ServerLevel serverLevel, int i) {
		int j = this.raidCooldownTicks / 20;
		float f = 0.22F * j - 0.24F;
		BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
		float g = serverLevel.random.nextFloat() * (float) (Math.PI * 2);

		for (int k = 0; k < i; k++) {
			float h = g + (float) Math.PI * k / 8.0F;
			int l = this.center.getX() + Mth.floor(Mth.cos(h) * 32.0F * f) + serverLevel.random.nextInt(3) * Mth.floor(f);
			int m = this.center.getZ() + Mth.floor(Mth.sin(h) * 32.0F * f) + serverLevel.random.nextInt(3) * Mth.floor(f);
			int n = serverLevel.getHeight(Heightmap.Types.WORLD_SURFACE, l, m);
			if (Mth.abs(n - this.center.getY()) <= 96) {
				mutableBlockPos.set(l, n, m);
				if (!serverLevel.isVillage(mutableBlockPos) || j <= 7) {
					int o = 10;
					if (serverLevel.hasChunksAt(mutableBlockPos.getX() - 10, mutableBlockPos.getZ() - 10, mutableBlockPos.getX() + 10, mutableBlockPos.getZ() + 10)
						&& serverLevel.isPositionEntityTicking(mutableBlockPos)
						&& (
							RAVAGER_SPAWN_PLACEMENT_TYPE.isSpawnPositionOk(serverLevel, mutableBlockPos, EntityType.RAVAGER)
								|| serverLevel.getBlockState(mutableBlockPos.below()).is(Blocks.SNOW) && serverLevel.getBlockState(mutableBlockPos).isAir()
						)) {
						return mutableBlockPos;
					}
				}
			}
		}

		return null;
	}

	private boolean addWaveMob(ServerLevel serverLevel, int i, Raider raider) {
		return this.addWaveMob(serverLevel, i, raider, true);
	}

	public boolean addWaveMob(ServerLevel serverLevel, int i, Raider raider, boolean bl) {
		this.groupRaiderMap.computeIfAbsent(i, integer -> Sets.newHashSet());
		Set<Raider> set = this.groupRaiderMap.get(i);
		Raider raider2 = null;

		for (Raider raider3 : set) {
			if (raider3.getUUID().equals(raider.getUUID())) {
				raider2 = raider3;
				break;
			}
		}

		if (raider2 != null) {
			set.remove(raider2);
			set.add(raider);
		}

		set.add(raider);
		if (bl) {
			this.totalHealth = this.totalHealth + raider.getHealth();
		}

		this.updateBossbar();
		this.setDirty(serverLevel);
		return true;
	}

	public void setLeader(int i, Raider raider) {
		this.groupToLeaderMap.put(i, raider);
		raider.setItemSlot(EquipmentSlot.HEAD, getOminousBannerInstance(raider.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
		raider.setDropChance(EquipmentSlot.HEAD, 2.0F);
	}

	public void removeLeader(int i) {
		this.groupToLeaderMap.remove(i);
	}

	public BlockPos getCenter() {
		return this.center;
	}

	private void setCenter(BlockPos blockPos) {
		this.center = blockPos;
	}

	private int getDefaultNumSpawns(Raid.RaiderType raiderType, int i, boolean bl) {
		return bl ? raiderType.spawnsPerWaveBeforeBonus[this.numGroups] : raiderType.spawnsPerWaveBeforeBonus[i];
	}

	private int getPotentialBonusSpawns(Raid.RaiderType raiderType, RandomSource randomSource, int i, DifficultyInstance difficultyInstance, boolean bl) {
		Difficulty difficulty = difficultyInstance.getDifficulty();
		boolean bl2 = difficulty == Difficulty.EASY;
		boolean bl3 = difficulty == Difficulty.NORMAL;
		int j;
		switch (raiderType) {
			case VINDICATOR:
			case PILLAGER:
				if (bl2) {
					j = randomSource.nextInt(2);
				} else if (bl3) {
					j = 1;
				} else {
					j = 2;
				}
				break;
			case EVOKER:
			default:
				return 0;
			case WITCH:
				if (bl2 || i <= 2 || i == 4) {
					return 0;
				}

				j = 1;
				break;
			case RAVAGER:
				j = !bl2 && bl ? 1 : 0;
		}

		return j > 0 ? randomSource.nextInt(j + 1) : 0;
	}

	public boolean isActive() {
		return this.active;
	}

	public int getNumGroups(Difficulty difficulty) {
		return switch (difficulty) {
			case PEACEFUL -> 0;
			case EASY -> 3;
			case NORMAL -> 5;
			case HARD -> 7;
		};
	}

	public float getEnchantOdds() {
		int i = this.getRaidOmenLevel();
		if (i == 2) {
			return 0.1F;
		} else if (i == 3) {
			return 0.25F;
		} else if (i == 4) {
			return 0.5F;
		} else {
			return i == 5 ? 0.75F : 0.0F;
		}
	}

	public void addHeroOfTheVillage(Entity entity) {
		this.heroesOfTheVillage.add(entity.getUUID());
	}

	enum RaidStatus implements StringRepresentable {
		ONGOING("ongoing"),
		VICTORY("victory"),
		LOSS("loss"),
		STOPPED("stopped");

		public static final Codec<Raid.RaidStatus> CODEC = StringRepresentable.fromEnum(Raid.RaidStatus::values);
		private final String name;

		RaidStatus(final String string2) {
			this.name = string2;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}

	enum RaiderType {
		VINDICATOR(EntityType.VINDICATOR, new int[]{0, 0, 2, 0, 1, 4, 2, 5}),
		EVOKER(EntityType.EVOKER, new int[]{0, 0, 0, 0, 0, 1, 1, 2}),
		PILLAGER(EntityType.PILLAGER, new int[]{0, 4, 3, 3, 4, 4, 4, 2}),
		WITCH(EntityType.WITCH, new int[]{0, 0, 0, 0, 3, 0, 0, 1}),
		RAVAGER(EntityType.RAVAGER, new int[]{0, 0, 0, 1, 0, 1, 0, 2});

		static final Raid.RaiderType[] VALUES = values();
		final EntityType<? extends Raider> entityType;
		final int[] spawnsPerWaveBeforeBonus;

		RaiderType(final EntityType<? extends Raider> entityType, final int[] is) {
			this.entityType = entityType;
			this.spawnsPerWaveBeforeBonus = is;
		}
	}
}
