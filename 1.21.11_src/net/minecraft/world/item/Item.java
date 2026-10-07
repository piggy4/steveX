package net.minecraft.world.item;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.fabricmc.fabric.api.item.v1.FabricItem.Settings;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.DependantName;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.component.ProvidesTrimMaterial;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.item.v1.FabricItem} injected by mod fabric-item-api-v1</p>
 */
public class Item implements FeatureElement, ItemLike, FabricItem {
	public static final Codec<Holder<Item>> CODEC = BuiltInRegistries.ITEM
		.holderByNameCodec()
		.validate(holder -> holder.is(Items.AIR.builtInRegistryHolder()) ? DataResult.error(() -> "Item must not be minecraft:air") : DataResult.success(holder));
	public static final StreamCodec<RegistryFriendlyByteBuf, Holder<Item>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.ITEM);
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final Map<Block, Item> BY_BLOCK = Maps.newHashMap();
	public static final Identifier BASE_ATTACK_DAMAGE_ID = Identifier.withDefaultNamespace("base_attack_damage");
	public static final Identifier BASE_ATTACK_SPEED_ID = Identifier.withDefaultNamespace("base_attack_speed");
	public static final int DEFAULT_MAX_STACK_SIZE = 64;
	public static final int ABSOLUTE_MAX_STACK_SIZE = 99;
	public static final int MAX_BAR_WIDTH = 13;
	protected static final int APPROXIMATELY_INFINITE_USE_DURATION = 72000;
	private final Holder.Reference<Item> builtInRegistryHolder = BuiltInRegistries.ITEM.createIntrusiveHolder(this);
	private final DataComponentMap components;
	private final @Nullable Item craftingRemainingItem;
	protected final String descriptionId;
	private final FeatureFlagSet requiredFeatures;

	public static int getId(Item item) {
		return item == null ? 0 : BuiltInRegistries.ITEM.getId(item);
	}

	public static Item byId(int i) {
		return BuiltInRegistries.ITEM.byId(i);
	}

	@Deprecated
	public static Item byBlock(Block block) {
		return BY_BLOCK.getOrDefault(block, Items.AIR);
	}

	public Item(Item.Properties properties) {
		this.descriptionId = properties.effectiveDescriptionId();
		this.components = properties.buildAndValidateComponents(Component.translatable(this.descriptionId), properties.effectiveModel());
		this.craftingRemainingItem = properties.craftingRemainingItem;
		this.requiredFeatures = properties.requiredFeatures;
		if (SharedConstants.IS_RUNNING_IN_IDE) {
			String string = this.getClass().getSimpleName();
			if (!string.endsWith("Item")) {
				LOGGER.error("Item classes should end with Item and {} doesn't.", string);
			}
		}
	}

	@Deprecated
	public Holder.Reference<Item> builtInRegistryHolder() {
		return this.builtInRegistryHolder;
	}

	public DataComponentMap components() {
		return this.components;
	}

	public int getDefaultMaxStackSize() {
		return this.components.getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
	}

	public void onUseTick(Level level, LivingEntity livingEntity, ItemStack itemStack, int i) {
	}

	public void onDestroyed(ItemEntity itemEntity) {
	}

	public boolean canDestroyBlock(ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos, LivingEntity livingEntity) {
		Tool tool = itemStack.get(DataComponents.TOOL);
		return tool != null && !tool.canDestroyBlocksInCreative() ? !(livingEntity instanceof Player player && player.getAbilities().instabuild) : true;
	}

	@Override
	public Item asItem() {
		return this;
	}

	public InteractionResult useOn(UseOnContext useOnContext) {
		return InteractionResult.PASS;
	}

	public float getDestroySpeed(ItemStack itemStack, BlockState blockState) {
		Tool tool = itemStack.get(DataComponents.TOOL);
		return tool != null ? tool.getMiningSpeed(blockState) : 1.0F;
	}

	public InteractionResult use(Level level, Player player, InteractionHand interactionHand) {
		ItemStack itemStack = player.getItemInHand(interactionHand);
		Consumable consumable = itemStack.get(DataComponents.CONSUMABLE);
		if (consumable != null) {
			return consumable.startConsuming(player, itemStack, interactionHand);
		} else {
			Equippable equippable = itemStack.get(DataComponents.EQUIPPABLE);
			if (equippable != null && equippable.swappable()) {
				return equippable.swapWithEquipmentSlot(itemStack, player);
			} else if (itemStack.has(DataComponents.BLOCKS_ATTACKS)) {
				player.startUsingItem(interactionHand);
				return InteractionResult.CONSUME;
			} else {
				KineticWeapon kineticWeapon = itemStack.get(DataComponents.KINETIC_WEAPON);
				if (kineticWeapon != null) {
					player.startUsingItem(interactionHand);
					kineticWeapon.makeSound(player);
					return InteractionResult.CONSUME;
				} else {
					return InteractionResult.PASS;
				}
			}
		}
	}

	public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity livingEntity) {
		Consumable consumable = itemStack.get(DataComponents.CONSUMABLE);
		return consumable != null ? consumable.onConsume(level, livingEntity, itemStack) : itemStack;
	}

	public boolean isBarVisible(ItemStack itemStack) {
		return itemStack.isDamaged();
	}

	public int getBarWidth(ItemStack itemStack) {
		return Mth.clamp(Math.round(13.0F - itemStack.getDamageValue() * 13.0F / itemStack.getMaxDamage()), 0, 13);
	}

	public int getBarColor(ItemStack itemStack) {
		int i = itemStack.getMaxDamage();
		float f = Math.max(0.0F, ((float)i - itemStack.getDamageValue()) / i);
		return Mth.hsvToRgb(f / 3.0F, 1.0F, 1.0F);
	}

	public boolean overrideStackedOnOther(ItemStack itemStack, Slot slot, ClickAction clickAction, Player player) {
		return false;
	}

	public boolean overrideOtherStackedOnMe(ItemStack itemStack, ItemStack itemStack2, Slot slot, ClickAction clickAction, Player player, SlotAccess slotAccess) {
		return false;
	}

	public float getAttackDamageBonus(Entity entity, float f, DamageSource damageSource) {
		return 0.0F;
	}

	@Deprecated
	public @Nullable DamageSource getItemDamageSource(LivingEntity livingEntity) {
		return null;
	}

	public void hurtEnemy(ItemStack itemStack, LivingEntity livingEntity, LivingEntity livingEntity2) {
	}

	public void postHurtEnemy(ItemStack itemStack, LivingEntity livingEntity, LivingEntity livingEntity2) {
	}

	public boolean mineBlock(ItemStack itemStack, Level level, BlockState blockState, BlockPos blockPos, LivingEntity livingEntity) {
		Tool tool = itemStack.get(DataComponents.TOOL);
		if (tool == null) {
			return false;
		}

		if (!level.isClientSide() && blockState.getDestroySpeed(level, blockPos) != 0.0F && tool.damagePerBlock() > 0) {
			itemStack.hurtAndBreak(tool.damagePerBlock(), livingEntity, EquipmentSlot.MAINHAND);
		}

		return true;
	}

	public boolean isCorrectToolForDrops(ItemStack itemStack, BlockState blockState) {
		Tool tool = itemStack.get(DataComponents.TOOL);
		return tool != null && tool.isCorrectForDrops(blockState);
	}

	public InteractionResult interactLivingEntity(ItemStack itemStack, Player player, LivingEntity livingEntity, InteractionHand interactionHand) {
		return InteractionResult.PASS;
	}

	@Override
	public String toString() {
		return BuiltInRegistries.ITEM.wrapAsHolder(this).getRegisteredName();
	}

	public final ItemStack getCraftingRemainder() {
		return this.craftingRemainingItem == null ? ItemStack.EMPTY : new ItemStack(this.craftingRemainingItem);
	}

	public void inventoryTick(ItemStack itemStack, ServerLevel serverLevel, Entity entity, @Nullable EquipmentSlot equipmentSlot) {
	}

	public void onCraftedBy(ItemStack itemStack, Player player) {
		this.onCraftedPostProcess(itemStack, player.level());
	}

	public void onCraftedPostProcess(ItemStack itemStack, Level level) {
	}

	public ItemUseAnimation getUseAnimation(ItemStack itemStack) {
		Consumable consumable = itemStack.get(DataComponents.CONSUMABLE);
		if (consumable != null) {
			return consumable.animation();
		} else if (itemStack.has(DataComponents.BLOCKS_ATTACKS)) {
			return ItemUseAnimation.BLOCK;
		} else {
			return itemStack.has(DataComponents.KINETIC_WEAPON) ? ItemUseAnimation.SPEAR : ItemUseAnimation.NONE;
		}
	}

	public int getUseDuration(ItemStack itemStack, LivingEntity livingEntity) {
		Consumable consumable = itemStack.get(DataComponents.CONSUMABLE);
		if (consumable != null) {
			return consumable.consumeTicks();
		} else {
			return !itemStack.has(DataComponents.BLOCKS_ATTACKS) && !itemStack.has(DataComponents.KINETIC_WEAPON) ? 0 : 72000;
		}
	}

	public boolean releaseUsing(ItemStack itemStack, Level level, LivingEntity livingEntity, int i) {
		return false;
	}

	@Deprecated
	public void appendHoverText(
		ItemStack itemStack, Item.TooltipContext tooltipContext, TooltipDisplay tooltipDisplay, Consumer<Component> consumer, TooltipFlag tooltipFlag
	) {
	}

	public Optional<TooltipComponent> getTooltipImage(ItemStack itemStack) {
		return Optional.empty();
	}

	@VisibleForTesting
	public final String getDescriptionId() {
		return this.descriptionId;
	}

	public final Component getName() {
		return this.components.getOrDefault(DataComponents.ITEM_NAME, CommonComponents.EMPTY);
	}

	public Component getName(ItemStack itemStack) {
		return itemStack.getComponents().getOrDefault(DataComponents.ITEM_NAME, CommonComponents.EMPTY);
	}

	public boolean isFoil(ItemStack itemStack) {
		return itemStack.isEnchanted();
	}

	protected static BlockHitResult getPlayerPOVHitResult(Level level, Player player, ClipContext.Fluid fluid) {
		Vec3 vec3 = player.getEyePosition();
		Vec3 vec32 = vec3.add(player.calculateViewVector(player.getXRot(), player.getYRot()).scale(player.blockInteractionRange()));
		return level.clip(new ClipContext(vec3, vec32, ClipContext.Block.OUTLINE, fluid, player));
	}

	public boolean useOnRelease(ItemStack itemStack) {
		return false;
	}

	public ItemStack getDefaultInstance() {
		return new ItemStack(this);
	}

	public boolean canFitInsideContainerItems() {
		return true;
	}

	@Override
	public FeatureFlagSet requiredFeatures() {
		return this.requiredFeatures;
	}

	public boolean shouldPrintOpWarning(ItemStack itemStack, @Nullable Player player) {
		return false;
	}

	/**
	 * <p>Interface {@link net.fabricmc.fabric.api.item.v1.FabricItem.Settings} injected by mod fabric-item-api-v1</p>
	 */
	public static class Properties implements Settings {
		private static final DependantName<Item, String> BLOCK_DESCRIPTION_ID = resourceKey -> Util.makeDescriptionId("block", resourceKey.identifier());
		private static final DependantName<Item, String> ITEM_DESCRIPTION_ID = resourceKey -> Util.makeDescriptionId("item", resourceKey.identifier());
		private final DataComponentMap.Builder components = DataComponentMap.builder().addAll(DataComponents.COMMON_ITEM_COMPONENTS);
		@Nullable Item craftingRemainingItem;
		FeatureFlagSet requiredFeatures = FeatureFlags.VANILLA_SET;
		private @Nullable ResourceKey<Item> id;
		private DependantName<Item, String> descriptionId = ITEM_DESCRIPTION_ID;
		private final DependantName<Item, Identifier> model = ResourceKey::identifier;

		public Item.Properties food(FoodProperties foodProperties) {
			return this.food(foodProperties, Consumables.DEFAULT_FOOD);
		}

		public Item.Properties food(FoodProperties foodProperties, Consumable consumable) {
			return this.component(DataComponents.FOOD, foodProperties).component(DataComponents.CONSUMABLE, consumable);
		}

		public Item.Properties usingConvertsTo(Item item) {
			return this.component(DataComponents.USE_REMAINDER, new UseRemainder(new ItemStack(item)));
		}

		public Item.Properties useCooldown(float f) {
			return this.component(DataComponents.USE_COOLDOWN, new UseCooldown(f));
		}

		public Item.Properties stacksTo(int i) {
			return this.component(DataComponents.MAX_STACK_SIZE, i);
		}

		public Item.Properties durability(int i) {
			this.component(DataComponents.MAX_DAMAGE, i);
			this.component(DataComponents.MAX_STACK_SIZE, 1);
			this.component(DataComponents.DAMAGE, 0);
			return this;
		}

		public Item.Properties craftRemainder(Item item) {
			this.craftingRemainingItem = item;
			return this;
		}

		public Item.Properties rarity(Rarity rarity) {
			return this.component(DataComponents.RARITY, rarity);
		}

		public Item.Properties fireResistant() {
			return this.component(DataComponents.DAMAGE_RESISTANT, new DamageResistant(DamageTypeTags.IS_FIRE));
		}

		public Item.Properties jukeboxPlayable(ResourceKey<JukeboxSong> resourceKey) {
			return this.component(DataComponents.JUKEBOX_PLAYABLE, new JukeboxPlayable(new EitherHolder<>(resourceKey)));
		}

		public Item.Properties enchantable(int i) {
			return this.component(DataComponents.ENCHANTABLE, new Enchantable(i));
		}

		public Item.Properties repairable(Item item) {
			return this.component(DataComponents.REPAIRABLE, new Repairable(HolderSet.direct(item.builtInRegistryHolder())));
		}

		public Item.Properties repairable(TagKey<Item> tagKey) {
			HolderGetter<Item> holderGetter = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.ITEM);
			return this.component(DataComponents.REPAIRABLE, new Repairable(holderGetter.getOrThrow(tagKey)));
		}

		public Item.Properties equippable(EquipmentSlot equipmentSlot) {
			return this.component(DataComponents.EQUIPPABLE, Equippable.builder(equipmentSlot).build());
		}

		public Item.Properties equippableUnswappable(EquipmentSlot equipmentSlot) {
			return this.component(DataComponents.EQUIPPABLE, Equippable.builder(equipmentSlot).setSwappable(false).build());
		}

		public Item.Properties tool(ToolMaterial toolMaterial, TagKey<Block> tagKey, float f, float g, float h) {
			return toolMaterial.applyToolProperties(this, tagKey, f, g, h);
		}

		public Item.Properties pickaxe(ToolMaterial toolMaterial, float f, float g) {
			return this.tool(toolMaterial, BlockTags.MINEABLE_WITH_PICKAXE, f, g, 0.0F);
		}

		public Item.Properties axe(ToolMaterial toolMaterial, float f, float g) {
			return this.tool(toolMaterial, BlockTags.MINEABLE_WITH_AXE, f, g, 5.0F);
		}

		public Item.Properties hoe(ToolMaterial toolMaterial, float f, float g) {
			return this.tool(toolMaterial, BlockTags.MINEABLE_WITH_HOE, f, g, 0.0F);
		}

		public Item.Properties shovel(ToolMaterial toolMaterial, float f, float g) {
			return this.tool(toolMaterial, BlockTags.MINEABLE_WITH_SHOVEL, f, g, 0.0F);
		}

		public Item.Properties sword(ToolMaterial toolMaterial, float f, float g) {
			return toolMaterial.applySwordProperties(this, f, g);
		}

		public Item.Properties spear(ToolMaterial toolMaterial, float f, float g, float h, float i, float j, float k, float l, float m, float n) {
			return this.durability(toolMaterial.durability())
				.repairable(toolMaterial.repairItems())
				.enchantable(toolMaterial.enchantmentValue())
				.component(DataComponents.DAMAGE_TYPE, new EitherHolder<>(DamageTypes.SPEAR))
				.component(
					DataComponents.KINETIC_WEAPON,
					new KineticWeapon(
						10,
						(int)(h * 20.0F),
						KineticWeapon.Condition.ofAttackerSpeed((int)(i * 20.0F), j),
						KineticWeapon.Condition.ofAttackerSpeed((int)(k * 20.0F), l),
						KineticWeapon.Condition.ofRelativeSpeed((int)(m * 20.0F), n),
						0.38F,
						g,
						Optional.of(toolMaterial == ToolMaterial.WOOD ? SoundEvents.SPEAR_WOOD_USE : SoundEvents.SPEAR_USE),
						Optional.of(toolMaterial == ToolMaterial.WOOD ? SoundEvents.SPEAR_WOOD_HIT : SoundEvents.SPEAR_HIT)
					)
				)
				.component(
					DataComponents.PIERCING_WEAPON,
					new PiercingWeapon(
						true,
						false,
						Optional.of(toolMaterial == ToolMaterial.WOOD ? SoundEvents.SPEAR_WOOD_ATTACK : SoundEvents.SPEAR_ATTACK),
						Optional.of(toolMaterial == ToolMaterial.WOOD ? SoundEvents.SPEAR_WOOD_HIT : SoundEvents.SPEAR_HIT)
					)
				)
				.component(DataComponents.ATTACK_RANGE, new AttackRange(2.0F, 4.5F, 2.0F, 6.5F, 0.125F, 0.5F))
				.component(DataComponents.MINIMUM_ATTACK_CHARGE, 1.0F)
				.component(DataComponents.SWING_ANIMATION, new SwingAnimation(SwingAnimationType.STAB, (int)(f * 20.0F)))
				.attributes(
					ItemAttributeModifiers.builder()
						.add(
							Attributes.ATTACK_DAMAGE,
							new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 0.0F + toolMaterial.attackDamageBonus(), AttributeModifier.Operation.ADD_VALUE),
							EquipmentSlotGroup.MAINHAND
						)
						.add(
							Attributes.ATTACK_SPEED,
							new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, 1.0F / f - 4.0, AttributeModifier.Operation.ADD_VALUE),
							EquipmentSlotGroup.MAINHAND
						)
						.build()
				)
				.component(DataComponents.USE_EFFECTS, new UseEffects(true, false, 1.0F))
				.component(DataComponents.WEAPON, new Weapon(1));
		}

		public Item.Properties spawnEgg(EntityType<?> entityType) {
			return this.component(DataComponents.ENTITY_DATA, TypedEntityData.of(entityType, new CompoundTag()));
		}

		public Item.Properties humanoidArmor(ArmorMaterial armorMaterial, ArmorType armorType) {
			return this.durability(armorType.getDurability(armorMaterial.durability()))
				.attributes(armorMaterial.createAttributes(armorType))
				.enchantable(armorMaterial.enchantmentValue())
				.component(
					DataComponents.EQUIPPABLE, Equippable.builder(armorType.getSlot()).setEquipSound(armorMaterial.equipSound()).setAsset(armorMaterial.assetId()).build()
				)
				.repairable(armorMaterial.repairIngredient());
		}

		public Item.Properties wolfArmor(ArmorMaterial armorMaterial) {
			return this.durability(ArmorType.BODY.getDurability(armorMaterial.durability()))
				.attributes(armorMaterial.createAttributes(ArmorType.BODY))
				.repairable(armorMaterial.repairIngredient())
				.component(
					DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.BODY)
						.setEquipSound(armorMaterial.equipSound())
						.setAsset(armorMaterial.assetId())
						.setAllowedEntities(HolderSet.direct(EntityType.WOLF.builtInRegistryHolder()))
						.setCanBeSheared(true)
						.setShearingSound(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.ARMOR_UNEQUIP_WOLF))
						.build()
				)
				.component(DataComponents.BREAK_SOUND, SoundEvents.WOLF_ARMOR_BREAK)
				.stacksTo(1);
		}

		public Item.Properties horseArmor(ArmorMaterial armorMaterial) {
			HolderGetter<EntityType<?>> holderGetter = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.ENTITY_TYPE);
			return this.attributes(armorMaterial.createAttributes(ArmorType.BODY))
				.component(
					DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.BODY)
						.setEquipSound(SoundEvents.HORSE_ARMOR)
						.setAsset(armorMaterial.assetId())
						.setAllowedEntities(holderGetter.getOrThrow(EntityTypeTags.CAN_WEAR_HORSE_ARMOR))
						.setDamageOnHurt(false)
						.setCanBeSheared(true)
						.setShearingSound(SoundEvents.HORSE_ARMOR_UNEQUIP)
						.build()
				)
				.stacksTo(1);
		}

		public Item.Properties nautilusArmor(ArmorMaterial armorMaterial) {
			HolderGetter<EntityType<?>> holderGetter = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.ENTITY_TYPE);
			return this.attributes(armorMaterial.createAttributes(ArmorType.BODY))
				.component(
					DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.BODY)
						.setEquipSound(SoundEvents.ARMOR_EQUIP_NAUTILUS)
						.setAsset(armorMaterial.assetId())
						.setAllowedEntities(holderGetter.getOrThrow(EntityTypeTags.CAN_WEAR_NAUTILUS_ARMOR))
						.setDamageOnHurt(false)
						.setEquipOnInteract(true)
						.setCanBeSheared(true)
						.setShearingSound(SoundEvents.ARMOR_UNEQUIP_NAUTILUS)
						.build()
				)
				.stacksTo(1);
		}

		public Item.Properties trimMaterial(ResourceKey<TrimMaterial> resourceKey) {
			return this.component(DataComponents.PROVIDES_TRIM_MATERIAL, new ProvidesTrimMaterial(resourceKey));
		}

		public Item.Properties requiredFeatures(FeatureFlag... featureFlags) {
			this.requiredFeatures = FeatureFlags.REGISTRY.subset(featureFlags);
			return this;
		}

		public Item.Properties setId(ResourceKey<Item> resourceKey) {
			this.id = resourceKey;
			return this;
		}

		public Item.Properties overrideDescription(String string) {
			this.descriptionId = DependantName.fixed(string);
			return this;
		}

		public Item.Properties useBlockDescriptionPrefix() {
			this.descriptionId = BLOCK_DESCRIPTION_ID;
			return this;
		}

		public Item.Properties useItemDescriptionPrefix() {
			this.descriptionId = ITEM_DESCRIPTION_ID;
			return this;
		}

		protected String effectiveDescriptionId() {
			return this.descriptionId.get(Objects.requireNonNull(this.id, "Item id not set"));
		}

		public Identifier effectiveModel() {
			return this.model.get(Objects.requireNonNull(this.id, "Item id not set"));
		}

		public <T> Item.Properties component(DataComponentType<T> dataComponentType, T object) {
			this.components.set(dataComponentType, object);
			return this;
		}

		public Item.Properties attributes(ItemAttributeModifiers itemAttributeModifiers) {
			return this.component(DataComponents.ATTRIBUTE_MODIFIERS, itemAttributeModifiers);
		}

		DataComponentMap buildAndValidateComponents(Component component, Identifier identifier) {
			DataComponentMap dataComponentMap = this.components.set(DataComponents.ITEM_NAME, component).set(DataComponents.ITEM_MODEL, identifier).build();
			if (dataComponentMap.has(DataComponents.DAMAGE) && dataComponentMap.getOrDefault(DataComponents.MAX_STACK_SIZE, 1) > 1) {
				throw new IllegalStateException("Item cannot have both durability and be stackable");
			} else {
				return dataComponentMap;
			}
		}
	}

	public interface TooltipContext {
		Item.TooltipContext EMPTY = new Item.TooltipContext() {
			@Override
			public HolderLookup.@Nullable Provider registries() {
				return null;
			}

			@Override
			public float tickRate() {
				return 20.0F;
			}

			@Override
			public @Nullable MapItemSavedData mapData(MapId mapId) {
				return null;
			}

			@Override
			public boolean isPeaceful() {
				return false;
			}
		};

		HolderLookup.@Nullable Provider registries();

		float tickRate();

		@Nullable MapItemSavedData mapData(MapId mapId);

		boolean isPeaceful();

		static Item.TooltipContext of(@Nullable Level level) {
			return level == null ? EMPTY : new Item.TooltipContext() {
				@Override
				public HolderLookup.Provider registries() {
					return level.registryAccess();
				}

				@Override
				public float tickRate() {
					return level.tickRateManager().tickrate();
				}

				@Override
				public MapItemSavedData mapData(MapId mapId) {
					return level.getMapData(mapId);
				}

				@Override
				public boolean isPeaceful() {
					return level.getDifficulty() == Difficulty.PEACEFUL;
				}
			};
		}

		static Item.TooltipContext of(HolderLookup.Provider provider) {
			return new Item.TooltipContext() {
				@Override
				public HolderLookup.Provider registries() {
					return provider;
				}

				@Override
				public float tickRate() {
					return 20.0F;
				}

				@Override
				public @Nullable MapItemSavedData mapData(MapId mapId) {
					return null;
				}

				@Override
				public boolean isPeaceful() {
					return false;
				}
			};
		}
	}
}
