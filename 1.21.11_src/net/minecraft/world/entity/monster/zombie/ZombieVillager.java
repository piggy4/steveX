package net.minecraft.world.entity.monster.zombie;

import com.google.common.annotations.VisibleForTesting;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerDataHolder;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class ZombieVillager extends Zombie implements VillagerDataHolder {
	private static final EntityDataAccessor<Boolean> DATA_CONVERTING_ID = SynchedEntityData.defineId(ZombieVillager.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<VillagerData> DATA_VILLAGER_DATA = SynchedEntityData.defineId(
		ZombieVillager.class, EntityDataSerializers.VILLAGER_DATA
	);
	private static final int VILLAGER_CONVERSION_WAIT_MIN = 3600;
	private static final int VILLAGER_CONVERSION_WAIT_MAX = 6000;
	private static final int MAX_SPECIAL_BLOCKS_COUNT = 14;
	private static final int SPECIAL_BLOCK_RADIUS = 4;
	private static final int NOT_CONVERTING = -1;
	private static final int DEFAULT_XP = 0;
	private static final Set<EntitySpawnReason> REASONS_NOT_TO_SET_TYPE = EnumSet.of(
		EntitySpawnReason.LOAD,
		EntitySpawnReason.DIMENSION_TRAVEL,
		EntitySpawnReason.CONVERSION,
		EntitySpawnReason.SPAWN_ITEM_USE,
		EntitySpawnReason.SPAWNER,
		EntitySpawnReason.TRIAL_SPAWNER
	);
	private int villagerConversionTime;
	private @Nullable UUID conversionStarter;
	private @Nullable GossipContainer gossips;
	private @Nullable MerchantOffers tradeOffers;
	private int villagerXp = 0;

	public ZombieVillager(EntityType<? extends ZombieVillager> entityType, Level level) {
		super(entityType, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_CONVERTING_ID, false);
		builder.define(DATA_VILLAGER_DATA, this.initializeVillagerData());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.store("VillagerData", VillagerData.CODEC, this.getVillagerData());
		valueOutput.storeNullable("Offers", MerchantOffers.CODEC, this.tradeOffers);
		valueOutput.storeNullable("Gossips", GossipContainer.CODEC, this.gossips);
		valueOutput.putInt("ConversionTime", this.isConverting() ? this.villagerConversionTime : -1);
		valueOutput.storeNullable("ConversionPlayer", UUIDUtil.CODEC, this.conversionStarter);
		valueOutput.putInt("Xp", this.villagerXp);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.entityData.set(DATA_VILLAGER_DATA, valueInput.read("VillagerData", VillagerData.CODEC).orElseGet(this::initializeVillagerData));
		this.tradeOffers = valueInput.read("Offers", MerchantOffers.CODEC).orElse(null);
		this.gossips = valueInput.read("Gossips", GossipContainer.CODEC).orElse(null);
		int i = valueInput.getIntOr("ConversionTime", -1);
		if (i != -1) {
			UUID uUID = valueInput.read("ConversionPlayer", UUIDUtil.CODEC).orElse(null);
			this.startConverting(uUID, i);
		} else {
			this.getEntityData().set(DATA_CONVERTING_ID, false);
			this.villagerConversionTime = -1;
		}

		this.villagerXp = valueInput.getIntOr("Xp", 0);
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(
		ServerLevelAccessor serverLevelAccessor, DifficultyInstance difficultyInstance, EntitySpawnReason entitySpawnReason, @Nullable SpawnGroupData spawnGroupData
	) {
		if (!REASONS_NOT_TO_SET_TYPE.contains(entitySpawnReason)) {
			this.setVillagerData(
				this.getVillagerData().withType(serverLevelAccessor.registryAccess(), VillagerType.byBiome(serverLevelAccessor.getBiome(this.blockPosition())))
			);
		}

		return super.finalizeSpawn(serverLevelAccessor, difficultyInstance, entitySpawnReason, spawnGroupData);
	}

	private VillagerData initializeVillagerData() {
		Optional<Holder.Reference<VillagerProfession>> optional = BuiltInRegistries.VILLAGER_PROFESSION.getRandom(this.random);
		VillagerData villagerData = Villager.createDefaultVillagerData();
		if (optional.isPresent()) {
			villagerData = villagerData.withProfession(optional.get());
		}

		return villagerData;
	}

	@Override
	public void tick() {
		if (!this.level().isClientSide() && this.isAlive() && this.isConverting()) {
			int i = this.getConversionProgress();
			this.villagerConversionTime -= i;
			if (this.villagerConversionTime <= 0) {
				this.finishConversion((ServerLevel)this.level());
			}
		}

		super.tick();
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand interactionHand) {
		ItemStack itemStack = player.getItemInHand(interactionHand);
		if (itemStack.is(Items.GOLDEN_APPLE)) {
			if (this.hasEffect(MobEffects.WEAKNESS)) {
				itemStack.consume(1, player);
				if (!this.level().isClientSide()) {
					this.startConverting(player.getUUID(), this.random.nextInt(2401) + 3600);
				}

				return InteractionResult.SUCCESS_SERVER;
			} else {
				return InteractionResult.CONSUME;
			}
		} else {
			return super.mobInteract(player, interactionHand);
		}
	}

	@Override
	protected boolean convertsInWater() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double d) {
		return !this.isConverting() && this.villagerXp == 0;
	}

	public boolean isConverting() {
		return this.getEntityData().get(DATA_CONVERTING_ID);
	}

	private void startConverting(@Nullable UUID uUID, int i) {
		this.conversionStarter = uUID;
		this.villagerConversionTime = i;
		this.getEntityData().set(DATA_CONVERTING_ID, true);
		this.removeEffect(MobEffects.WEAKNESS);
		this.addEffect(new MobEffectInstance(MobEffects.STRENGTH, i, Math.min(this.level().getDifficulty().getId() - 1, 0)));
		this.level().broadcastEntityEvent(this, (byte)16);
	}

	@Override
	public void handleEntityEvent(byte b) {
		if (b == 16) {
			if (!this.isSilent()) {
				this.level()
					.playLocalSound(
						this.getX(),
						this.getEyeY(),
						this.getZ(),
						SoundEvents.ZOMBIE_VILLAGER_CURE,
						this.getSoundSource(),
						1.0F + this.random.nextFloat(),
						this.random.nextFloat() * 0.7F + 0.3F,
						false
					);
			}
		} else {
			super.handleEntityEvent(b);
		}
	}

	private void finishConversion(ServerLevel serverLevel) {
		this.convertTo(
			EntityType.VILLAGER,
			ConversionParams.single(this, false, false),
			villager -> {
				for (EquipmentSlot equipmentSlot : this.dropPreservedEquipment(
					serverLevel, itemStack -> !EnchantmentHelper.has(itemStack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)
				)) {
					SlotAccess slotAccess = villager.getSlot(equipmentSlot.getIndex() + 300);
					if (slotAccess != null) {
						slotAccess.set(this.getItemBySlot(equipmentSlot));
					}
				}

				villager.setVillagerData(this.getVillagerData());
				if (this.gossips != null) {
					villager.setGossips(this.gossips);
				}

				if (this.tradeOffers != null) {
					villager.setOffers(this.tradeOffers.copy());
				}

				villager.setVillagerXp(this.villagerXp);
				villager.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(villager.blockPosition()), EntitySpawnReason.CONVERSION, null);
				villager.refreshBrain(serverLevel);
				if (this.conversionStarter != null) {
					Player player = serverLevel.getPlayerByUUID(this.conversionStarter);
					if (player instanceof ServerPlayer) {
						CriteriaTriggers.CURED_ZOMBIE_VILLAGER.trigger((ServerPlayer)player, this, villager);
						serverLevel.onReputationEvent(ReputationEventType.ZOMBIE_VILLAGER_CURED, player, villager);
					}
				}

				villager.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200, 0));
				if (!this.isSilent()) {
					serverLevel.levelEvent(null, 1027, this.blockPosition(), 0);
				}
			}
		);
	}

	@VisibleForTesting
	public void setVillagerConversionTime(int i) {
		this.villagerConversionTime = i;
	}

	private int getConversionProgress() {
		int i = 1;
		if (this.random.nextFloat() < 0.01F) {
			int j = 0;
			BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

			for (int k = (int)this.getX() - 4; k < (int)this.getX() + 4 && j < 14; k++) {
				for (int l = (int)this.getY() - 4; l < (int)this.getY() + 4 && j < 14; l++) {
					for (int m = (int)this.getZ() - 4; m < (int)this.getZ() + 4 && j < 14; m++) {
						BlockState blockState = this.level().getBlockState(mutableBlockPos.set(k, l, m));
						if (blockState.is(Blocks.IRON_BARS) || blockState.getBlock() instanceof BedBlock) {
							if (this.random.nextFloat() < 0.3F) {
								i++;
							}

							j++;
						}
					}
				}
			}
		}

		return i;
	}

	@Override
	public float getVoicePitch() {
		return this.isBaby() ? (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 2.0F : (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F;
	}

	@Override
	public SoundEvent getAmbientSound() {
		return SoundEvents.ZOMBIE_VILLAGER_AMBIENT;
	}

	@Override
	public SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundEvents.ZOMBIE_VILLAGER_HURT;
	}

	@Override
	public SoundEvent getDeathSound() {
		return SoundEvents.ZOMBIE_VILLAGER_DEATH;
	}

	@Override
	public SoundEvent getStepSound() {
		return SoundEvents.ZOMBIE_VILLAGER_STEP;
	}

	public void setTradeOffers(MerchantOffers merchantOffers) {
		this.tradeOffers = merchantOffers;
	}

	public void setGossips(GossipContainer gossipContainer) {
		this.gossips = gossipContainer;
	}

	@Override
	public void setVillagerData(VillagerData villagerData) {
		VillagerData villagerData2 = this.getVillagerData();
		if (!villagerData2.profession().equals(villagerData.profession())) {
			this.tradeOffers = null;
		}

		this.entityData.set(DATA_VILLAGER_DATA, villagerData);
	}

	@Override
	public VillagerData getVillagerData() {
		return this.entityData.get(DATA_VILLAGER_DATA);
	}

	public int getVillagerXp() {
		return this.villagerXp;
	}

	public void setVillagerXp(int i) {
		this.villagerXp = i;
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
