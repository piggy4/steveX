package net.minecraft.world.entity.animal.cow;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SpellParticleOption;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SuspiciousEffectHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.jspecify.annotations.Nullable;

public class MushroomCow extends AbstractCow implements Shearable {
	private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.defineId(MushroomCow.class, EntityDataSerializers.INT);
	private static final int MUTATE_CHANCE = 1024;
	private static final String TAG_STEW_EFFECTS = "stew_effects";
	private @Nullable SuspiciousStewEffects stewEffects;
	private @Nullable UUID lastLightningBoltUUID;

	public MushroomCow(EntityType<? extends MushroomCow> entityType, Level level) {
		super(entityType, level);
	}

	@Override
	public float getWalkTargetValue(BlockPos blockPos, LevelReader levelReader) {
		return levelReader.getBlockState(blockPos.below()).is(Blocks.MYCELIUM) ? 10.0F : levelReader.getPathfindingCostFromLightLevels(blockPos);
	}

	public static boolean checkMushroomSpawnRules(
		EntityType<MushroomCow> entityType, LevelAccessor levelAccessor, EntitySpawnReason entitySpawnReason, BlockPos blockPos, RandomSource randomSource
	) {
		return levelAccessor.getBlockState(blockPos.below()).is(BlockTags.MOOSHROOMS_SPAWNABLE_ON) && isBrightEnoughToSpawn(levelAccessor, blockPos);
	}

