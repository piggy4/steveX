package net.minecraft.world.item;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.item.v1.FabricItemStack;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.NullOps;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.gameevent.GameEvent;
import org.apache.commons.lang3.function.TriConsumer;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.item.v1.FabricItemStack} injected by mod fabric-item-api-v1</p>
 */
public final class ItemStack implements DataComponentHolder, FabricItemStack {
	private static final List<Component> OP_NBT_WARNING = List.of(
		Component.translatable("item.op_warning.line1").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
		Component.translatable("item.op_warning.line2").withStyle(ChatFormatting.RED),
		Component.translatable("item.op_warning.line3").withStyle(ChatFormatting.RED)
	);
	private static final Component UNBREAKABLE_TOOLTIP = Component.translatable("item.unbreakable").withStyle(ChatFormatting.BLUE);
	private static final Component INTANGIBLE_TOOLTIP = Component.translatable("item.intangible").withStyle(ChatFormatting.GRAY);
	public static final MapCodec<ItemStack> MAP_CODEC = MapCodec.recursive(
		"ItemStack",
		codec -> RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Item.CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder),
					ExtraCodecs.intRange(1, 99).fieldOf("count").orElse(1).forGetter(ItemStack::getCount),
					DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(itemStack -> itemStack.components.asPatch())
				)
				.apply(instance, ItemStack::new)
		)
	);
	public static final Codec<ItemStack> CODEC = Codec.lazyInitialized(MAP_CODEC::codec);
	public static final Codec<ItemStack> SINGLE_ITEM_CODEC = Codec.lazyInitialized(
		() -> RecordCodecBuilder.create(
			instance -> instance.group(
					Item.CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder),
					DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(itemStack -> itemStack.components.asPatch())
				)
				.apply(instance, (holder, dataComponentPatch) -> new ItemStack(holder, 1, dataComponentPatch))
		)
	);
	public static final Codec<ItemStack> STRICT_CODEC = CODEC.validate(ItemStack::validateStrict);
	public static final Codec<ItemStack> STRICT_SINGLE_ITEM_CODEC = SINGLE_ITEM_CODEC.validate(ItemStack::validateStrict);
	public static final Codec<ItemStack> OPTIONAL_CODEC = ExtraCodecs.optionalEmptyMap(CODEC)
		.xmap(optional -> optional.orElse(ItemStack.EMPTY), itemStack -> itemStack.isEmpty() ? Optional.empty() : Optional.of(itemStack));
	public static final Codec<ItemStack> SIMPLE_ITEM_CODEC = Item.CODEC.xmap(ItemStack::new, ItemStack::getItemHolder);
	public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> OPTIONAL_STREAM_CODEC = createOptionalStreamCodec(DataComponentPatch.STREAM_CODEC);
	public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> OPTIONAL_UNTRUSTED_STREAM_CODEC = createOptionalStreamCodec(
		DataComponentPatch.DELIMITED_STREAM_CODEC
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> STREAM_CODEC = new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
		public ItemStack decode(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
			ItemStack itemStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(registryFriendlyByteBuf);
			if (itemStack.isEmpty()) {
				throw new DecoderException("Empty ItemStack not allowed");
			} else {
				return itemStack;
			}
		}

		public void encode(RegistryFriendlyByteBuf registryFriendlyByteBuf, ItemStack itemStack) {
			if (itemStack.isEmpty()) {
				throw new EncoderException("Empty ItemStack not allowed");
			}

			ItemStack.OPTIONAL_STREAM_CODEC.encode(registryFriendlyByteBuf, itemStack);
		}
	};
	public static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> OPTIONAL_LIST_STREAM_CODEC = OPTIONAL_STREAM_CODEC.apply(
		ByteBufCodecs.collection(NonNullList::createWithCapacity)
	);
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final ItemStack EMPTY = new ItemStack((Void)null);
	private static final Component DISABLED_ITEM_TOOLTIP = Component.translatable("item.disabled").withStyle(ChatFormatting.RED);
	private int count;
	private int popTime;
	@Deprecated
	private final @Nullable Item item;
	final PatchedDataComponentMap components;
	private @Nullable Entity entityRepresentation;

	public static DataResult<ItemStack> validateStrict(ItemStack itemStack) {
		DataResult<Unit> dataResult = validateComponents(itemStack.getComponents());
		if (dataResult.isError()) {
			return dataResult.map(unit -> itemStack);
		} else {
			return itemStack.getCount() > itemStack.getMaxStackSize()
				? DataResult.error(() -> "Item stack with stack size of " + itemStack.getCount() + " was larger than maximum: " + itemStack.getMaxStackSize())
				: DataResult.success(itemStack);
		}
	}

	private static StreamCodec<RegistryFriendlyByteBuf, ItemStack> createOptionalStreamCodec(StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> streamCodec) {
		return new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
			public ItemStack decode(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
				int i = registryFriendlyByteBuf.readVarInt();
				if (i <= 0) {
					return ItemStack.EMPTY;
				}

				Holder<Item> holder = Item.STREAM_CODEC.decode(registryFriendlyByteBuf);
				DataComponentPatch dataComponentPatch = streamCodec.decode(registryFriendlyByteBuf);
				return new ItemStack(holder, i, dataComponentPatch);
			}

			public void encode(RegistryFriendlyByteBuf registryFriendlyByteBuf, ItemStack itemStack) {
				if (itemStack.isEmpty()) {
					registryFriendlyByteBuf.writeVarInt(0);
				} else {
					registryFriendlyByteBuf.writeVarInt(itemStack.getCount());
					Item.STREAM_CODEC.encode(registryFriendlyByteBuf, itemStack.getItemHolder());
					streamCodec.encode(registryFriendlyByteBuf, itemStack.components.asPatch());
				}
			}
		};
	}

	public static StreamCodec<RegistryFriendlyByteBuf, ItemStack> validatedStreamCodec(StreamCodec<RegistryFriendlyByteBuf, ItemStack> streamCodec) {
		return new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
			public ItemStack decode(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
				ItemStack itemStack = streamCodec.decode(registryFriendlyByteBuf);
				if (!itemStack.isEmpty()) {
					RegistryOps<Unit> registryOps = registryFriendlyByteBuf.registryAccess().createSerializationContext(NullOps.INSTANCE);
					ItemStack.CODEC.encodeStart(registryOps, itemStack).getOrThrow(DecoderException::new);
				}

				return itemStack;
			}

			public void encode(RegistryFriendlyByteBuf registryFriendlyByteBuf, ItemStack itemStack) {
				streamCodec.encode(registryFriendlyByteBuf, itemStack);
			}
		};
	}

	public Optional<TooltipComponent> getTooltipImage() {
		return this.getItem().getTooltipImage(this);
	}

	@Override
	public DataComponentMap getComponents() {
		return !this.isEmpty() ? this.components : DataComponentMap.EMPTY;
	}

	public DataComponentMap getPrototype() {
		return !this.isEmpty() ? this.getItem().components() : DataComponentMap.EMPTY;
	}

	public DataComponentPatch getComponentsPatch() {
		return !this.isEmpty() ? this.components.asPatch() : DataComponentPatch.EMPTY;
	}

	public DataComponentMap immutableComponents() {
		return !this.isEmpty() ? this.components.toImmutableMap() : DataComponentMap.EMPTY;
	}

	public boolean hasNonDefault(DataComponentType<?> dataComponentType) {
		return !this.isEmpty() && this.components.hasNonDefault(dataComponentType);
	}

	public ItemStack(ItemLike itemLike) {
		this(itemLike, 1);
	}

	public ItemStack(Holder<Item> holder) {
		this(holder.value(), 1);
	}

	public ItemStack(Holder<Item> holder, int i, DataComponentPatch dataComponentPatch) {
		this(holder.value(), i, PatchedDataComponentMap.fromPatch(holder.value().components(), dataComponentPatch));
	}

	public ItemStack(Holder<Item> holder, int i) {
		this(holder.value(), i);
	}

	public ItemStack(ItemLike itemLike, int i) {
		this(itemLike, i, new PatchedDataComponentMap(itemLike.asItem().components()));
	}

	private ItemStack(ItemLike itemLike, int i, PatchedDataComponentMap patchedDataComponentMap) {
		this.item = itemLike.asItem();
		this.count = i;
		this.components = patchedDataComponentMap;
	}

	private ItemStack(@Nullable Void void_) {
		this.item = null;
		this.components = new PatchedDataComponentMap(DataComponentMap.EMPTY);
	}

	public static DataResult<Unit> validateComponents(DataComponentMap dataComponentMap) {
		if (dataComponentMap.has(DataComponents.MAX_DAMAGE) && dataComponentMap.getOrDefault(DataComponents.MAX_STACK_SIZE, 1) > 1) {
			return DataResult.error(() -> "Item cannot be both damageable and stackable");
		}

		ItemContainerContents itemContainerContents = dataComponentMap.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);

		for (ItemStack itemStack : itemContainerContents.nonEmptyItems()) {
			int i = itemStack.getCount();
			int j = itemStack.getMaxStackSize();
			if (i > j) {
				return DataResult.error(() -> "Item stack with count of " + i + " was larger than maximum: " + j);
			}
		}

		return DataResult.success(Unit.INSTANCE);
	}

	public boolean isEmpty() {
		return this == EMPTY || this.item == Items.AIR || this.count <= 0;
	}

	public boolean isItemEnabled(FeatureFlagSet featureFlagSet) {
		return this.isEmpty() || this.getItem().isEnabled(featureFlagSet);
	}

	public ItemStack split(int i) {
		int j = Math.min(i, this.getCount());
		ItemStack itemStack = this.copyWithCount(j);
		this.shrink(j);
		return itemStack;
	}

	public ItemStack copyAndClear() {
		if (this.isEmpty()) {
			return EMPTY;
		}

		ItemStack itemStack = this.copy();
		this.setCount(0);
		return itemStack;
	}

	public Item getItem() {
		return this.isEmpty() ? Items.AIR : this.item;
	}

	public Holder<Item> getItemHolder() {
		return this.getItem().builtInRegistryHolder();
	}

	public boolean is(TagKey<Item> tagKey) {
		return this.getItem().builtInRegistryHolder().is(tagKey);
	}

	public boolean is(Item item) {
		return this.getItem() == item;
	}

	public boolean is(Predicate<Holder<Item>> predicate) {
		return predicate.test(this.getItem().builtInRegistryHolder());
	}

	public boolean is(Holder<Item> holder) {
		return this.getItem().builtInRegistryHolder() == holder;
	}

	public boolean is(HolderSet<Item> holderSet) {
		return holderSet.contains(this.getItemHolder());
	}

	public Stream<TagKey<Item>> getTags() {
		return this.getItem().builtInRegistryHolder().tags();
	}

	public InteractionResult useOn(UseOnContext useOnContext) {
		Player player = useOnContext.getPlayer();
		BlockPos blockPos = useOnContext.getClickedPos();
		if (player != null && !player.getAbilities().mayBuild && !this.canPlaceOnBlockInAdventureMode(new BlockInWorld(useOnContext.getLevel(), blockPos, false))) {
			return InteractionResult.PASS;
		}

		Item item = this.getItem();
		InteractionResult interactionResult = item.useOn(useOnContext);
		if (player != null && interactionResult instanceof InteractionResult.Success success && success.wasItemInteraction()) {
			player.awardStat(Stats.ITEM_USED.get(item));
		}

		return interactionResult;
	}

	public float getDestroySpeed(BlockState blockState) {
		return this.getItem().getDestroySpeed(this, blockState);
	}

	public InteractionResult use(Level level, Player player, InteractionHand interactionHand) {
		ItemStack itemStack = this.copy();
		boolean bl = this.getUseDuration(player) <= 0;
		InteractionResult interactionResult = this.getItem().use(level, player, interactionHand);
		return bl && interactionResult instanceof InteractionResult.Success success
			? success.heldItemTransformedTo(
				success.heldItemTransformedTo() == null
					? this.applyAfterUseComponentSideEffects(player, itemStack)
					: success.heldItemTransformedTo().applyAfterUseComponentSideEffects(player, itemStack)
			)
			: interactionResult;
	}

	public ItemStack finishUsingItem(Level level, LivingEntity livingEntity) {
		ItemStack itemStack = this.copy();
		ItemStack itemStack2 = this.getItem().finishUsingItem(this, level, livingEntity);
		return itemStack2.applyAfterUseComponentSideEffects(livingEntity, itemStack);
	}

	private ItemStack applyAfterUseComponentSideEffects(LivingEntity livingEntity, ItemStack itemStack) {
		UseRemainder useRemainder = itemStack.get(DataComponents.USE_REMAINDER);
		UseCooldown useCooldown = itemStack.get(DataComponents.USE_COOLDOWN);
		int i = itemStack.getCount();
		ItemStack itemStack2 = this;
		if (useRemainder != null) {
			itemStack2 = useRemainder.convertIntoRemainder(itemStack2, i, livingEntity.hasInfiniteMaterials(), livingEntity::handleExtraItemsCreatedOnUse);
		}

		if (useCooldown != null) {
			useCooldown.apply(itemStack, livingEntity);
		}

		return itemStack2;
	}

	public int getMaxStackSize() {
		return this.getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
	}

	public boolean isStackable() {
		return this.getMaxStackSize() > 1 && (!this.isDamageableItem() || !this.isDamaged());
	}

	public boolean isDamageableItem() {
		return this.has(DataComponents.MAX_DAMAGE) && !this.has(DataComponents.UNBREAKABLE) && this.has(DataComponents.DAMAGE);
	}

	public boolean isDamaged() {
		return this.isDamageableItem() && this.getDamageValue() > 0;
	}

	public int getDamageValue() {
		return Mth.clamp(this.getOrDefault(DataComponents.DAMAGE, 0), 0, this.getMaxDamage());
	}

	public void setDamageValue(int i) {
		this.set(DataComponents.DAMAGE, Mth.clamp(i, 0, this.getMaxDamage()));
	}

	public int getMaxDamage() {
		return this.getOrDefault(DataComponents.MAX_DAMAGE, 0);
	}

	public boolean isBroken() {
		return this.isDamageableItem() && this.getDamageValue() >= this.getMaxDamage();
	}

	public boolean nextDamageWillBreak() {
		return this.isDamageableItem() && this.getDamageValue() >= this.getMaxDamage() - 1;
	}

	public void hurtAndBreak(int i, ServerLevel serverLevel, @Nullable ServerPlayer serverPlayer, Consumer<Item> consumer) {
		int j = this.processDurabilityChange(i, serverLevel, serverPlayer);
		if (j != 0) {
			this.applyDamage(this.getDamageValue() + j, serverPlayer, consumer);
		}
	}

	private int processDurabilityChange(int i, ServerLevel serverLevel, @Nullable ServerPlayer serverPlayer) {
		if (!this.isDamageableItem()) {
			return 0;
		} else if (serverPlayer != null && serverPlayer.hasInfiniteMaterials()) {
			return 0;
		} else {
			return i > 0 ? EnchantmentHelper.processDurabilityChange(serverLevel, this, i) : i;
		}
	}

	private void applyDamage(int i, @Nullable ServerPlayer serverPlayer, Consumer<Item> consumer) {
		if (serverPlayer != null) {
			CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(serverPlayer, this, i);
		}

		this.setDamageValue(i);
		if (this.isBroken()) {
			Item item = this.getItem();
			this.shrink(1);
			consumer.accept(item);
		}
	}

	public void hurtWithoutBreaking(int i, Player player) {
		if (player instanceof ServerPlayer serverPlayer) {
			int j = this.processDurabilityChange(i, serverPlayer.level(), serverPlayer);
			if (j == 0) {
				return;
			}

			int k = Math.min(this.getDamageValue() + j, this.getMaxDamage() - 1);
			this.applyDamage(k, serverPlayer, item -> {});
		}
	}

	public void hurtAndBreak(int i, LivingEntity livingEntity, InteractionHand interactionHand) {
		this.hurtAndBreak(i, livingEntity, interactionHand.asEquipmentSlot());
	}

	public void hurtAndBreak(int i, LivingEntity livingEntity, EquipmentSlot equipmentSlot) {
		if (livingEntity.level() instanceof ServerLevel serverLevel) {
			this.hurtAndBreak(
				i, serverLevel, livingEntity instanceof ServerPlayer serverPlayer ? serverPlayer : null, item -> livingEntity.onEquippedItemBroken(item, equipmentSlot)
			);
		}
	}

	public ItemStack hurtAndConvertOnBreak(int i, ItemLike itemLike, LivingEntity livingEntity, EquipmentSlot equipmentSlot) {
		this.hurtAndBreak(i, livingEntity, equipmentSlot);
		if (this.isEmpty()) {
			ItemStack itemStack = this.transmuteCopyIgnoreEmpty(itemLike, 1);
			if (itemStack.isDamageableItem()) {
				itemStack.setDamageValue(0);
			}

			return itemStack;
		} else {
			return this;
		}
	}

	public boolean isBarVisible() {
		return this.getItem().isBarVisible(this);
	}

	public int getBarWidth() {
		return this.getItem().getBarWidth(this);
	}

	public int getBarColor() {
		return this.getItem().getBarColor(this);
	}

	public boolean overrideStackedOnOther(Slot slot, ClickAction clickAction, Player player) {
		return this.getItem().overrideStackedOnOther(this, slot, clickAction, player);
	}

	public boolean overrideOtherStackedOnMe(ItemStack itemStack, Slot slot, ClickAction clickAction, Player player, SlotAccess slotAccess) {
		return this.getItem().overrideOtherStackedOnMe(this, itemStack, slot, clickAction, player, slotAccess);
	}

	public boolean hurtEnemy(LivingEntity livingEntity, LivingEntity livingEntity2) {
		Item item = this.getItem();
		item.hurtEnemy(this, livingEntity, livingEntity2);
		if (this.has(DataComponents.WEAPON)) {
			if (livingEntity2 instanceof Player player) {
				player.awardStat(Stats.ITEM_USED.get(item));
			}

			return true;
		} else {
			return false;
		}
	}

	public void postHurtEnemy(LivingEntity livingEntity, LivingEntity livingEntity2) {
		this.getItem().postHurtEnemy(this, livingEntity, livingEntity2);
		Weapon weapon = this.get(DataComponents.WEAPON);
		if (weapon != null) {
			this.hurtAndBreak(weapon.itemDamagePerAttack(), livingEntity2, EquipmentSlot.MAINHAND);
		}
	}

	public void mineBlock(Level level, BlockState blockState, BlockPos blockPos, Player player) {
		Item item = this.getItem();
		if (item.mineBlock(this, level, blockState, blockPos, player)) {
			player.awardStat(Stats.ITEM_USED.get(item));
		}
	}

	public boolean isCorrectToolForDrops(BlockState blockState) {
		return this.getItem().isCorrectToolForDrops(this, blockState);
	}

	public InteractionResult interactLivingEntity(Player player, LivingEntity livingEntity, InteractionHand interactionHand) {
		Equippable equippable = this.get(DataComponents.EQUIPPABLE);
		if (equippable != null && equippable.equipOnInteract()) {
			InteractionResult interactionResult = equippable.equipOnTarget(player, livingEntity, this);
			if (interactionResult != InteractionResult.PASS) {
				return interactionResult;
			}
		}

		return this.getItem().interactLivingEntity(this, player, livingEntity, interactionHand);
	}

	public ItemStack copy() {
		if (this.isEmpty()) {
			return EMPTY;
		}

		ItemStack itemStack = new ItemStack(this.getItem(), this.count, this.components.copy());
		itemStack.setPopTime(this.getPopTime());
		return itemStack;
	}

	public ItemStack copyWithCount(int i) {
		if (this.isEmpty()) {
			return EMPTY;
		}

		ItemStack itemStack = this.copy();
		itemStack.setCount(i);
		return itemStack;
	}

	public ItemStack transmuteCopy(ItemLike itemLike) {
		return this.transmuteCopy(itemLike, this.getCount());
	}

	public ItemStack transmuteCopy(ItemLike itemLike, int i) {
		return this.isEmpty() ? EMPTY : this.transmuteCopyIgnoreEmpty(itemLike, i);
	}

	private ItemStack transmuteCopyIgnoreEmpty(ItemLike itemLike, int i) {
		return new ItemStack(itemLike.asItem().builtInRegistryHolder(), i, this.components.asPatch());
	}

	public static boolean matches(ItemStack itemStack, ItemStack itemStack2) {
		if (itemStack == itemStack2) {
			return true;
		} else {
			return itemStack.getCount() != itemStack2.getCount() ? false : isSameItemSameComponents(itemStack, itemStack2);
		}
	}

	@Deprecated
	public static boolean listMatches(List<ItemStack> list, List<ItemStack> list2) {
		if (list.size() != list2.size()) {
			return false;
		}

		for (int i = 0; i < list.size(); i++) {
			if (!matches(list.get(i), list2.get(i))) {
				return false;
			}
		}

		return true;
	}

	public static boolean isSameItem(ItemStack itemStack, ItemStack itemStack2) {
		return itemStack.is(itemStack2.getItem());
	}

	public static boolean isSameItemSameComponents(ItemStack itemStack, ItemStack itemStack2) {
		if (!itemStack.is(itemStack2.getItem())) {
			return false;
		} else {
			return itemStack.isEmpty() && itemStack2.isEmpty() ? true : Objects.equals(itemStack.components, itemStack2.components);
		}
	}

	public static boolean matchesIgnoringComponents(ItemStack itemStack, ItemStack itemStack2, Predicate<DataComponentType<?>> predicate) {
		if (itemStack == itemStack2) {
			return true;
		}

		if (itemStack.getCount() != itemStack2.getCount()) {
			return false;
		}

		if (!itemStack.is(itemStack2.getItem())) {
			return false;
		}

		if (itemStack.isEmpty() && itemStack2.isEmpty()) {
			return true;
		}

		if (itemStack.components.size() != itemStack2.components.size()) {
			return false;
		}

		for (DataComponentType<?> dataComponentType : itemStack.components.keySet()) {
			Object object = itemStack.components.get(dataComponentType);
			Object object2 = itemStack2.components.get(dataComponentType);
			if (object == null || object2 == null) {
				return false;
			}

			if (!Objects.equals(object, object2) && !predicate.test(dataComponentType)) {
				return false;
			}
		}

		return true;
	}

	public static MapCodec<ItemStack> lenientOptionalFieldOf(String string) {
		return CODEC.lenientOptionalFieldOf(string)
			.xmap(optional -> optional.orElse(EMPTY), itemStack -> itemStack.isEmpty() ? Optional.empty() : Optional.of(itemStack));
	}

	public static int hashItemAndComponents(@Nullable ItemStack itemStack) {
		if (itemStack != null) {
			int i = 31 + itemStack.getItem().hashCode();
			return 31 * i + itemStack.getComponents().hashCode();
		} else {
			return 0;
		}
	}

	@Deprecated
	public static int hashStackList(List<ItemStack> list) {
		int i = 0;

		for (ItemStack itemStack : list) {
			i = i * 31 + hashItemAndComponents(itemStack);
		}

		return i;
	}

	@Override
	public String toString() {
		return this.getCount() + " " + this.getItem();
	}

	public void inventoryTick(Level level, Entity entity, @Nullable EquipmentSlot equipmentSlot) {
		if (this.popTime > 0) {
			this.popTime--;
		}

		if (level instanceof ServerLevel serverLevel) {
			this.getItem().inventoryTick(this, serverLevel, entity, equipmentSlot);
		}
	}

	public void onCraftedBy(Player player, int i) {
		player.awardStat(Stats.ITEM_CRAFTED.get(this.getItem()), i);
		this.getItem().onCraftedBy(this, player);
	}

	public void onCraftedBySystem(Level level) {
		this.getItem().onCraftedPostProcess(this, level);
	}

	public int getUseDuration(LivingEntity livingEntity) {
		return this.getItem().getUseDuration(this, livingEntity);
	}

	public ItemUseAnimation getUseAnimation() {
		return this.getItem().getUseAnimation(this);
	}

	public void releaseUsing(Level level, LivingEntity livingEntity, int i) {
		ItemStack itemStack = this.copy();
		if (this.getItem().releaseUsing(this, level, livingEntity, i)) {
			ItemStack itemStack2 = this.applyAfterUseComponentSideEffects(livingEntity, itemStack);
			if (itemStack2 != this) {
				livingEntity.setItemInHand(livingEntity.getUsedItemHand(), itemStack2);
			}
		}
	}

	public void causeUseVibration(Entity entity, Holder.Reference<GameEvent> reference) {
		UseEffects useEffects = this.get(DataComponents.USE_EFFECTS);
		if (useEffects != null && useEffects.interactVibrations()) {
			entity.gameEvent(reference);
		}
	}

	public boolean useOnRelease() {
		return this.getItem().useOnRelease(this);
	}

	public <T> @Nullable T set(DataComponentType<T> dataComponentType, @Nullable T object) {
		return this.components.set(dataComponentType, object);
	}

	public <T> @Nullable T set(TypedDataComponent<T> typedDataComponent) {
		return this.components.set(typedDataComponent);
	}

	public <T> void copyFrom(DataComponentType<T> dataComponentType, DataComponentGetter dataComponentGetter) {
		this.set(dataComponentType, dataComponentGetter.get(dataComponentType));
	}

	public <T, U> @Nullable T update(DataComponentType<T> dataComponentType, T object, U object2, BiFunction<T, U, T> biFunction) {
		return this.set(dataComponentType, biFunction.apply(this.getOrDefault(dataComponentType, object), object2));
	}

	public <T> @Nullable T update(DataComponentType<T> dataComponentType, T object, UnaryOperator<T> unaryOperator) {
		T object2 = this.getOrDefault(dataComponentType, object);
		return this.set(dataComponentType, unaryOperator.apply(object2));
	}

	public <T> @Nullable T remove(DataComponentType<? extends T> dataComponentType) {
		return this.components.remove(dataComponentType);
	}

	public void applyComponentsAndValidate(DataComponentPatch dataComponentPatch) {
		DataComponentPatch dataComponentPatch2 = this.components.asPatch();
		this.components.applyPatch(dataComponentPatch);
		Optional<Error<ItemStack>> optional = validateStrict(this).error();
		if (optional.isPresent()) {
			LOGGER.error("Failed to apply component patch '{}' to item: '{}'", dataComponentPatch, optional.get().message());
			this.components.restorePatch(dataComponentPatch2);
		}
	}

	public void applyComponents(DataComponentPatch dataComponentPatch) {
		this.components.applyPatch(dataComponentPatch);
	}

	public void applyComponents(DataComponentMap dataComponentMap) {
		this.components.setAll(dataComponentMap);
	}

	public Component getHoverName() {
		Component component = this.getCustomName();
		return component != null ? component : this.getItemName();
	}

	public @Nullable Component getCustomName() {
		Component component = this.get(DataComponents.CUSTOM_NAME);
		if (component != null) {
			return component;
		}

		WrittenBookContent writtenBookContent = this.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (writtenBookContent != null) {
			String string = writtenBookContent.title().raw();
			if (!StringUtil.isBlank(string)) {
				return Component.literal(string);
			}
		}

		return null;
	}

	public Component getItemName() {
		return this.getItem().getName(this);
	}

	public Component getStyledHoverName() {
		MutableComponent mutableComponent = Component.empty().append(this.getHoverName()).withStyle(this.getRarity().color());
		if (this.has(DataComponents.CUSTOM_NAME)) {
			mutableComponent.withStyle(ChatFormatting.ITALIC);
		}

		return mutableComponent;
	}

	public <T extends TooltipProvider> void addToTooltip(
		DataComponentType<T> dataComponentType,
		Item.TooltipContext tooltipContext,
		TooltipDisplay tooltipDisplay,
		Consumer<Component> consumer,
		TooltipFlag tooltipFlag
	) {
		T tooltipProvider = (T)this.get(dataComponentType);
		if (tooltipProvider != null && tooltipDisplay.shows(dataComponentType)) {
			tooltipProvider.addToTooltip(tooltipContext, consumer, tooltipFlag, this.components);
		}
	}

	public List<Component> getTooltipLines(Item.TooltipContext tooltipContext, @Nullable Player player, TooltipFlag tooltipFlag) {
		TooltipDisplay tooltipDisplay = this.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
		if (!tooltipFlag.isCreative() && tooltipDisplay.hideTooltip()) {
			boolean bl = this.getItem().shouldPrintOpWarning(this, player);
			return bl ? OP_NBT_WARNING : List.of();
		} else {
			List<Component> list = Lists.newArrayList();
			list.add(this.getStyledHoverName());
			this.addDetailsToTooltip(tooltipContext, tooltipDisplay, player, tooltipFlag, list::add);
			return list;
		}
	}

	public void addDetailsToTooltip(
		Item.TooltipContext tooltipContext, TooltipDisplay tooltipDisplay, @Nullable Player player, TooltipFlag tooltipFlag, Consumer<Component> consumer
	) {
		this.getItem().appendHoverText(this, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		this.addToTooltip(DataComponents.TROPICAL_FISH_PATTERN, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		this.addToTooltip(DataComponents.INSTRUMENT, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		this.addToTooltip(DataComponents.MAP_ID, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		this.addToTooltip(DataComponents.BEES, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		this.addToTooltip(DataComponents.CONTAINER_LOOT, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		this.addToTooltip(DataComponents.CONTAINER, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		this.addToTooltip(DataComponents.BANNER_PATTERNS, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		this.addToTooltip(DataComponents.POT_DECORATIONS, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		this.addToTooltip(DataComponents.WRITTEN_BOOK_CONTENT, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		this.addToTooltip(DataComponents.CHARGED_PROJECTILES, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		this.addToTooltip(DataComponents.FIREWORKS, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		this.addToTooltip(DataComponents.FIREWORK_EXPLOSION, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		this.addToTooltip(DataComponents.POTION_CONTENTS, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		this.addToTooltip(DataComponents.JUKEBOX_PLAYABLE, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		this.addToTooltip(DataComponents.TRIM, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		this.addToTooltip(DataComponents.STORED_ENCHANTMENTS, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		this.addToTooltip(DataComponents.ENCHANTMENTS, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		this.addToTooltip(DataComponents.DYED_COLOR, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		this.addToTooltip(DataComponents.PROFILE, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		this.addToTooltip(DataComponents.LORE, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		this.addAttributeTooltips(consumer, tooltipDisplay, player);
		this.addUnitComponentToTooltip(DataComponents.INTANGIBLE_PROJECTILE, INTANGIBLE_TOOLTIP, tooltipDisplay, consumer);
		this.addUnitComponentToTooltip(DataComponents.UNBREAKABLE, UNBREAKABLE_TOOLTIP, tooltipDisplay, consumer);
		this.addToTooltip(DataComponents.OMINOUS_BOTTLE_AMPLIFIER, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		this.addToTooltip(DataComponents.SUSPICIOUS_STEW_EFFECTS, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		this.addToTooltip(DataComponents.BLOCK_STATE, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		this.addToTooltip(DataComponents.ENTITY_DATA, tooltipContext, tooltipDisplay, consumer, tooltipFlag);
		if ((this.is(Items.SPAWNER) || this.is(Items.TRIAL_SPAWNER)) && tooltipDisplay.shows(DataComponents.BLOCK_ENTITY_DATA)) {
			TypedEntityData<BlockEntityType<?>> typedEntityData = this.get(DataComponents.BLOCK_ENTITY_DATA);
			Spawner.appendHoverText(typedEntityData, consumer, "SpawnData");
		}

		AdventureModePredicate adventureModePredicate = this.get(DataComponents.CAN_BREAK);
		if (adventureModePredicate != null && tooltipDisplay.shows(DataComponents.CAN_BREAK)) {
			consumer.accept(CommonComponents.EMPTY);
			consumer.accept(AdventureModePredicate.CAN_BREAK_HEADER);
			adventureModePredicate.addToTooltip(consumer);
		}

		AdventureModePredicate adventureModePredicate2 = this.get(DataComponents.CAN_PLACE_ON);
		if (adventureModePredicate2 != null && tooltipDisplay.shows(DataComponents.CAN_PLACE_ON)) {
			consumer.accept(CommonComponents.EMPTY);
			consumer.accept(AdventureModePredicate.CAN_PLACE_HEADER);
			adventureModePredicate2.addToTooltip(consumer);
		}

		if (tooltipFlag.isAdvanced()) {
			if (this.isDamaged() && tooltipDisplay.shows(DataComponents.DAMAGE)) {
				consumer.accept(Component.translatable("item.durability", this.getMaxDamage() - this.getDamageValue(), this.getMaxDamage()));
			}

			consumer.accept(Component.literal(BuiltInRegistries.ITEM.getKey(this.getItem()).toString()).withStyle(ChatFormatting.DARK_GRAY));
			int i = this.components.size();
			if (i > 0) {
				consumer.accept(Component.translatable("item.components", i).withStyle(ChatFormatting.DARK_GRAY));
			}
		}

		if (player != null && !this.getItem().isEnabled(player.level().enabledFeatures())) {
			consumer.accept(DISABLED_ITEM_TOOLTIP);
		}

		boolean bl = this.getItem().shouldPrintOpWarning(this, player);
		if (bl) {
			OP_NBT_WARNING.forEach(consumer);
		}
	}

	private void addUnitComponentToTooltip(
		DataComponentType<?> dataComponentType, Component component, TooltipDisplay tooltipDisplay, Consumer<Component> consumer
	) {
		if (this.has(dataComponentType) && tooltipDisplay.shows(dataComponentType)) {
			consumer.accept(component);
		}
	}

	private void addAttributeTooltips(Consumer<Component> consumer, TooltipDisplay tooltipDisplay, @Nullable Player player) {
		if (tooltipDisplay.shows(DataComponents.ATTRIBUTE_MODIFIERS)) {
			for (EquipmentSlotGroup equipmentSlotGroup : EquipmentSlotGroup.values()) {
				MutableBoolean mutableBoolean = new MutableBoolean(true);
				this.forEachModifier(equipmentSlotGroup, (holder, attributeModifier, display) -> {
					if (display != ItemAttributeModifiers.Display.hidden()) {
						if (mutableBoolean.isTrue()) {
							consumer.accept(CommonComponents.EMPTY);
							consumer.accept(Component.translatable("item.modifiers." + equipmentSlotGroup.getSerializedName()).withStyle(ChatFormatting.GRAY));
							mutableBoolean.setFalse();
						}

						display.apply(consumer, player, holder, attributeModifier);
					}
				});
			}
		}
	}

	public boolean hasFoil() {
		Boolean boolean_ = this.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
		return boolean_ != null ? boolean_ : this.getItem().isFoil(this);
	}

	public Rarity getRarity() {
		Rarity rarity = this.getOrDefault(DataComponents.RARITY, Rarity.COMMON);
		if (!this.isEnchanted()) {
			return rarity;
		}

		return switch (rarity) {
			case COMMON, UNCOMMON -> Rarity.RARE;
			case RARE -> Rarity.EPIC;
			default -> rarity;
		};
	}

	public boolean isEnchantable() {
		if (!this.has(DataComponents.ENCHANTABLE)) {
			return false;
		}

		ItemEnchantments itemEnchantments = this.get(DataComponents.ENCHANTMENTS);
		return itemEnchantments != null && itemEnchantments.isEmpty();
	}

	public void enchant(Holder<Enchantment> holder, int i) {
		EnchantmentHelper.updateEnchantments(this, mutable -> mutable.upgrade(holder, i));
	}

	public boolean isEnchanted() {
		return !this.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
	}

	public ItemEnchantments getEnchantments() {
		return this.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
	}

	public boolean isFramed() {
		return this.entityRepresentation instanceof ItemFrame;
	}

	public void setEntityRepresentation(@Nullable Entity entity) {
		if (!this.isEmpty()) {
			this.entityRepresentation = entity;
		}
	}

	public @Nullable ItemFrame getFrame() {
		return this.entityRepresentation instanceof ItemFrame ? (ItemFrame)this.getEntityRepresentation() : null;
	}

	public @Nullable Entity getEntityRepresentation() {
		return !this.isEmpty() ? this.entityRepresentation : null;
	}

	public void forEachModifier(
		EquipmentSlotGroup equipmentSlotGroup, TriConsumer<Holder<Attribute>, AttributeModifier, ItemAttributeModifiers.Display> triConsumer
	) {
		ItemAttributeModifiers itemAttributeModifiers = this.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		itemAttributeModifiers.forEach(equipmentSlotGroup, triConsumer);
		EnchantmentHelper.forEachModifier(
			this, equipmentSlotGroup, (holder, attributeModifier) -> triConsumer.accept(holder, attributeModifier, ItemAttributeModifiers.Display.attributeModifiers())
		);
	}

	public void forEachModifier(EquipmentSlot equipmentSlot, BiConsumer<Holder<Attribute>, AttributeModifier> biConsumer) {
		ItemAttributeModifiers itemAttributeModifiers = this.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		itemAttributeModifiers.forEach(equipmentSlot, biConsumer);
		EnchantmentHelper.forEachModifier(this, equipmentSlot, biConsumer);
	}

	public Component getDisplayName() {
		MutableComponent mutableComponent = Component.empty().append(this.getHoverName());
		if (this.has(DataComponents.CUSTOM_NAME)) {
			mutableComponent.withStyle(ChatFormatting.ITALIC);
		}

		MutableComponent mutableComponent2 = ComponentUtils.wrapInSquareBrackets(mutableComponent);
		if (!this.isEmpty()) {
			mutableComponent2.withStyle(this.getRarity().color()).withStyle(style -> style.withHoverEvent(new HoverEvent.ShowItem(this)));
		}

		return mutableComponent2;
	}

	public SwingAnimation getSwingAnimation() {
		return this.getOrDefault(DataComponents.SWING_ANIMATION, SwingAnimation.DEFAULT);
	}

	public boolean canPlaceOnBlockInAdventureMode(BlockInWorld blockInWorld) {
		AdventureModePredicate adventureModePredicate = this.get(DataComponents.CAN_PLACE_ON);
		return adventureModePredicate != null && adventureModePredicate.test(blockInWorld);
	}

	public boolean canBreakBlockInAdventureMode(BlockInWorld blockInWorld) {
		AdventureModePredicate adventureModePredicate = this.get(DataComponents.CAN_BREAK);
		return adventureModePredicate != null && adventureModePredicate.test(blockInWorld);
	}

	public int getPopTime() {
		return this.popTime;
	}

	public void setPopTime(int i) {
		this.popTime = i;
	}

	public int getCount() {
		return this.isEmpty() ? 0 : this.count;
	}

	public void setCount(int i) {
		this.count = i;
	}

	public void limitSize(int i) {
		if (!this.isEmpty() && this.getCount() > i) {
			this.setCount(i);
		}
	}

	public void grow(int i) {
		this.setCount(this.getCount() + i);
	}

	public void shrink(int i) {
		this.grow(-i);
	}

	public void consume(int i, @Nullable LivingEntity livingEntity) {
		if (livingEntity == null || !livingEntity.hasInfiniteMaterials()) {
			this.shrink(i);
		}
	}

	public ItemStack consumeAndReturn(int i, @Nullable LivingEntity livingEntity) {
		ItemStack itemStack = this.copyWithCount(i);
		this.consume(i, livingEntity);
		return itemStack;
	}

	public void onUseTick(Level level, LivingEntity livingEntity, int i) {
		Consumable consumable = this.get(DataComponents.CONSUMABLE);
		if (consumable != null && consumable.shouldEmitParticlesAndSounds(i)) {
			consumable.emitParticlesAndSounds(livingEntity.getRandom(), livingEntity, this, 5);
		}

		KineticWeapon kineticWeapon = this.get(DataComponents.KINETIC_WEAPON);
		if (kineticWeapon != null && !level.isClientSide()) {
			kineticWeapon.damageEntities(this, i, livingEntity, livingEntity.getUsedItemHand().asEquipmentSlot());
		} else {
			this.getItem().onUseTick(level, livingEntity, this, i);
		}
	}

	public void onDestroyed(ItemEntity itemEntity) {
		this.getItem().onDestroyed(itemEntity);
	}

	public boolean canBeHurtBy(DamageSource damageSource) {
		DamageResistant damageResistant = this.get(DataComponents.DAMAGE_RESISTANT);
		return damageResistant == null || !damageResistant.isResistantTo(damageSource);
	}

	public boolean isValidRepairItem(ItemStack itemStack) {
		Repairable repairable = this.get(DataComponents.REPAIRABLE);
		return repairable != null && repairable.isValidRepairItem(itemStack);
	}

	public boolean canDestroyBlock(BlockState blockState, Level level, BlockPos blockPos, Player player) {
		return this.getItem().canDestroyBlock(this, blockState, level, blockPos, player);
	}

	public DamageSource getDamageSource(LivingEntity livingEntity, Supplier<DamageSource> supplier) {
		return Optional.ofNullable(this.get(DataComponents.DAMAGE_TYPE))
			.flatMap(eitherHolder -> eitherHolder.unwrap(livingEntity.registryAccess()))
			.map(holder -> new DamageSource((Holder<DamageType>)holder, livingEntity))
			.or(() -> Optional.ofNullable(this.getItem().getItemDamageSource(livingEntity)))
			.orElseGet(supplier);
	}
}
