package net.minecraft.world.item;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class SpawnEggItem extends Item {
	private static final Map<EntityType<?>, SpawnEggItem> BY_ID = Maps.newIdentityHashMap();

	public SpawnEggItem(Item.Properties properties) {
		super(properties);
		TypedEntityData<EntityType<?>> typedEntityData = this.components().get(DataComponents.ENTITY_DATA);
		if (typedEntityData != null) {
			BY_ID.put(typedEntityData.type(), this);
		}
	}

	@Override
	public InteractionResult useOn(UseOnContext useOnContext) {
		Level level = useOnContext.getLevel();
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		} else {
			ItemStack itemStack = useOnContext.getItemInHand();
			BlockPos blockPos = useOnContext.getClickedPos();
			Direction direction = useOnContext.getClickedFace();
			BlockState blockState = level.getBlockState(blockPos);
			if (level.getBlockEntity(blockPos) instanceof Spawner spawner) {
				EntityType<?> entityType = this.getType(itemStack);
				if (entityType == null) {
					return InteractionResult.FAIL;
				}

				if (!serverLevel.isSpawnerBlockEnabled()) {
					if (useOnContext.getPlayer() instanceof ServerPlayer serverPlayer) {
						serverPlayer.sendSystemMessage(Component.translatable("advMode.notEnabled.spawner"));
					}

					return InteractionResult.FAIL;
				} else {
					spawner.setEntityId(entityType, level.getRandom());
					level.sendBlockUpdated(blockPos, blockState, blockState, 3);
					level.gameEvent(useOnContext.getPlayer(), GameEvent.BLOCK_CHANGE, blockPos);
					itemStack.shrink(1);
					return InteractionResult.SUCCESS;
				}
			} else {
				BlockPos blockPos2;
				if (blockState.getCollisionShape(level, blockPos).isEmpty()) {
					blockPos2 = blockPos;
				} else {
					blockPos2 = blockPos.relative(direction);
				}

				return this.spawnMob(useOnContext.getPlayer(), itemStack, level, blockPos2, true, !Objects.equals(blockPos, blockPos2) && direction == Direction.UP);
			}
		}
	}

	private InteractionResult spawnMob(@Nullable LivingEntity livingEntity, ItemStack itemStack, Level level, BlockPos blockPos, boolean bl, boolean bl2) {
		EntityType<?> entityType = this.getType(itemStack);
		if (entityType == null) {
			return InteractionResult.FAIL;
		}

		if (!entityType.isAllowedInPeaceful() && level.getDifficulty() == Difficulty.PEACEFUL) {
			return InteractionResult.FAIL;
		}

		if (entityType.spawn((ServerLevel)level, itemStack, livingEntity, blockPos, EntitySpawnReason.SPAWN_ITEM_USE, bl, bl2) != null) {
			itemStack.consume(1, livingEntity);
			level.gameEvent(livingEntity, GameEvent.ENTITY_PLACE, blockPos);
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand interactionHand) {
		ItemStack itemStack = player.getItemInHand(interactionHand);
		BlockHitResult blockHitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
		if (blockHitResult.getType() != HitResult.Type.BLOCK) {
			return InteractionResult.PASS;
		}

		if (level instanceof ServerLevel serverLevel) {
			BlockPos blockPos = blockHitResult.getBlockPos();
			if (!(level.getBlockState(blockPos).getBlock() instanceof LiquidBlock)) {
				return InteractionResult.PASS;
			}

			if (level.mayInteract(player, blockPos) && player.mayUseItemAt(blockPos, blockHitResult.getDirection(), itemStack)) {
				InteractionResult interactionResult = this.spawnMob(player, itemStack, level, blockPos, false, false);
				if (interactionResult == InteractionResult.SUCCESS) {
					player.awardStat(Stats.ITEM_USED.get(this));
				}

				return interactionResult;
			} else {
				return InteractionResult.FAIL;
			}
		} else {
			return InteractionResult.SUCCESS;
		}
	}

	public boolean spawnsEntity(ItemStack itemStack, EntityType<?> entityType) {
		return Objects.equals(this.getType(itemStack), entityType);
	}

	public static @Nullable SpawnEggItem byId(@Nullable EntityType<?> entityType) {
		return BY_ID.get(entityType);
	}

	public static Iterable<SpawnEggItem> eggs() {
		return Iterables.unmodifiableIterable(BY_ID.values());
	}

	public @Nullable EntityType<?> getType(ItemStack itemStack) {
		TypedEntityData<EntityType<?>> typedEntityData = itemStack.get(DataComponents.ENTITY_DATA);
		return typedEntityData != null ? typedEntityData.type() : null;
	}

	@Override
	public FeatureFlagSet requiredFeatures() {
		return Optional.ofNullable(this.components().get(DataComponents.ENTITY_DATA))
			.map(TypedEntityData::type)
			.map(EntityType::requiredFeatures)
			.orElseGet(FeatureFlagSet::of);
	}

	public Optional<Mob> spawnOffspringFromSpawnEgg(
		Player player, Mob mob, EntityType<? extends Mob> entityType, ServerLevel serverLevel, Vec3 vec3, ItemStack itemStack
	) {
		if (!this.spawnsEntity(itemStack, entityType)) {
			return Optional.empty();
		}

		Mob mob2;
		if (mob instanceof AgeableMob) {
			mob2 = ((AgeableMob)mob).getBreedOffspring(serverLevel, (AgeableMob)mob);
		} else {
			mob2 = entityType.create(serverLevel, EntitySpawnReason.SPAWN_ITEM_USE);
		}

		if (mob2 == null) {
			return Optional.empty();
		}

		mob2.setBaby(true);
		if (!mob2.isBaby()) {
			return Optional.empty();
		}

		mob2.snapTo(vec3.x(), vec3.y(), vec3.z(), 0.0F, 0.0F);
		mob2.applyComponentsFromItemStack(itemStack);
		serverLevel.addFreshEntityWithPassengers(mob2);
		itemStack.consume(1, player);
		return Optional.of(mob2);
	}

	@Override
	public boolean shouldPrintOpWarning(ItemStack itemStack, @Nullable Player player) {
		if (player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
			TypedEntityData<EntityType<?>> typedEntityData = itemStack.get(DataComponents.ENTITY_DATA);
			if (typedEntityData != null) {
				return typedEntityData.type().onlyOpCanSetNbt();
			}
		}

		return false;
	}
}
