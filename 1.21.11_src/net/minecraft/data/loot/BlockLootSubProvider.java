package net.minecraft.data.loot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import net.fabricmc.fabric.api.datagen.v1.loot.FabricBlockLootTableGenerator;
import net.minecraft.advancements.criterion.BlockPredicate;
import net.minecraft.advancements.criterion.DataComponentMatchers;
import net.minecraft.advancements.criterion.EnchantmentPredicate;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.advancements.criterion.LocationPredicate;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.advancements.criterion.StatePropertiesPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.predicates.DataComponentPredicates;
import net.minecraft.core.component.predicates.EnchantmentsPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CopperGolemStatueBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.MossyCarpetBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.SegmentableBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.IntRange;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.ApplyExplosionDecay;
import net.minecraft.world.level.storage.loot.functions.CopyBlockState;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.functions.FunctionUserBuilder;
import net.minecraft.world.level.storage.loot.functions.LimitCount;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.BonusLevelTableCondition;
import net.minecraft.world.level.storage.loot.predicates.ConditionUserBuilder;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.predicates.LocationCheck;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.predicates.MatchTool;
import net.minecraft.world.level.storage.loot.providers.number.BinomialDistributionGenerator;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.datagen.v1.loot.FabricBlockLootTableGenerator} injected by mod fabric-data-generation-api-v1</p>
 */
public abstract class BlockLootSubProvider implements LootTableSubProvider, FabricBlockLootTableGenerator {
	protected final HolderLookup.Provider registries;
	protected final Set<Item> explosionResistant;
	protected final FeatureFlagSet enabledFeatures;
	protected final Map<ResourceKey<LootTable>, LootTable.Builder> map;
	protected static final float[] NORMAL_LEAVES_SAPLING_CHANCES = new float[]{0.05F, 0.0625F, 0.083333336F, 0.1F};
	private static final float[] NORMAL_LEAVES_STICK_CHANCES = new float[]{0.02F, 0.022222223F, 0.025F, 0.033333335F, 0.1F};