	@Override
	public void thunderHit(ServerLevel serverLevel, LightningBolt lightningBolt) {
		UUID uUID = lightningBolt.getUUID();
		if (!uUID.equals(this.lastLightningBoltUUID)) {
			this.setVariant(this.getVariant() == MushroomCow.Variant.RED ? MushroomCow.Variant.BROWN : MushroomCow.Variant.RED);
			this.lastLightningBoltUUID = uUID;
			this.playSound(SoundEvents.MOOSHROOM_CONVERT, 2.0F, 1.0F);
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_TYPE, MushroomCow.Variant.DEFAULT.id);
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand interactionHand) {
		ItemStack itemStack = player.getItemInHand(interactionHand);
		if (itemStack.is(Items.BOWL) && !this.isBaby()) {
			boolean bl = false;
			ItemStack itemStack2;
			if (this.stewEffects != null) {
				bl = true;
				itemStack2 = new ItemStack(Items.SUSPICIOUS_STEW);
				itemStack2.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, this.stewEffects);
				this.stewEffects = null;
			} else {
				itemStack2 = new ItemStack(Items.MUSHROOM_STEW);
			}

			ItemStack itemStack3 = ItemUtils.createFilledResult(itemStack, player, itemStack2, false);
			player.setItemInHand(interactionHand, itemStack3);
			SoundEvent soundEvent;
			if (bl) {
				soundEvent = SoundEvents.MOOSHROOM_MILK_SUSPICIOUSLY;
			} else {
				soundEvent = SoundEvents.MOOSHROOM_MILK;
			}

			this.playSound(soundEvent, 1.0F, 1.0F);
			return InteractionResult.SUCCESS;
		} else if (itemStack.is(Items.SHEARS) && this.readyForShearing()) {
			if (this.level() instanceof ServerLevel serverLevel) {
				this.shear(serverLevel, SoundSource.PLAYERS, itemStack);
				this.gameEvent(GameEvent.SHEAR, player);
				itemStack.hurtAndBreak(1, player, interactionHand.asEquipmentSlot());
			}

			return InteractionResult.SUCCESS;
		} else if (this.getVariant() == MushroomCow.Variant.BROWN) {
			Optional<SuspiciousStewEffects> optional = this.getEffectsFromItemStack(itemStack);
			if (optional.isEmpty()) {
				return super.mobInteract(player, interactionHand);
			}

			if (this.stewEffects != null) {
				for (int i = 0; i < 2; i++) {
					this.level()
						.addParticle(
							ParticleTypes.SMOKE,
							this.getX() + this.random.nextDouble() / 2.0,
							this.getY(0.5),
							this.getZ() + this.random.nextDouble() / 2.0,
							0.0,
							this.random.nextDouble() / 5.0,
							0.0
						);
				}
			} else {
				itemStack.consume(1, player);
				SpellParticleOption spellParticleOption = SpellParticleOption.create(ParticleTypes.EFFECT, -1, 1.0F);

				for (int j = 0; j < 4; j++) {
					this.level()
						.addParticle(
							spellParticleOption,
							this.getX() + this.random.nextDouble() / 2.0,
							this.getY(0.5),
							this.getZ() + this.random.nextDouble() / 2.0,
							0.0,
							this.random.nextDouble() / 5.0,
							0.0
						);
				}

				this.stewEffects = optional.get();
				this.playSound(SoundEvents.MOOSHROOM_EAT, 2.0F, 1.0F);
			}

			return InteractionResult.SUCCESS;
		} else {
			return super.mobInteract(player, interactionHand);
		}
	}

	@Override
	public void shear(ServerLevel serverLevel, SoundSource soundSource, ItemStack itemStack) {
		serverLevel.playSound(null, this, SoundEvents.MOOSHROOM_SHEAR, soundSource, 1.0F, 1.0F);
		this.convertTo(EntityType.COW, ConversionParams.single(this, false, false), cow -> {
			serverLevel.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(0.5), this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			this.dropFromShearingLootTable(serverLevel, BuiltInLootTables.SHEAR_MOOSHROOM, itemStack, (serverLevelxx, itemStackxx) -> {
				for (int i = 0; i < itemStackxx.getCount(); i++) {
					serverLevelxx.addFreshEntity(new ItemEntity(this.level(), this.getX(), this.getY(1.0), this.getZ(), itemStackxx.copyWithCount(1)));
				}
			});
		});
	}

	@Override
	public boolean readyForShearing() {
		return this.isAlive() && !this.isBaby();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.store("Type", MushroomCow.Variant.CODEC, this.getVariant());
		valueOutput.storeNullable("stew_effects", SuspiciousStewEffects.CODEC, this.stewEffects);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.setVariant(valueInput.read("Type", MushroomCow.Variant.CODEC).orElse(MushroomCow.Variant.DEFAULT));
		this.stewEffects = valueInput.read("stew_effects", SuspiciousStewEffects.CODEC).orElse(null);
	}

	private Optional<SuspiciousStewEffects> getEffectsFromItemStack(ItemStack itemStack) {
		SuspiciousEffectHolder suspiciousEffectHolder = SuspiciousEffectHolder.tryGet(itemStack.getItem());
		return suspiciousEffectHolder != null ? Optional.of(suspiciousEffectHolder.getSuspiciousEffects()) : Optional.empty();
	}

	private void setVariant(MushroomCow.Variant variant) {
		this.entityData.set(DATA_TYPE, variant.id);
	}

	public MushroomCow.Variant getVariant() {
		return MushroomCow.Variant.byId(this.entityData.get(DATA_TYPE));
	}

	@Override
	public <T> @Nullable T get(DataComponentType<? extends T> dataComponentType) {
		return dataComponentType == DataComponents.MOOSHROOM_VARIANT
			? castComponentValue((DataComponentType<T>)dataComponentType, this.getVariant())
			: super.get(dataComponentType);
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter dataComponentGetter) {
		this.applyImplicitComponentIfPresent(dataComponentGetter, DataComponents.MOOSHROOM_VARIANT);
		super.applyImplicitComponents(dataComponentGetter);
	}

	@Override
	protected <T> boolean applyImplicitComponent(DataComponentType<T> dataComponentType, T object) {
		if (dataComponentType == DataComponents.MOOSHROOM_VARIANT) {
			this.setVariant(castComponentValue(DataComponents.MOOSHROOM_VARIANT, object));
			return true;
		} else {
			return super.applyImplicitComponent(dataComponentType, object);
		}
	}

	public @Nullable MushroomCow getBreedOffspring(ServerLevel serverLevel, AgeableMob ageableMob) {
		MushroomCow mushroomCow = EntityType.MOOSHROOM.create(serverLevel, EntitySpawnReason.BREEDING);
		if (mushroomCow != null) {
			mushroomCow.setVariant(this.getOffspringVariant((MushroomCow)ageableMob));
		}

		return mushroomCow;
	}

	private MushroomCow.Variant getOffspringVariant(MushroomCow mushroomCow) {
		MushroomCow.Variant variant = this.getVariant();
		MushroomCow.Variant variant2 = mushroomCow.getVariant();
		MushroomCow.Variant variant3;
		if (variant == variant2 && this.random.nextInt(1024) == 0) {
			variant3 = variant == MushroomCow.Variant.BROWN ? MushroomCow.Variant.RED : MushroomCow.Variant.BROWN;
		} else {
			variant3 = this.random.nextBoolean() ? variant : variant2;
		}

		return variant3;
	}

	public enum Variant implements StringRepresentable {
		RED("red", 0, Blocks.RED_MUSHROOM.defaultBlockState()),
		BROWN("brown", 1, Blocks.BROWN_MUSHROOM.defaultBlockState());

		public static final MushroomCow.Variant DEFAULT = RED;
		public static final Codec<MushroomCow.Variant> CODEC = StringRepresentable.fromEnum(MushroomCow.Variant::values);
		private static final IntFunction<MushroomCow.Variant> BY_ID = ByIdMap.continuous(MushroomCow.Variant::id, values(), ByIdMap.OutOfBoundsStrategy.CLAMP);
		public static final StreamCodec<ByteBuf, MushroomCow.Variant> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, MushroomCow.Variant::id);
		private final String type;
		final int id;
		private final BlockState blockState;

		Variant(final String string2, final int j, final BlockState blockState) {
			this.type = string2;
			this.id = j;
			this.blockState = blockState;
		}

		public BlockState getBlockState() {
			return this.blockState;
		}

		@Override
		public String getSerializedName() {
			return this.type;
		}

		private int id() {
			return this.id;
		}

		static MushroomCow.Variant byId(int i) {
			return BY_ID.apply(i);
		}
	}
}
