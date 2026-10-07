package net.minecraft.world.entity.npc.villager;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.SpawnUtil;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ReputationEventHandler;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.sensing.GolemSensor;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class Villager extends AbstractVillager implements ReputationEventHandler, VillagerDataHolder {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final EntityDataAccessor<VillagerData> DATA_VILLAGER_DATA = SynchedEntityData.defineId(Villager.class, EntityDataSerializers.VILLAGER_DATA);
	public static final int BREEDING_FOOD_THRESHOLD = 12;
	public static final Map<Item, Integer> FOOD_POINTS = ImmutableMap.of(Items.BREAD, 4, Items.POTATO, 1, Items.CARROT, 1, Items.BEETROOT, 1);
	private static final int TRADES_PER_LEVEL = 2;
	private static final int MAX_GOSSIP_TOPICS = 10;
	private static final int GOSSIP_COOLDOWN = 1200;
	private static final int GOSSIP_DECAY_INTERVAL = 24000;
	private static final int HOW_FAR_AWAY_TO_TALK_TO_OTHER_VILLAGERS_ABOUT_GOLEMS = 10;
	private static final int HOW_MANY_VILLAGERS_NEED_TO_AGREE_TO_SPAWN_A_GOLEM = 5;
	private static final long TIME_SINCE_SLEEPING_FOR_GOLEM_SPAWNING = 24000L;
	@VisibleForTesting
	public static final float SPEED_MODIFIER = 0.5F;
	private static final int DEFAULT_XP = 0;
	private static final byte DEFAULT_FOOD_LEVEL = 0;
	private static final int DEFAULT_LAST_RESTOCK = 0;
	private static final int DEFAULT_LAST_GOSSIP_DECAY = 0;
	private static final int DEFAULT_RESTOCKS_TODAY = 0;
	private static final boolean DEFAULT_ASSIGN_PROFESSION_WHEN_SPAWNED = false;
	private int updateMerchantTimer;
	private boolean increaseProfessionLevelOnUpdate;
	private @Nullable Player lastTradedPlayer;
	private boolean chasing;
	private int foodLevel = 0;
	private final GossipContainer gossips = new GossipContainer();
	private long lastGossipTime;
	private long lastGossipDecayTime = 0L;
	private int villagerXp = 0;
	private long lastRestockGameTime = 0L;
	private int numberOfRestocksToday = 0;
	private long lastRestockCheckDay;
	private boolean assignProfessionWhenSpawned = false;
	private static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(
		MemoryModuleType.HOME,
		MemoryModuleType.JOB_SITE,
		MemoryModuleType.POTENTIAL_JOB_SITE,
		MemoryModuleType.MEETING_POINT,
		MemoryModuleType.NEAREST_LIVING_ENTITIES,
		MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
		MemoryModuleType.VISIBLE_VILLAGER_BABIES,
		MemoryModuleType.NEAREST_PLAYERS,
		MemoryModuleType.NEAREST_VISIBLE_PLAYER,
		MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER,
		MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM,
		MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS,
		MemoryModuleType.WALK_TARGET,
		MemoryModuleType.LOOK_TARGET,
		MemoryModuleType.INTERACTION_TARGET,
		MemoryModuleType.BREED_TARGET,
		MemoryModuleType.PATH,
		MemoryModuleType.DOORS_TO_CLOSE,
		MemoryModuleType.NEAREST_BED,
		MemoryModuleType.HURT_BY,
		MemoryModuleType.HURT_BY_ENTITY,
		MemoryModuleType.NEAREST_HOSTILE,
		MemoryModuleType.SECONDARY_JOB_SITE,
		MemoryModuleType.HIDING_PLACE,
		MemoryModuleType.HEARD_BELL_TIME,
		MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
		MemoryModuleType.LAST_SLEPT,
		MemoryModuleType.LAST_WOKEN,
		MemoryModuleType.LAST_WORKED_AT_POI,
		MemoryModuleType.GOLEM_DETECTED_RECENTLY
	);
	private static final ImmutableList<SensorType<? extends Sensor<? super Villager>>> SENSOR_TYPES = ImmutableList.of(
		SensorType.NEAREST_LIVING_ENTITIES,
		SensorType.NEAREST_PLAYERS,
		SensorType.NEAREST_ITEMS,
		SensorType.NEAREST_BED,
		SensorType.HURT_BY,
		SensorType.VILLAGER_HOSTILES,
		SensorType.VILLAGER_BABIES,
		SensorType.SECONDARY_POIS,
		SensorType.GOLEM_DETECTED
	);
	public static final Map<MemoryModuleType<GlobalPos>, BiPredicate<Villager, Holder<PoiType>>> POI_MEMORIES = ImmutableMap.of(
		MemoryModuleType.HOME,
		(villager, holder) -> holder.is(PoiTypes.HOME),
		MemoryModuleType.JOB_SITE,
		(villager, holder) -> villager.getVillagerData().profession().value().heldJobSite().test(holder),
		MemoryModuleType.POTENTIAL_JOB_SITE,
		(villager, holder) -> VillagerProfession.ALL_ACQUIRABLE_JOBS.test(holder),
		MemoryModuleType.MEETING_POINT,
		(villager, holder) -> holder.is(PoiTypes.MEETING)
	);

	public Villager(EntityType<? extends Villager> entityType, Level level) {
		this(entityType, level, VillagerType.PLAINS);
	}

	public Villager(EntityType<? extends Villager> entityType, Level level, ResourceKey<VillagerType> resourceKey) {
		this(entityType, level, level.registryAccess().getOrThrow(resourceKey));
	}

	public Villager(EntityType<? extends Villager> entityType, Level level, Holder<VillagerType> holder) {
		super(entityType, level);
		this.getNavigation().setCanOpenDoors(true);
		this.getNavigation().setCanFloat(true);
		this.getNavigation().setRequiredPathLength(48.0F);
		this.setCanPickUpLoot(true);
		this.setVillagerData(this.getVillagerData().withType(holder).withProfession(level.registryAccess(), VillagerProfession.NONE));
	}

	@Override
	public Brain<Villager> getBrain() {
		return (Brain<Villager>)super.getBrain();
	}

	@Override
	protected Brain.Provider<Villager> brainProvider() {
		return Brain.provider(MEMORY_TYPES, SENSOR_TYPES);
	}

	@Override
	protected Brain<?> makeBrain(Dynamic<?> dynamic) {
		Brain<Villager> brain = this.brainProvider().makeBrain(dynamic);
		this.registerBrainGoals(brain);
		return brain;
	}

	public void refreshBrain(ServerLevel serverLevel) {
		Brain<Villager> brain = this.getBrain();
		brain.stopAll(serverLevel, this);
		this.brain = brain.copyWithoutBehaviors();
		this.registerBrainGoals(this.getBrain());
	}

	private void registerBrainGoals(Brain<Villager> brain) {
		Holder<VillagerProfession> holder = this.getVillagerData().profession();
		if (this.isBaby()) {
			brain.setSchedule(EnvironmentAttributes.BABY_VILLAGER_ACTIVITY);
			brain.addActivity(Activity.PLAY, VillagerGoalPackages.getPlayPackage(0.5F));
		} else {
			brain.setSchedule(EnvironmentAttributes.VILLAGER_ACTIVITY);
			brain.addActivityWithConditions(
				Activity.WORK, VillagerGoalPackages.getWorkPackage(holder, 0.5F), ImmutableSet.of(Pair.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT))
			);
		}

		brain.addActivity(Activity.CORE, VillagerGoalPackages.getCorePackage(holder, 0.5F));
		brain.addActivityWithConditions(
			Activity.MEET, VillagerGoalPackages.getMeetPackage(holder, 0.5F), ImmutableSet.of(Pair.of(MemoryModuleType.MEETING_POINT, MemoryStatus.VALUE_PRESENT))
		);
		brain.addActivity(Activity.REST, VillagerGoalPackages.getRestPackage(holder, 0.5F));
		brain.addActivity(Activity.IDLE, VillagerGoalPackages.getIdlePackage(holder, 0.5F));
		brain.addActivity(Activity.PANIC, VillagerGoalPackages.getPanicPackage(holder, 0.5F));
		brain.addActivity(Activity.PRE_RAID, VillagerGoalPackages.getPreRaidPackage(holder, 0.5F));
		brain.addActivity(Activity.RAID, VillagerGoalPackages.getRaidPackage(holder, 0.5F));
		brain.addActivity(Activity.HIDE, VillagerGoalPackages.getHidePackage(holder, 0.5F));
		brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
		brain.setDefaultActivity(Activity.IDLE);
		brain.setActiveActivityIfPossible(Activity.IDLE);
		brain.updateActivityFromSchedule(this.level().environmentAttributes(), this.level().getGameTime(), this.position());
	}

	@Override
	protected void ageBoundaryReached() {
		super.ageBoundaryReached();
		if (this.level() instanceof ServerLevel) {
			this.refreshBrain((ServerLevel)this.level());
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.5);
	}

	public boolean assignProfessionWhenSpawned() {
		return this.assignProfessionWhenSpawned;
	}

	@Override
	protected void customServerAiStep(ServerLevel serverLevel) {
		ProfilerFiller profilerFiller = Profiler.get();
		profilerFiller.push("villagerBrain");
		this.getBrain().tick(serverLevel, this);
		profilerFiller.pop();
		if (this.assignProfessionWhenSpawned) {
			this.assignProfessionWhenSpawned = false;
		}

		if (!this.isTrading() && this.updateMerchantTimer > 0) {
			this.updateMerchantTimer--;
			if (this.updateMerchantTimer <= 0) {
				if (this.increaseProfessionLevelOnUpdate) {
					this.increaseMerchantCareer(serverLevel);
					this.increaseProfessionLevelOnUpdate = false;
				}

				this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
			}
		}

		if (this.lastTradedPlayer != null) {
			serverLevel.onReputationEvent(ReputationEventType.TRADE, this.lastTradedPlayer, this);
			serverLevel.broadcastEntityEvent(this, (byte)14);
			this.lastTradedPlayer = null;
		}

		if (!this.isNoAi() && this.random.nextInt(100) == 0) {
			Raid raid = serverLevel.getRaidAt(this.blockPosition());
			if (raid != null && raid.isActive() && !raid.isOver()) {
				serverLevel.broadcastEntityEvent(this, (byte)42);
			}
		}

		if (this.getVillagerData().profession().is(VillagerProfession.NONE) && this.isTrading()) {
			this.stopTrading();
		}

		super.customServerAiStep(serverLevel);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getUnhappyCounter() > 0) {
			this.setUnhappyCounter(this.getUnhappyCounter() - 1);
		}

		this.maybeDecayGossip();
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand interactionHand) {
		ItemStack itemStack = player.getItemInHand(interactionHand);
		if (itemStack.is(Items.VILLAGER_SPAWN_EGG) || !this.isAlive() || this.isTrading() || this.isSleeping()) {
			return super.mobInteract(player, interactionHand);
		}

		if (this.isBaby()) {
			this.setUnhappy();
			return InteractionResult.SUCCESS;
		}

		if (!this.level().isClientSide()) {
			boolean bl = this.getOffers().isEmpty();
			if (interactionHand == InteractionHand.MAIN_HAND) {
				if (bl) {
					this.setUnhappy();
				}

				player.awardStat(Stats.TALKED_TO_VILLAGER);
			}

			if (bl) {
				return InteractionResult.CONSUME;
			}

			this.startTrading(player);
		}

		return InteractionResult.SUCCESS;
	}

	private void setUnhappy() {
		this.setUnhappyCounter(40);
		if (!this.level().isClientSide()) {
			this.makeSound(SoundEvents.VILLAGER_NO);
		}
	}

	private void startTrading(Player player) {
		this.updateSpecialPrices(player);
		this.setTradingPlayer(player);
		this.openTradingScreen(player, this.getDisplayName(), this.getVillagerData().level());
	}

	@Override
	public void setTradingPlayer(@Nullable Player player) {
		boolean bl = this.getTradingPlayer() != null && player == null;
		super.setTradingPlayer(player);
		if (bl) {
			this.stopTrading();
		}
	}

	@Override
	protected void stopTrading() {
		super.stopTrading();
		this.resetSpecialPrices();
	}

	private void resetSpecialPrices() {
		if (!this.level().isClientSide()) {
			for (MerchantOffer merchantOffer : this.getOffers()) {
				merchantOffer.resetSpecialPriceDiff();
			}
		}
	}

	@Override
	public boolean canRestock() {
		return true;
	}

	public void restock() {
		this.updateDemand();

		for (MerchantOffer merchantOffer : this.getOffers()) {
			merchantOffer.resetUses();
		}

		this.resendOffersToTradingPlayer();
		this.lastRestockGameTime = this.level().getGameTime();
		this.numberOfRestocksToday++;
	}

	private void resendOffersToTradingPlayer() {
		MerchantOffers merchantOffers = this.getOffers();
		Player player = this.getTradingPlayer();
		if (player != null && !merchantOffers.isEmpty()) {
			player.sendMerchantOffers(
				player.containerMenu.containerId, merchantOffers, this.getVillagerData().level(), this.getVillagerXp(), this.showProgressBar(), this.canRestock()
			);
		}
	}

	private boolean needsToRestock() {
		for (MerchantOffer merchantOffer : this.getOffers()) {
			if (merchantOffer.needsRestock()) {
				return true;
			}
		}

		return false;
	}

	private boolean allowedToRestock() {
		return this.numberOfRestocksToday == 0 || this.numberOfRestocksToday < 2 && this.level().getGameTime() > this.lastRestockGameTime + 2400L;
	}

	public boolean shouldRestock(ServerLevel serverLevel) {
		long l = this.lastRestockGameTime + 12000L;
		long m = this.level().getGameTime();
		boolean bl = m > l;
		long n = serverLevel.getDayCount();
		bl |= this.lastRestockCheckDay > 0L && n > this.lastRestockCheckDay;
		this.lastRestockCheckDay = n;
		if (bl) {
			this.lastRestockGameTime = m;
			this.resetNumberOfRestocks();
		}

		return this.allowedToRestock() && this.needsToRestock();
	}

	private void catchUpDemand() {
		int i = 2 - this.numberOfRestocksToday;
		if (i > 0) {
			for (MerchantOffer merchantOffer : this.getOffers()) {
				merchantOffer.resetUses();
			}
		}

		for (int j = 0; j < i; j++) {
			this.updateDemand();
		}

		this.resendOffersToTradingPlayer();
	}

	private void updateDemand() {
		for (MerchantOffer merchantOffer : this.getOffers()) {
			merchantOffer.updateDemand();
		}
	}

	private void updateSpecialPrices(Player player) {
		int i = this.getPlayerReputation(player);
		if (i != 0) {
			for (MerchantOffer merchantOffer : this.getOffers()) {
				merchantOffer.addToSpecialPriceDiff(-Mth.floor(i * merchantOffer.getPriceMultiplier()));
			}
		}

		if (player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) {
			MobEffectInstance mobEffectInstance = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
			int j = mobEffectInstance.getAmplifier();

			for (MerchantOffer merchantOffer2 : this.getOffers()) {
				double d = 0.3 + 0.0625 * j;
				int k = (int)Math.floor(d * merchantOffer2.getBaseCostA().getCount());
				merchantOffer2.addToSpecialPriceDiff(-Math.max(k, 1));
			}
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VILLAGER_DATA, createDefaultVillagerData());
	}

	public static VillagerData createDefaultVillagerData() {
		return new VillagerData(
			BuiltInRegistries.VILLAGER_TYPE.getOrThrow(VillagerType.PLAINS), BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE), 1
		);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.store("VillagerData", VillagerData.CODEC, this.getVillagerData());
		valueOutput.putByte("FoodLevel", (byte)this.foodLevel);
		valueOutput.store("Gossips", GossipContainer.CODEC, this.gossips);
		valueOutput.putInt("Xp", this.villagerXp);
		valueOutput.putLong("LastRestock", this.lastRestockGameTime);
		valueOutput.putLong("LastGossipDecay", this.lastGossipDecayTime);
		valueOutput.putInt("RestocksToday", this.numberOfRestocksToday);
		if (this.assignProfessionWhenSpawned) {
			valueOutput.putBoolean("AssignProfessionWhenSpawned", true);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.entityData.set(DATA_VILLAGER_DATA, valueInput.read("VillagerData", VillagerData.CODEC).orElseGet(Villager::createDefaultVillagerData));
		this.foodLevel = valueInput.getByteOr("FoodLevel", (byte)0);
		this.gossips.clear();
		valueInput.read("Gossips", GossipContainer.CODEC).ifPresent(this.gossips::putAll);
		this.villagerXp = valueInput.getIntOr("Xp", 0);
		this.lastRestockGameTime = valueInput.getLongOr("LastRestock", 0L);
		this.lastGossipDecayTime = valueInput.getLongOr("LastGossipDecay", 0L);
		if (this.level() instanceof ServerLevel) {
			this.refreshBrain((ServerLevel)this.level());
		}

		this.numberOfRestocksToday = valueInput.getIntOr("RestocksToday", 0);
		this.assignProfessionWhenSpawned = valueInput.getBooleanOr("AssignProfessionWhenSpawned", false);
	}

	@Override
	public boolean removeWhenFarAway(double d) {
		return false;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		if (this.isSleeping()) {
			return null;
		} else {
			return this.isTrading() ? SoundEvents.VILLAGER_TRADE : SoundEvents.VILLAGER_AMBIENT;
		}
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundEvents.VILLAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.VILLAGER_DEATH;
	}

	public void playWorkSound() {
		this.makeSound(this.getVillagerData().profession().value().workSound());
	}

	@Override
	public void setVillagerData(VillagerData villagerData) {
		VillagerData villagerData2 = this.getVillagerData();
		if (!villagerData2.profession().equals(villagerData.profession())) {
			this.offers = null;
		}

		this.entityData.set(DATA_VILLAGER_DATA, villagerData);
	}

	@Override
	public VillagerData getVillagerData() {
		return this.entityData.get(DATA_VILLAGER_DATA);
	}

	@Override
	protected void rewardTradeXp(MerchantOffer merchantOffer) {
		int i = 3 + this.random.nextInt(4);
		this.villagerXp = this.villagerXp + merchantOffer.getXp();
		this.lastTradedPlayer = this.getTradingPlayer();
		if (this.shouldIncreaseLevel()) {
			this.updateMerchantTimer = 40;
			this.increaseProfessionLevelOnUpdate = true;
			i += 5;
		}

		if (merchantOffer.shouldRewardExp()) {
			this.level().addFreshEntity(new ExperienceOrb(this.level(), this.getX(), this.getY() + 0.5, this.getZ(), i));
		}
	}

	@Override
	public void setLastHurtByMob(@Nullable LivingEntity livingEntity) {
		if (livingEntity != null && this.level() instanceof ServerLevel) {
			((ServerLevel)this.level()).onReputationEvent(ReputationEventType.VILLAGER_HURT, livingEntity, this);
			if (this.isAlive() && livingEntity instanceof Player) {
				this.level().broadcastEntityEvent(this, (byte)13);
			}
		}

		super.setLastHurtByMob(livingEntity);
	}

	@Override
	public void die(DamageSource damageSource) {
		LOGGER.info("Villager {} died, message: '{}'", this, damageSource.getLocalizedDeathMessage(this).getString());
		Entity entity = damageSource.getEntity();
		if (entity != null) {
			this.tellWitnessesThatIWasMurdered(entity);
		}

		this.releaseAllPois();
		super.die(damageSource);
	}

	private void releaseAllPois() {
		this.releasePoi(MemoryModuleType.HOME);
		this.releasePoi(MemoryModuleType.JOB_SITE);
		this.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
		this.releasePoi(MemoryModuleType.MEETING_POINT);
	}

	private void tellWitnessesThatIWasMurdered(Entity entity) {
		if (this.level() instanceof ServerLevel serverLevel) {
			Optional<NearestVisibleLivingEntities> optional = this.brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
			if (!optional.isEmpty()) {
				optional.get()
					.findAll(ReputationEventHandler.class::isInstance)
					.forEach(livingEntity -> serverLevel.onReputationEvent(ReputationEventType.VILLAGER_KILLED, entity, (ReputationEventHandler)livingEntity));
			}
		}
	}

	public void releasePoi(MemoryModuleType<GlobalPos> memoryModuleType) {
		if (this.level() instanceof ServerLevel) {
			MinecraftServer minecraftServer = ((ServerLevel)this.level()).getServer();
			this.brain.getMemory(memoryModuleType).ifPresent(globalPos -> {
				ServerLevel serverLevel = minecraftServer.getLevel(globalPos.dimension());
				if (serverLevel != null) {
					PoiManager poiManager = serverLevel.getPoiManager();
					Optional<Holder<PoiType>> optional = poiManager.getType(globalPos.pos());
					BiPredicate<Villager, Holder<PoiType>> biPredicate = POI_MEMORIES.get(memoryModuleType);
					if (optional.isPresent() && biPredicate.test(this, optional.get())) {
						poiManager.release(globalPos.pos());
						serverLevel.debugSynchronizers().updatePoi(globalPos.pos());
					}
				}
			});
		}
	}

	@Override
	public boolean canBreed() {
		return this.foodLevel + this.countFoodPointsInInventory() >= 12 && !this.isSleeping() && this.getAge() == 0;
	}

	private boolean hungry() {
		return this.foodLevel < 12;
	}

	private void eatUntilFull() {
		if (this.hungry() && this.countFoodPointsInInventory() != 0) {
			for (int i = 0; i < this.getInventory().getContainerSize(); i++) {
				ItemStack itemStack = this.getInventory().getItem(i);
				if (!itemStack.isEmpty()) {
					Integer integer = FOOD_POINTS.get(itemStack.getItem());
					if (integer != null) {
						int j = itemStack.getCount();

						for (int k = j; k > 0; k--) {
							this.foodLevel = this.foodLevel + integer;
							this.getInventory().removeItem(i, 1);
							if (!this.hungry()) {
								return;
							}
						}
					}
				}
			}
		}
	}

	public int getPlayerReputation(Player player) {
		return this.gossips.getReputation(player.getUUID(), gossipType -> true);
	}

	private void digestFood(int i) {
		this.foodLevel -= i;
	}

	public void eatAndDigestFood() {
		this.eatUntilFull();
		this.digestFood(12);
	}

	public void setOffers(MerchantOffers merchantOffers) {
		this.offers = merchantOffers;
	}

	private boolean shouldIncreaseLevel() {
		int i = this.getVillagerData().level();
		return VillagerData.canLevelUp(i) && this.villagerXp >= VillagerData.getMaxXpPerLevel(i);
	}

	private void increaseMerchantCareer(ServerLevel serverLevel) {
		this.setVillagerData(this.getVillagerData().withLevel(this.getVillagerData().level() + 1));
		this.updateTrades(serverLevel);
	}

	@Override
	protected Component getTypeName() {
		return this.getVillagerData().profession().value().name();
	}

	@Override
	public void handleEntityEvent(byte b) {
		if (b == 12) {
			this.addParticlesAroundSelf(ParticleTypes.HEART);
		} else if (b == 13) {
			this.addParticlesAroundSelf(ParticleTypes.ANGRY_VILLAGER);
		} else if (b == 14) {
			this.addParticlesAroundSelf(ParticleTypes.HAPPY_VILLAGER);
		} else if (b == 42) {
			this.addParticlesAroundSelf(ParticleTypes.SPLASH);
		} else {
			super.handleEntityEvent(b);
		}
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(
		ServerLevelAccessor serverLevelAccessor, DifficultyInstance difficultyInstance, EntitySpawnReason entitySpawnReason, @Nullable SpawnGroupData spawnGroupData
	) {
		if (entitySpawnReason == EntitySpawnReason.BREEDING) {
			this.setVillagerData(this.getVillagerData().withProfession(serverLevelAccessor.registryAccess(), VillagerProfession.NONE));
		}

		if (entitySpawnReason == EntitySpawnReason.COMMAND
			|| entitySpawnReason == EntitySpawnReason.SPAWN_ITEM_USE
			|| EntitySpawnReason.isSpawner(entitySpawnReason)
			|| entitySpawnReason == EntitySpawnReason.DISPENSER) {
			this.setVillagerData(
				this.getVillagerData().withType(serverLevelAccessor.registryAccess(), VillagerType.byBiome(serverLevelAccessor.getBiome(this.blockPosition())))
			);
		}

		if (entitySpawnReason == EntitySpawnReason.STRUCTURE) {
			this.assignProfessionWhenSpawned = true;
		}

		return super.finalizeSpawn(serverLevelAccessor, difficultyInstance, entitySpawnReason, spawnGroupData);
	}

	public @Nullable Villager getBreedOffspring(ServerLevel serverLevel, AgeableMob ageableMob) {
		double d = this.random.nextDouble();
		Holder<VillagerType> holder;
		if (d < 0.5) {
			holder = serverLevel.registryAccess().getOrThrow(VillagerType.byBiome(serverLevel.getBiome(this.blockPosition())));
		} else if (d < 0.75) {
			holder = this.getVillagerData().type();
		} else {
			holder = ((Villager)ageableMob).getVillagerData().type();
		}

		Villager villager = new Villager(EntityType.VILLAGER, serverLevel, holder);
		villager.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(villager.blockPosition()), EntitySpawnReason.BREEDING, null);
		return villager;
	}

	@Override
	public void thunderHit(ServerLevel serverLevel, LightningBolt lightningBolt) {
		if (serverLevel.getDifficulty() != Difficulty.PEACEFUL) {
			LOGGER.info("Villager {} was struck by lightning {}.", this, lightningBolt);
			Witch witch = this.convertTo(EntityType.WITCH, ConversionParams.single(this, false, false), witchx -> {
				witchx.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(witchx.blockPosition()), EntitySpawnReason.CONVERSION, null);
				witchx.setPersistenceRequired();
				this.releaseAllPois();
			});
			if (witch == null) {
				super.thunderHit(serverLevel, lightningBolt);
			}
		} else {
			super.thunderHit(serverLevel, lightningBolt);
		}
	}

	@Override
	protected void pickUpItem(ServerLevel serverLevel, ItemEntity itemEntity) {
		InventoryCarrier.pickUpItem(serverLevel, this, this, itemEntity);
	}

	@Override
	public boolean wantsToPickUp(ServerLevel serverLevel, ItemStack itemStack) {
		Item item = itemStack.getItem();
		return (itemStack.is(ItemTags.VILLAGER_PICKS_UP) || this.getVillagerData().profession().value().requestedItems().contains(item))
			&& this.getInventory().canAddItem(itemStack);
	}

	public boolean hasExcessFood() {
		return this.countFoodPointsInInventory() >= 24;
	}

	public boolean wantsMoreFood() {
		return this.countFoodPointsInInventory() < 12;
	}

	private int countFoodPointsInInventory() {
		SimpleContainer simpleContainer = this.getInventory();
		return FOOD_POINTS.entrySet().stream().mapToInt(entry -> simpleContainer.countItem(entry.getKey()) * entry.getValue()).sum();
	}

	public boolean hasFarmSeeds() {
		return this.getInventory().hasAnyMatching(itemStack -> itemStack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS));
	}

	@Override
	protected void updateTrades(ServerLevel serverLevel) {
		VillagerData villagerData = this.getVillagerData();
		ResourceKey<VillagerProfession> resourceKey = villagerData.profession().unwrapKey().orElse(null);
		if (resourceKey != null) {
			Int2ObjectMap<VillagerTrades.ItemListing[]> int2ObjectMap2;
			if (this.level().enabledFeatures().contains(FeatureFlags.TRADE_REBALANCE)) {
				Int2ObjectMap<VillagerTrades.ItemListing[]> int2ObjectMap = VillagerTrades.EXPERIMENTAL_TRADES.get(resourceKey);
				int2ObjectMap2 = int2ObjectMap != null ? int2ObjectMap : VillagerTrades.TRADES.get(resourceKey);
			} else {
				int2ObjectMap2 = VillagerTrades.TRADES.get(resourceKey);
			}

			if (int2ObjectMap2 != null && !int2ObjectMap2.isEmpty()) {
				VillagerTrades.ItemListing[] itemListings = int2ObjectMap2.get(villagerData.level());
				if (itemListings != null) {
					MerchantOffers merchantOffers = this.getOffers();
					this.addOffersFromItemListings(serverLevel, merchantOffers, itemListings, 2);
					if (SharedConstants.DEBUG_UNLOCK_ALL_TRADES && villagerData.level() < int2ObjectMap2.size()) {
						this.increaseMerchantCareer(serverLevel);
					}
				}
			}
		}
	}

	public void gossip(ServerLevel serverLevel, Villager villager, long l) {
		if ((l < this.lastGossipTime || l >= this.lastGossipTime + 1200L) && (l < villager.lastGossipTime || l >= villager.lastGossipTime + 1200L)) {
			this.gossips.transferFrom(villager.gossips, this.random, 10);
			this.lastGossipTime = l;
			villager.lastGossipTime = l;
			this.spawnGolemIfNeeded(serverLevel, l, 5);
		}
	}

	private void maybeDecayGossip() {
		long l = this.level().getGameTime();
		if (this.lastGossipDecayTime == 0L) {
			this.lastGossipDecayTime = l;
		} else if (l >= this.lastGossipDecayTime + 24000L) {
			this.gossips.decay();
			this.lastGossipDecayTime = l;
		}
	}

	public void spawnGolemIfNeeded(ServerLevel serverLevel, long l, int i) {
		if (this.wantsToSpawnGolem(l)) {
			AABB aABB = this.getBoundingBox().inflate(10.0, 10.0, 10.0);
			List<Villager> list = serverLevel.getEntitiesOfClass(Villager.class, aABB);
			List<Villager> list2 = list.stream().filter(villager -> villager.wantsToSpawnGolem(l)).limit(5L).toList();
			if (list2.size() >= i) {
				if (!SpawnUtil.trySpawnMob(
						EntityType.IRON_GOLEM, EntitySpawnReason.MOB_SUMMONED, serverLevel, this.blockPosition(), 10, 8, 6, SpawnUtil.Strategy.LEGACY_IRON_GOLEM, false
					)
					.isEmpty()) {
					list.forEach(GolemSensor::golemDetected);
				}
			}
		}
	}

	public boolean wantsToSpawnGolem(long l) {
		return !this.golemSpawnConditionsMet(this.level().getGameTime()) ? false : !this.brain.hasMemoryValue(MemoryModuleType.GOLEM_DETECTED_RECENTLY);
	}

	@Override
	public void onReputationEventFrom(ReputationEventType reputationEventType, Entity entity) {
		if (reputationEventType == ReputationEventType.ZOMBIE_VILLAGER_CURED) {
			this.gossips.add(entity.getUUID(), GossipType.MAJOR_POSITIVE, 20);
			this.gossips.add(entity.getUUID(), GossipType.MINOR_POSITIVE, 25);
		} else if (reputationEventType == ReputationEventType.TRADE) {
			this.gossips.add(entity.getUUID(), GossipType.TRADING, 2);
		} else if (reputationEventType == ReputationEventType.VILLAGER_HURT) {
			this.gossips.add(entity.getUUID(), GossipType.MINOR_NEGATIVE, 25);
		} else if (reputationEventType == ReputationEventType.VILLAGER_KILLED) {
			this.gossips.add(entity.getUUID(), GossipType.MAJOR_NEGATIVE, 25);
		}
	}

	@Override
	public int getVillagerXp() {
		return this.villagerXp;
	}

	public void setVillagerXp(int i) {
		this.villagerXp = i;
	}

	private void resetNumberOfRestocks() {
		this.catchUpDemand();
		this.numberOfRestocksToday = 0;
	}

	public GossipContainer getGossips() {
		return this.gossips;
	}

	public void setGossips(GossipContainer gossipContainer) {
		this.gossips.putAll(gossipContainer);
	}

	@Override
	public void startSleeping(BlockPos blockPos) {
		super.startSleeping(blockPos);
		this.brain.setMemory(MemoryModuleType.LAST_SLEPT, this.level().getGameTime());
		this.brain.eraseMemory(MemoryModuleType.WALK_TARGET);
		this.brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
	}

	@Override
	public void stopSleeping() {
		super.stopSleeping();
		this.brain.setMemory(MemoryModuleType.LAST_WOKEN, this.level().getGameTime());
	}

	private boolean golemSpawnConditionsMet(long l) {
		Optional<Long> optional = this.brain.getMemory(MemoryModuleType.LAST_SLEPT);
		return optional.filter(long_ -> l - long_ < 24000L).isPresent();
	}

	@Override
	public <T> @Nullable T get(DataComponentType<? extends T> dataComponentType) {
		return dataComponentType == DataComponents.VILLAGER_VARIANT
			? castComponentValue((DataComponentType<T>)dataComponentType, this.getVillagerData().type())
			: super.get(dataComponentType);
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter dataComponentGetter) {
		this.applyImplicitComponentIfPresent(dataComponentGetter, DataComponents.VILLAGER_VARIANT);
		super.applyImplicitComponents(dataComponentGetter);
	}

	@Override
	protected <T> boolean applyImplicitComponent(DataComponentType<T> dataComponentType, T object) {
		if (dataComponentType == DataComponents.VILLAGER_VARIANT) {
			Holder<VillagerType> holder = castComponentValue(DataComponents.VILLAGER_VARIANT, object);
			this.setVillagerData(this.getVillagerData().withType(holder));
			return true;
		} else {
			return super.applyImplicitComponent(dataComponentType, object);
		}
	}
}