	public LootItemCondition.Builder hasSilkTouch() {
		return MatchTool.toolMatches(
			ItemPredicate.Builder.item()
				.withComponents(
					DataComponentMatchers.Builder.components()
						.partial(
							DataComponentPredicates.ENCHANTMENTS,
							EnchantmentsPredicate.enchantments(
								List.of(
									new EnchantmentPredicate(this.registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), MinMaxBounds.Ints.atLeast(1))
								)
							)
						)
						.build()
				)
		);
	}

	public LootItemCondition.Builder doesNotHaveSilkTouch() {
		return this.hasSilkTouch().invert();
	}

	public LootItemCondition.Builder hasShears() {
		return MatchTool.toolMatches(ItemPredicate.Builder.item().of(this.registries.lookupOrThrow(Registries.ITEM), Items.SHEARS));
	}

	public final LootItemCondition.Builder hasShearsOrSilkTouch() {
		return this.hasShears().or(this.hasSilkTouch());
	}

	public final LootItemCondition.Builder doesNotHaveShearsOrSilkTouch() {
		return this.hasShearsOrSilkTouch().invert();
	}

	protected BlockLootSubProvider(Set<Item> set, FeatureFlagSet featureFlagSet, HolderLookup.Provider provider) {
		this(set, featureFlagSet, new HashMap<>(), provider);
	}

	protected BlockLootSubProvider(
		Set<Item> set, FeatureFlagSet featureFlagSet, Map<ResourceKey<LootTable>, LootTable.Builder> map, HolderLookup.Provider provider
	) {
		this.explosionResistant = set;
		this.enabledFeatures = featureFlagSet;
		this.map = map;
		this.registries = provider;
	}

	public <T extends FunctionUserBuilder<T>> T applyExplosionDecay(ItemLike itemLike, FunctionUserBuilder<T> functionUserBuilder) {
		return !this.explosionResistant.contains(itemLike.asItem()) ? functionUserBuilder.apply(ApplyExplosionDecay.explosionDecay()) : functionUserBuilder.unwrap();
	}

	public <T extends ConditionUserBuilder<T>> T applyExplosionCondition(ItemLike itemLike, ConditionUserBuilder<T> conditionUserBuilder) {
		return !this.explosionResistant.contains(itemLike.asItem())
			? conditionUserBuilder.when(ExplosionCondition.survivesExplosion())
			: conditionUserBuilder.unwrap();
	}

	public LootTable.Builder createSingleItemTable(ItemLike itemLike) {
		return LootTable.lootTable()
			.withPool(this.applyExplosionCondition(itemLike, LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F)).add(LootItem.lootTableItem(itemLike))));
	}

	public static LootTable.Builder createSelfDropDispatchTable(Block block, LootItemCondition.Builder builder, LootPoolEntryContainer.Builder<?> builder2) {
		return LootTable.lootTable()
			.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F)).add(LootItem.lootTableItem(block).when(builder).otherwise(builder2)));
	}

	public LootTable.Builder createSilkTouchDispatchTable(Block block, LootPoolEntryContainer.Builder<?> builder) {
		return createSelfDropDispatchTable(block, this.hasSilkTouch(), builder);
	}

	public LootTable.Builder createShearsDispatchTable(Block block, LootPoolEntryContainer.Builder<?> builder) {
		return createSelfDropDispatchTable(block, this.hasShears(), builder);
	}

	public LootTable.Builder createSilkTouchOrShearsDispatchTable(Block block, LootPoolEntryContainer.Builder<?> builder) {
		return createSelfDropDispatchTable(block, this.hasShearsOrSilkTouch(), builder);
	}

	public LootTable.Builder createSingleItemTableWithSilkTouch(Block block, ItemLike itemLike) {
		return this.createSilkTouchDispatchTable(block, (LootPoolEntryContainer.Builder<?>)this.applyExplosionCondition(block, LootItem.lootTableItem(itemLike)));
	}

	public LootTable.Builder createSingleItemTable(ItemLike itemLike, NumberProvider numberProvider) {
		return LootTable.lootTable()
			.withPool(
				LootPool.lootPool()
					.setRolls(ConstantValue.exactly(1.0F))
					.add(
						(LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(
							itemLike, LootItem.lootTableItem(itemLike).apply(SetItemCountFunction.setCount(numberProvider))
						)
					)
			);
	}

	public LootTable.Builder createSingleItemTableWithSilkTouch(Block block, ItemLike itemLike, NumberProvider numberProvider) {
		return this.createSilkTouchDispatchTable(
			block,
			(LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(block, LootItem.lootTableItem(itemLike).apply(SetItemCountFunction.setCount(numberProvider)))
		);
	}

	public final LootTable.Builder createSilkTouchOnlyTable(ItemLike itemLike) {
		return LootTable.lootTable()
			.withPool(LootPool.lootPool().when(this.hasSilkTouch()).setRolls(ConstantValue.exactly(1.0F)).add(LootItem.lootTableItem(itemLike)));
	}

	public final LootTable.Builder createPotFlowerItemTable(ItemLike itemLike) {
		return LootTable.lootTable()
			.withPool(
				this.applyExplosionCondition(Blocks.FLOWER_POT, LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F)).add(LootItem.lootTableItem(Blocks.FLOWER_POT)))
			)
			.withPool(this.applyExplosionCondition(itemLike, LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F)).add(LootItem.lootTableItem(itemLike))));
	}

	public LootTable.Builder createSlabItemTable(Block block) {
		return LootTable.lootTable()
			.withPool(
				LootPool.lootPool()
					.setRolls(ConstantValue.exactly(1.0F))
					.add(
						(LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(
							block,
							LootItem.lootTableItem(block)
								.apply(
									SetItemCountFunction.setCount(ConstantValue.exactly(2.0F))
										.when(
											LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
												.setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(SlabBlock.TYPE, SlabType.DOUBLE))
										)
								)
						)
					)
			);
	}

	public <T extends Comparable<T> & StringRepresentable> LootTable.Builder createSinglePropConditionTable(Block block, Property<T> property, T comparable) {
		return LootTable.lootTable()
			.withPool(
				this.applyExplosionCondition(
					block,
					LootPool.lootPool()
						.setRolls(ConstantValue.exactly(1.0F))
						.add(
							LootItem.lootTableItem(block)
								.when(
									LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
										.setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(property, comparable))
								)
						)
				)
			);
	}

	public LootTable.Builder createNameableBlockEntityTable(Block block) {
		return LootTable.lootTable()
			.withPool(
				this.applyExplosionCondition(
					block,
					LootPool.lootPool()
						.setRolls(ConstantValue.exactly(1.0F))
						.add(
							LootItem.lootTableItem(block)
								.apply(CopyComponentsFunction.copyComponentsFromBlockEntity(LootContextParams.BLOCK_ENTITY).include(DataComponents.CUSTOM_NAME))
						)
				)
			);
	}

	public LootTable.Builder createShulkerBoxDrop(Block block) {
		return LootTable.lootTable()
			.withPool(
				this.applyExplosionCondition(
					block,
					LootPool.lootPool()
						.setRolls(ConstantValue.exactly(1.0F))
						.add(
							LootItem.lootTableItem(block)
								.apply(
									CopyComponentsFunction.copyComponentsFromBlockEntity(LootContextParams.BLOCK_ENTITY)
										.include(DataComponents.CUSTOM_NAME)
										.include(DataComponents.CONTAINER)
										.include(DataComponents.LOCK)
										.include(DataComponents.CONTAINER_LOOT)
								)
						)
				)
			);
	}

	public LootTable.Builder createCopperOreDrops(Block block) {
		HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
		return this.createSilkTouchDispatchTable(
			block,
			(LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(
				block,
				LootItem.lootTableItem(Items.RAW_COPPER)
					.apply(SetItemCountFunction.setCount(UniformGenerator.between(2.0F, 5.0F)))
					.apply(ApplyBonusCount.addOreBonusCount(registryLookup.getOrThrow(Enchantments.FORTUNE)))
			)
		);
	}

	public LootTable.Builder createLapisOreDrops(Block block) {
		HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
		return this.createSilkTouchDispatchTable(
			block,
			(LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(
				block,
				LootItem.lootTableItem(Items.LAPIS_LAZULI)
					.apply(SetItemCountFunction.setCount(UniformGenerator.between(4.0F, 9.0F)))
					.apply(ApplyBonusCount.addOreBonusCount(registryLookup.getOrThrow(Enchantments.FORTUNE)))
			)
		);
	}

	public LootTable.Builder createRedstoneOreDrops(Block block) {
		HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
		return this.createSilkTouchDispatchTable(
			block,
			(LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(
				block,
				LootItem.lootTableItem(Items.REDSTONE)
					.apply(SetItemCountFunction.setCount(UniformGenerator.between(4.0F, 5.0F)))
					.apply(ApplyBonusCount.addUniformBonusCount(registryLookup.getOrThrow(Enchantments.FORTUNE)))
			)
		);
	}

	public LootTable.Builder createBannerDrop(Block block) {
		return LootTable.lootTable()
			.withPool(
				this.applyExplosionCondition(
					block,
					LootPool.lootPool()
						.setRolls(ConstantValue.exactly(1.0F))
						.add(
							LootItem.lootTableItem(block)
								.apply(
									CopyComponentsFunction.copyComponentsFromBlockEntity(LootContextParams.BLOCK_ENTITY)
										.include(DataComponents.CUSTOM_NAME)
										.include(DataComponents.ITEM_NAME)
										.include(DataComponents.TOOLTIP_DISPLAY)
										.include(DataComponents.BANNER_PATTERNS)
										.include(DataComponents.RARITY)
								)
						)
				)
			);
	}

	public LootTable.Builder createBeeNestDrop(Block block) {
		return LootTable.lootTable()
			.withPool(
				LootPool.lootPool()
					.when(this.hasSilkTouch())
					.setRolls(ConstantValue.exactly(1.0F))
					.add(
						LootItem.lootTableItem(block)
							.apply(CopyComponentsFunction.copyComponentsFromBlockEntity(LootContextParams.BLOCK_ENTITY).include(DataComponents.BEES))
							.apply(CopyBlockState.copyState(block).copy(BeehiveBlock.HONEY_LEVEL))
					)
			);
	}

	public LootTable.Builder createBeeHiveDrop(Block block) {
		return LootTable.lootTable()
			.withPool(
				LootPool.lootPool()
					.setRolls(ConstantValue.exactly(1.0F))
					.add(
						LootItem.lootTableItem(block)
							.when(this.hasSilkTouch())
							.apply(CopyComponentsFunction.copyComponentsFromBlockEntity(LootContextParams.BLOCK_ENTITY).include(DataComponents.BEES))
							.apply(CopyBlockState.copyState(block).copy(BeehiveBlock.HONEY_LEVEL))
							.otherwise(LootItem.lootTableItem(block))
					)
			);
	}

	public LootTable.Builder createCaveVinesDrop(Block block) {
		return LootTable.lootTable()
			.withPool(
				LootPool.lootPool()
					.add(LootItem.lootTableItem(Items.GLOW_BERRIES))
					.when(
						LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
							.setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(CaveVines.BERRIES, true))
					)
			);
	}

	public LootTable.Builder createCopperGolemStatueBlock(Block block) {
		return LootTable.lootTable()
			.withPool(
				this.applyExplosionCondition(
					block,
					LootPool.lootPool()
						.setRolls(ConstantValue.exactly(1.0F))
						.add(
							LootItem.lootTableItem(block)
								.apply(CopyComponentsFunction.copyComponentsFromBlockEntity(LootContextParams.BLOCK_ENTITY).include(DataComponents.CUSTOM_NAME))
								.apply(CopyBlockState.copyState(block).copy(CopperGolemStatueBlock.POSE))
						)
				)
			);
	}

	public LootTable.Builder createOreDrop(Block block, Item item) {
		HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
		return this.createSilkTouchDispatchTable(
			block,
			(LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(
				block, LootItem.lootTableItem(item).apply(ApplyBonusCount.addOreBonusCount(registryLookup.getOrThrow(Enchantments.FORTUNE)))
			)
		);
	}

	public LootTable.Builder createMushroomBlockDrop(Block block, ItemLike itemLike) {
		return this.createSilkTouchDispatchTable(
			block,
			(LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(
				block,
				LootItem.lootTableItem(itemLike)
					.apply(SetItemCountFunction.setCount(UniformGenerator.between(-6.0F, 2.0F)))
					.apply(LimitCount.limitCount(IntRange.lowerBound(0)))
			)
		);
	}

	public LootTable.Builder createGrassDrops(Block block) {
		HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
		return this.createShearsDispatchTable(
			block,
			(LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(
				block,
				LootItem.lootTableItem(Items.WHEAT_SEEDS)
					.when(LootItemRandomChanceCondition.randomChance(0.125F))
					.apply(ApplyBonusCount.addUniformBonusCount(registryLookup.getOrThrow(Enchantments.FORTUNE), 2))
			)
		);
	}

	public LootTable.Builder createStemDrops(Block block, Item item) {
		return LootTable.lootTable()
			.withPool(
				this.applyExplosionDecay(
					block,
					LootPool.lootPool()
						.setRolls(ConstantValue.exactly(1.0F))
						.add(
							LootItem.lootTableItem(item)
								.apply(
									StemBlock.AGE.getPossibleValues(),
									integer -> SetItemCountFunction.setCount(BinomialDistributionGenerator.binomial(3, (integer + 1) / 15.0F))
										.when(
											LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
												.setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(StemBlock.AGE, integer.intValue()))
										)
								)
						)
				)
			);
	}

	public LootTable.Builder createAttachedStemDrops(Block block, Item item) {
		return LootTable.lootTable()
			.withPool(
				this.applyExplosionDecay(
					block,
					LootPool.lootPool()
						.setRolls(ConstantValue.exactly(1.0F))
						.add(LootItem.lootTableItem(item).apply(SetItemCountFunction.setCount(BinomialDistributionGenerator.binomial(3, 0.53333336F))))
				)
			);
	}

	public LootTable.Builder createShearsOnlyDrop(ItemLike itemLike) {
		return LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F)).when(this.hasShears()).add(LootItem.lootTableItem(itemLike)));
	}

	public LootTable.Builder createShearsOrSilkTouchOnlyDrop(ItemLike itemLike) {
		return LootTable.lootTable()
			.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F)).when(this.hasShearsOrSilkTouch()).add(LootItem.lootTableItem(itemLike)));
	}

	public LootTable.Builder createMultifaceBlockDrops(Block block, LootItemCondition.Builder builder) {
		return LootTable.lootTable()
			.withPool(
				LootPool.lootPool()
					.add(
						(LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(
							block,
							LootItem.lootTableItem(block)
								.when(builder)
								.apply(
									Direction.values(),
									direction -> SetItemCountFunction.setCount(ConstantValue.exactly(1.0F), true)
										.when(
											LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
												.setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(MultifaceBlock.getFaceProperty(direction), true))
										)
								)
								.apply(SetItemCountFunction.setCount(ConstantValue.exactly(-1.0F), true))
						)
					)
			);
	}

	public LootTable.Builder createMultifaceBlockDrops(Block block) {
		return LootTable.lootTable()
			.withPool(
				LootPool.lootPool()
					.add(
						(LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(
							block,
							LootItem.lootTableItem(block)
								.apply(
									Direction.values(),
									direction -> SetItemCountFunction.setCount(ConstantValue.exactly(1.0F), true)
										.when(
											LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
												.setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(MultifaceBlock.getFaceProperty(direction), true))
										)
								)
								.apply(SetItemCountFunction.setCount(ConstantValue.exactly(-1.0F), true))
						)
					)
			);
	}

	public LootTable.Builder createMossyCarpetBlockDrops(Block block) {
		return LootTable.lootTable()
			.withPool(
				LootPool.lootPool()
					.add(
						(LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(
							block,
							LootItem.lootTableItem(block)
								.when(
									LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
										.setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(MossyCarpetBlock.BASE, true))
								)
						)
					)
			);
	}

	public LootTable.Builder createLeavesDrops(Block block, Block block2, float... fs) {
		HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
		return this.createSilkTouchOrShearsDispatchTable(
				block,
				((LootPoolSingletonContainer.Builder)this.applyExplosionCondition(block, LootItem.lootTableItem(block2)))
					.when(BonusLevelTableCondition.bonusLevelFlatChance(registryLookup.getOrThrow(Enchantments.FORTUNE), fs))
			)
			.withPool(
				LootPool.lootPool()
					.setRolls(ConstantValue.exactly(1.0F))
					.when(this.doesNotHaveShearsOrSilkTouch())
					.add(
						((LootPoolSingletonContainer.Builder)this.applyExplosionDecay(
								block, LootItem.lootTableItem(Items.STICK).apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F)))
							))
							.when(BonusLevelTableCondition.bonusLevelFlatChance(registryLookup.getOrThrow(Enchantments.FORTUNE), NORMAL_LEAVES_STICK_CHANCES))
					)
			);
	}

	public LootTable.Builder createOakLeavesDrops(Block block, Block block2, float... fs) {
		HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
		return this.createLeavesDrops(block, block2, fs)
			.withPool(
				LootPool.lootPool()
					.setRolls(ConstantValue.exactly(1.0F))
					.when(this.doesNotHaveShearsOrSilkTouch())
					.add(
						((LootPoolSingletonContainer.Builder)this.applyExplosionCondition(block, LootItem.lootTableItem(Items.APPLE)))
							.when(
								BonusLevelTableCondition.bonusLevelFlatChance(registryLookup.getOrThrow(Enchantments.FORTUNE), 0.005F, 0.0055555557F, 0.00625F, 0.008333334F, 0.025F)
							)
					)
			);
	}

	public LootTable.Builder createMangroveLeavesDrops(Block block) {
		HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
		return this.createSilkTouchOrShearsDispatchTable(
			block,
			((LootPoolSingletonContainer.Builder)this.applyExplosionDecay(
					Blocks.MANGROVE_LEAVES, LootItem.lootTableItem(Items.STICK).apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F)))
				))
				.when(BonusLevelTableCondition.bonusLevelFlatChance(registryLookup.getOrThrow(Enchantments.FORTUNE), NORMAL_LEAVES_STICK_CHANCES))
		);
	}

	public LootTable.Builder createCropDrops(Block block, Item item, Item item2, LootItemCondition.Builder builder) {
		HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
		return this.applyExplosionDecay(
			block,
			LootTable.lootTable()
				.withPool(LootPool.lootPool().add(LootItem.lootTableItem(item).when(builder).otherwise(LootItem.lootTableItem(item2))))
				.withPool(
					LootPool.lootPool()
						.when(builder)
						.add(
							LootItem.lootTableItem(item2).apply(ApplyBonusCount.addBonusBinomialDistributionCount(registryLookup.getOrThrow(Enchantments.FORTUNE), 0.5714286F, 3))
						)
				)
		);
	}

	public LootTable.Builder createDoublePlantShearsDrop(Block block) {
		return LootTable.lootTable()
			.withPool(LootPool.lootPool().when(this.hasShears()).add(LootItem.lootTableItem(block).apply(SetItemCountFunction.setCount(ConstantValue.exactly(2.0F)))));
	}

	public LootTable.Builder createDoublePlantWithSeedDrops(Block block, Block block2) {
		HolderLookup.RegistryLookup<Block> registryLookup = this.registries.lookupOrThrow(Registries.BLOCK);
		LootPoolEntryContainer.Builder<?> builder = LootItem.lootTableItem(block2)
			.apply(SetItemCountFunction.setCount(ConstantValue.exactly(2.0F)))
			.when(this.hasShears())
			.otherwise(
				((LootPoolSingletonContainer.Builder)this.applyExplosionCondition(block, LootItem.lootTableItem(Items.WHEAT_SEEDS)))
					.when(LootItemRandomChanceCondition.randomChance(0.125F))
			);
		return LootTable.lootTable()
			.withPool(
				LootPool.lootPool()
					.add(builder)
					.when(
						LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
							.setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER))
					)
					.when(
						LocationCheck.checkLocation(
							LocationPredicate.Builder.location()
								.setBlock(
									BlockPredicate.Builder.block()
										.of(registryLookup, block)
										.setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER))
								),
							new BlockPos(0, 1, 0)
						)
					)
			)
			.withPool(
				LootPool.lootPool()
					.add(builder)
					.when(
						LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
							.setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER))
					)
					.when(
						LocationCheck.checkLocation(
							LocationPredicate.Builder.location()
								.setBlock(
									BlockPredicate.Builder.block()
										.of(registryLookup, block)
										.setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER))
								),
							new BlockPos(0, -1, 0)
						)
					)
			);
	}

	public LootTable.Builder createCandleDrops(Block block) {
		return LootTable.lootTable()
			.withPool(
				LootPool.lootPool()
					.setRolls(ConstantValue.exactly(1.0F))
					.add(
						(LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(
							block,
							LootItem.lootTableItem(block)
								.apply(
									List.of(2, 3, 4),
									integer -> SetItemCountFunction.setCount(ConstantValue.exactly(integer.intValue()))
										.when(
											LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
												.setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(CandleBlock.CANDLES, integer.intValue()))
										)
								)
						)
					)
			);
	}

	public LootTable.Builder createSegmentedBlockDrops(Block block) {
		return block instanceof SegmentableBlock segmentableBlock
			? LootTable.lootTable()
				.withPool(
					LootPool.lootPool()
						.setRolls(ConstantValue.exactly(1.0F))
						.add(
							(LootPoolEntryContainer.Builder<?>)this.applyExplosionDecay(
								block,
								LootItem.lootTableItem(block)
									.apply(
										IntStream.rangeClosed(1, 4).boxed().toList(),
										integer -> SetItemCountFunction.setCount(ConstantValue.exactly(integer.intValue()))
											.when(
												LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
													.setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(segmentableBlock.getSegmentAmountProperty(), integer.intValue()))
											)
									)
							)
						)
				)
			: noDrop();
	}

	public static LootTable.Builder createCandleCakeDrops(Block block) {
		return LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F)).add(LootItem.lootTableItem(block)));
	}

	public static LootTable.Builder noDrop() {
		return LootTable.lootTable();
	}

	public abstract void generate();

	@Override
	public void generate(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> biConsumer) {
		this.generate();
		Set<ResourceKey<LootTable>> set = new HashSet<>();

		for (Block block : BuiltInRegistries.BLOCK) {
			if (block.isEnabled(this.enabledFeatures)) {
				block.getLootTable()
					.ifPresent(
						resourceKey -> {
							if (set.add((ResourceKey<LootTable>)resourceKey)) {
								LootTable.Builder builder = this.map.remove(resourceKey);
								if (builder == null) {
									throw new IllegalStateException(
										String.format(Locale.ROOT, "Missing loottable '%s' for '%s'", resourceKey.identifier(), BuiltInRegistries.BLOCK.getKey(block))
									);
								}

								biConsumer.accept((ResourceKey<LootTable>)resourceKey, builder);
							}
						}
					);
			}
		}

		if (!this.map.isEmpty()) {
			throw new IllegalStateException("Created block loot tables for non-blocks: " + this.map.keySet());
		}
	}

	public void addNetherVinesDropTable(Block block, Block block2) {
		HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
		LootTable.Builder builder = this.createSilkTouchOrShearsDispatchTable(
			block,
			LootItem.lootTableItem(block)
				.when(BonusLevelTableCondition.bonusLevelFlatChance(registryLookup.getOrThrow(Enchantments.FORTUNE), 0.33F, 0.55F, 0.77F, 1.0F))
		);
		this.add(block, builder);
		this.add(block2, builder);
	}

	public LootTable.Builder createDoorTable(Block block) {
		return this.createSinglePropConditionTable(block, DoorBlock.HALF, DoubleBlockHalf.LOWER);
	}

	public void dropPottedContents(Block block) {
		this.add(block, blockx -> this.createPotFlowerItemTable(((FlowerPotBlock)blockx).getPotted()));
	}

	public void otherWhenSilkTouch(Block block, Block block2) {
		this.add(block, this.createSilkTouchOnlyTable(block2));
	}

	public void dropOther(Block block, ItemLike itemLike) {
		this.add(block, this.createSingleItemTable(itemLike));
	}

	public void dropWhenSilkTouch(Block block) {
		this.otherWhenSilkTouch(block, block);
	}

	public void dropSelf(Block block) {
		this.dropOther(block, block);
	}

	public void add(Block block, Function<Block, LootTable.Builder> function) {
		this.add(block, function.apply(block));
	}

	public void add(Block block, LootTable.Builder builder) {
		this.map.put(block.getLootTable().orElseThrow(() -> new IllegalStateException("Block " + block + " does not have loot table")), builder);
	}
}
