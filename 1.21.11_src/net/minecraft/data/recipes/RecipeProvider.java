package net.minecraft.data.recipes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.criterion.EnterBlockTrigger;
import net.minecraft.advancements.criterion.ImpossibleTrigger;
import net.minecraft.advancements.criterion.InventoryChangeTrigger;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.BlockFamilies;
import net.minecraft.data.BlockFamily;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SuspiciousEffectHolder;
import org.jspecify.annotations.Nullable;

public abstract class RecipeProvider {
	protected final HolderLookup.Provider registries;
	private final HolderGetter<Item> items;
	protected final RecipeOutput output;
	private static final Map<BlockFamily.Variant, RecipeProvider.FamilyRecipeProvider> SHAPE_BUILDERS = ImmutableMap.<BlockFamily.Variant, RecipeProvider.FamilyRecipeProvider>builder()
		.put(BlockFamily.Variant.BUTTON, (recipeProvider, itemLike, itemLike2) -> recipeProvider.buttonBuilder(itemLike, Ingredient.of(itemLike2)))
		.put(
			BlockFamily.Variant.CHISELED,
			(recipeProvider, itemLike, itemLike2) -> recipeProvider.chiseledBuilder(RecipeCategory.BUILDING_BLOCKS, itemLike, Ingredient.of(itemLike2))
		)
		.put(
			BlockFamily.Variant.CUT,
			(recipeProvider, itemLike, itemLike2) -> recipeProvider.cutBuilder(RecipeCategory.BUILDING_BLOCKS, itemLike, Ingredient.of(itemLike2))
		)
		.put(BlockFamily.Variant.DOOR, (recipeProvider, itemLike, itemLike2) -> recipeProvider.doorBuilder(itemLike, Ingredient.of(itemLike2)))
		.put(BlockFamily.Variant.CUSTOM_FENCE, (recipeProvider, itemLike, itemLike2) -> recipeProvider.fenceBuilder(itemLike, Ingredient.of(itemLike2)))
		.put(BlockFamily.Variant.FENCE, (recipeProvider, itemLike, itemLike2) -> recipeProvider.fenceBuilder(itemLike, Ingredient.of(itemLike2)))
		.put(BlockFamily.Variant.CUSTOM_FENCE_GATE, (recipeProvider, itemLike, itemLike2) -> recipeProvider.fenceGateBuilder(itemLike, Ingredient.of(itemLike2)))
		.put(BlockFamily.Variant.FENCE_GATE, (recipeProvider, itemLike, itemLike2) -> recipeProvider.fenceGateBuilder(itemLike, Ingredient.of(itemLike2)))
		.put(BlockFamily.Variant.SIGN, (recipeProvider, itemLike, itemLike2) -> recipeProvider.signBuilder(itemLike, Ingredient.of(itemLike2)))
		.put(
			BlockFamily.Variant.SLAB,
			(recipeProvider, itemLike, itemLike2) -> recipeProvider.slabBuilder(RecipeCategory.BUILDING_BLOCKS, itemLike, Ingredient.of(itemLike2))
		)
		.put(BlockFamily.Variant.STAIRS, (recipeProvider, itemLike, itemLike2) -> recipeProvider.stairBuilder(itemLike, Ingredient.of(itemLike2)))
		.put(
			BlockFamily.Variant.PRESSURE_PLATE,
			(recipeProvider, itemLike, itemLike2) -> recipeProvider.pressurePlateBuilder(RecipeCategory.REDSTONE, itemLike, Ingredient.of(itemLike2))
		)
		.put(
			BlockFamily.Variant.POLISHED,
			(recipeProvider, itemLike, itemLike2) -> recipeProvider.polishedBuilder(RecipeCategory.BUILDING_BLOCKS, itemLike, Ingredient.of(itemLike2))
		)
		.put(BlockFamily.Variant.TRAPDOOR, (recipeProvider, itemLike, itemLike2) -> recipeProvider.trapdoorBuilder(itemLike, Ingredient.of(itemLike2)))
		.put(
			BlockFamily.Variant.WALL,
			(recipeProvider, itemLike, itemLike2) -> recipeProvider.wallBuilder(RecipeCategory.DECORATIONS, itemLike, Ingredient.of(itemLike2))
		)
		.build();

	protected RecipeProvider(HolderLookup.Provider provider, RecipeOutput recipeOutput) {
		this.registries = provider;
		this.items = provider.lookupOrThrow(Registries.ITEM);
		this.output = recipeOutput;
	}

	public abstract void buildRecipes();

	public void generateForEnabledBlockFamilies(FeatureFlagSet featureFlagSet) {
		BlockFamilies.getAllFamilies().filter(BlockFamily::shouldGenerateRecipe).forEach(blockFamily -> this.generateRecipes(blockFamily, featureFlagSet));
	}

	public void oneToOneConversionRecipe(ItemLike itemLike, ItemLike itemLike2, @Nullable String string) {
		this.oneToOneConversionRecipe(itemLike, itemLike2, string, 1);
	}

	public void oneToOneConversionRecipe(ItemLike itemLike, ItemLike itemLike2, @Nullable String string, int i) {
		this.shapeless(RecipeCategory.MISC, itemLike, i)
			.requires(itemLike2)
			.group(string)
			.unlockedBy(getHasName(itemLike2), this.has(itemLike2))
			.save(this.output, getConversionRecipeName(itemLike, itemLike2));
	}

	public void oreSmelting(List<ItemLike> list, RecipeCategory recipeCategory, ItemLike itemLike, float f, int i, String string) {
		this.oreCooking(RecipeSerializer.SMELTING_RECIPE, SmeltingRecipe::new, list, recipeCategory, itemLike, f, i, string, "_from_smelting");
	}

	public void oreBlasting(List<ItemLike> list, RecipeCategory recipeCategory, ItemLike itemLike, float f, int i, String string) {
		this.oreCooking(RecipeSerializer.BLASTING_RECIPE, BlastingRecipe::new, list, recipeCategory, itemLike, f, i, string, "_from_blasting");
	}

	public final <T extends AbstractCookingRecipe> void oreCooking(
		RecipeSerializer<T> recipeSerializer,
		AbstractCookingRecipe.Factory<T> factory,
		List<ItemLike> list,
		RecipeCategory recipeCategory,
		ItemLike itemLike,
		float f,
		int i,
		String string,
		String string2
	) {
		for (ItemLike itemLike2 : list) {
			SimpleCookingRecipeBuilder.generic(Ingredient.of(itemLike2), recipeCategory, itemLike, f, i, recipeSerializer, factory)
				.group(string)
				.unlockedBy(getHasName(itemLike2), this.has(itemLike2))
				.save(this.output, getItemName(itemLike) + string2 + "_" + getItemName(itemLike2));
		}
	}

	public void netheriteSmithing(Item item, RecipeCategory recipeCategory, Item item2) {
		SmithingTransformRecipeBuilder.smithing(
				Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), Ingredient.of(item), this.tag(ItemTags.NETHERITE_TOOL_MATERIALS), recipeCategory, item2
			)
			.unlocks("has_netherite_ingot", this.has(ItemTags.NETHERITE_TOOL_MATERIALS))
			.save(this.output, getItemName(item2) + "_smithing");
	}

	public void trimSmithing(Item item, ResourceKey<TrimPattern> resourceKey, ResourceKey<Recipe<?>> resourceKey2) {
		Holder.Reference<TrimPattern> reference = this.registries.lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(resourceKey);
		SmithingTrimRecipeBuilder.smithingTrim(
				Ingredient.of(item), this.tag(ItemTags.TRIMMABLE_ARMOR), this.tag(ItemTags.TRIM_MATERIALS), reference, RecipeCategory.MISC
			)
			.unlocks("has_smithing_trim_template", this.has(item))
			.save(this.output, resourceKey2);
	}

	public void twoByTwoPacker(RecipeCategory recipeCategory, ItemLike itemLike, ItemLike itemLike2) {
		this.shaped(recipeCategory, itemLike, 1)
			.define('#', itemLike2)
			.pattern("##")
			.pattern("##")
			.unlockedBy(getHasName(itemLike2), this.has(itemLike2))
			.save(this.output);
	}

	public void threeByThreePacker(RecipeCategory recipeCategory, ItemLike itemLike, ItemLike itemLike2, String string) {
		this.shapeless(recipeCategory, itemLike).requires(itemLike2, 9).unlockedBy(string, this.has(itemLike2)).save(this.output);
	}

	public void threeByThreePacker(RecipeCategory recipeCategory, ItemLike itemLike, ItemLike itemLike2) {
		this.threeByThreePacker(recipeCategory, itemLike, itemLike2, getHasName(itemLike2));
	}

	public void planksFromLog(ItemLike itemLike, TagKey<Item> tagKey, int i) {
		this.shapeless(RecipeCategory.BUILDING_BLOCKS, itemLike, i).requires(tagKey).group("planks").unlockedBy("has_log", this.has(tagKey)).save(this.output);
	}

	public void planksFromLogs(ItemLike itemLike, TagKey<Item> tagKey, int i) {
		this.shapeless(RecipeCategory.BUILDING_BLOCKS, itemLike, i).requires(tagKey).group("planks").unlockedBy("has_logs", this.has(tagKey)).save(this.output);
	}

	public void woodFromLogs(ItemLike itemLike, ItemLike itemLike2) {
		this.shaped(RecipeCategory.BUILDING_BLOCKS, itemLike, 3)
			.define('#', itemLike2)
			.pattern("##")
			.pattern("##")
			.group("bark")
			.unlockedBy("has_log", this.has(itemLike2))
			.save(this.output);
	}

	public void woodenBoat(ItemLike itemLike, ItemLike itemLike2) {
		this.shaped(RecipeCategory.TRANSPORTATION, itemLike)
			.define('#', itemLike2)
			.pattern("# #")
			.pattern("###")
			.group("boat")
			.unlockedBy("in_water", insideOf(Blocks.WATER))
			.save(this.output);
	}

	public void chestBoat(ItemLike itemLike, ItemLike itemLike2) {
		this.shapeless(RecipeCategory.TRANSPORTATION, itemLike)
			.requires(Blocks.CHEST)
			.requires(itemLike2)
			.group("chest_boat")
			.unlockedBy("has_boat", this.has(ItemTags.BOATS))
			.save(this.output);
	}

	public final RecipeBuilder buttonBuilder(ItemLike itemLike, Ingredient ingredient) {
		return this.shapeless(RecipeCategory.REDSTONE, itemLike).requires(ingredient);
	}

	public RecipeBuilder doorBuilder(ItemLike itemLike, Ingredient ingredient) {
		return this.shaped(RecipeCategory.REDSTONE, itemLike, 3).define('#', ingredient).pattern("##").pattern("##").pattern("##");
	}

	public final RecipeBuilder fenceBuilder(ItemLike itemLike, Ingredient ingredient) {
		int i = itemLike == Blocks.NETHER_BRICK_FENCE ? 6 : 3;
		Item item = itemLike == Blocks.NETHER_BRICK_FENCE ? Items.NETHER_BRICK : Items.STICK;
		return this.shaped(RecipeCategory.DECORATIONS, itemLike, i).define('W', ingredient).define('#', item).pattern("W#W").pattern("W#W");
	}

	public final RecipeBuilder fenceGateBuilder(ItemLike itemLike, Ingredient ingredient) {
		return this.shaped(RecipeCategory.REDSTONE, itemLike).define('#', Items.STICK).define('W', ingredient).pattern("#W#").pattern("#W#");
	}

	public void pressurePlate(ItemLike itemLike, ItemLike itemLike2) {
		this.pressurePlateBuilder(RecipeCategory.REDSTONE, itemLike, Ingredient.of(itemLike2))
			.unlockedBy(getHasName(itemLike2), this.has(itemLike2))
			.save(this.output);
	}

	public final RecipeBuilder pressurePlateBuilder(RecipeCategory recipeCategory, ItemLike itemLike, Ingredient ingredient) {
		return this.shaped(recipeCategory, itemLike).define('#', ingredient).pattern("##");
	}

	public void slab(RecipeCategory recipeCategory, ItemLike itemLike, ItemLike itemLike2) {
		this.slabBuilder(recipeCategory, itemLike, Ingredient.of(itemLike2)).unlockedBy(getHasName(itemLike2), this.has(itemLike2)).save(this.output);
	}

	public void shelf(ItemLike itemLike, ItemLike itemLike2) {
		this.shaped(RecipeCategory.DECORATIONS, itemLike, 6)
			.define('#', itemLike2)
			.pattern("###")
			.pattern("   ")
			.pattern("###")
			.group("shelf")
			.unlockedBy(getHasName(itemLike2), this.has(itemLike2))
			.save(this.output);
	}

	public RecipeBuilder slabBuilder(RecipeCategory recipeCategory, ItemLike itemLike, Ingredient ingredient) {
		return this.shaped(recipeCategory, itemLike, 6).define('#', ingredient).pattern("###");
	}

	public RecipeBuilder stairBuilder(ItemLike itemLike, Ingredient ingredient) {
		return this.shaped(RecipeCategory.BUILDING_BLOCKS, itemLike, 4).define('#', ingredient).pattern("#  ").pattern("## ").pattern("###");
	}

	public RecipeBuilder trapdoorBuilder(ItemLike itemLike, Ingredient ingredient) {
		return this.shaped(RecipeCategory.REDSTONE, itemLike, 2).define('#', ingredient).pattern("###").pattern("###");
	}

	public final RecipeBuilder signBuilder(ItemLike itemLike, Ingredient ingredient) {
		return this.shaped(RecipeCategory.DECORATIONS, itemLike, 3)
			.group("sign")
			.define('#', ingredient)
			.define('X', Items.STICK)
			.pattern("###")
			.pattern("###")
			.pattern(" X ");
	}

	public void hangingSign(ItemLike itemLike, ItemLike itemLike2) {
		this.shaped(RecipeCategory.DECORATIONS, itemLike, 6)
			.group("hanging_sign")
			.define('#', itemLike2)
			.define('X', Items.IRON_CHAIN)
			.pattern("X X")
			.pattern("###")
			.pattern("###")
			.unlockedBy("has_stripped_logs", this.has(itemLike2))
			.save(this.output);
	}

	public void colorItemWithDye(List<Item> list, List<Item> list2, String string, RecipeCategory recipeCategory) {
		this.colorWithDye(list, list2, null, string, recipeCategory);
	}

	public void colorWithDye(List<Item> list, List<Item> list2, @Nullable Item item, String string, RecipeCategory recipeCategory) {
		for (int i = 0; i < list.size(); i++) {
			Item item2 = list.get(i);
			Item item3 = list2.get(i);
			Stream<Item> stream = list2.stream().filter(item2x -> !item2x.equals(item3));
			if (item != null) {
				stream = Stream.concat(stream, Stream.of(item));
			}

			this.shapeless(recipeCategory, item3)
				.requires(item2)
				.requires(Ingredient.of(stream))
				.group(string)
				.unlockedBy("has_needed_dye", this.has(item2))
				.save(this.output, "dye_" + getItemName(item3));
		}
	}

	public void carpet(ItemLike itemLike, ItemLike itemLike2) {
		this.shaped(RecipeCategory.DECORATIONS, itemLike, 3)
			.define('#', itemLike2)
			.pattern("##")
			.group("carpet")
			.unlockedBy(getHasName(itemLike2), this.has(itemLike2))
			.save(this.output);
	}

	public void bedFromPlanksAndWool(ItemLike itemLike, ItemLike itemLike2) {
		this.shaped(RecipeCategory.DECORATIONS, itemLike)
			.define('#', itemLike2)
			.define('X', ItemTags.PLANKS)
			.pattern("###")
			.pattern("XXX")
			.group("bed")
			.unlockedBy(getHasName(itemLike2), this.has(itemLike2))
			.save(this.output);
	}

	public void banner(ItemLike itemLike, ItemLike itemLike2) {
		this.shaped(RecipeCategory.DECORATIONS, itemLike)
			.define('#', itemLike2)
			.define('|', Items.STICK)
			.pattern("###")
			.pattern("###")
			.pattern(" | ")
			.group("banner")
			.unlockedBy(getHasName(itemLike2), this.has(itemLike2))
			.save(this.output);
	}

	public void stainedGlassFromGlassAndDye(ItemLike itemLike, ItemLike itemLike2) {
		this.shaped(RecipeCategory.BUILDING_BLOCKS, itemLike, 8)
			.define('#', Blocks.GLASS)
			.define('X', itemLike2)
			.pattern("###")
			.pattern("#X#")
			.pattern("###")
			.group("stained_glass")
			.unlockedBy("has_glass", this.has(Blocks.GLASS))
			.save(this.output);
	}

	public void dryGhast(ItemLike itemLike) {
		this.shaped(RecipeCategory.BUILDING_BLOCKS, itemLike, 1)
			.define('#', Items.GHAST_TEAR)
			.define('X', Items.SOUL_SAND)
			.pattern("###")
			.pattern("#X#")
			.pattern("###")
			.group("dry_ghast")
			.unlockedBy(getHasName(Items.GHAST_TEAR), this.has(Items.GHAST_TEAR))
			.save(this.output);
	}

	public void harness(ItemLike itemLike, ItemLike itemLike2) {
		this.shaped(RecipeCategory.COMBAT, itemLike)
			.define('#', itemLike2)
			.define('G', Items.GLASS)
			.define('L', Items.LEATHER)
			.pattern("LLL")
			.pattern("G#G")
			.group("harness")
			.unlockedBy("has_dried_ghast", this.has(Blocks.DRIED_GHAST))
			.save(this.output);
	}

	public void stainedGlassPaneFromStainedGlass(ItemLike itemLike, ItemLike itemLike2) {
		this.shaped(RecipeCategory.DECORATIONS, itemLike, 16)
			.define('#', itemLike2)
			.pattern("###")
			.pattern("###")
			.group("stained_glass_pane")
			.unlockedBy("has_glass", this.has(itemLike2))
			.save(this.output);
	}

	public void stainedGlassPaneFromGlassPaneAndDye(ItemLike itemLike, ItemLike itemLike2) {
		this.shaped(RecipeCategory.DECORATIONS, itemLike, 8)
			.define('#', Blocks.GLASS_PANE)
			.define('$', itemLike2)
			.pattern("###")
			.pattern("#$#")
			.pattern("###")
			.group("stained_glass_pane")
			.unlockedBy("has_glass_pane", this.has(Blocks.GLASS_PANE))
			.unlockedBy(getHasName(itemLike2), this.has(itemLike2))
			.save(this.output, getConversionRecipeName(itemLike, Blocks.GLASS_PANE));
	}

	public void coloredTerracottaFromTerracottaAndDye(ItemLike itemLike, ItemLike itemLike2) {
		this.shaped(RecipeCategory.BUILDING_BLOCKS, itemLike, 8)
			.define('#', Blocks.TERRACOTTA)
			.define('X', itemLike2)
			.pattern("###")
			.pattern("#X#")
			.pattern("###")
			.group("stained_terracotta")
			.unlockedBy("has_terracotta", this.has(Blocks.TERRACOTTA))
			.save(this.output);
	}

	public void concretePowder(ItemLike itemLike, ItemLike itemLike2) {
		this.shapeless(RecipeCategory.BUILDING_BLOCKS, itemLike, 8)
			.requires(itemLike2)
			.requires(Blocks.SAND, 4)
			.requires(Blocks.GRAVEL, 4)
			.group("concrete_powder")
			.unlockedBy("has_sand", this.has(Blocks.SAND))
			.unlockedBy("has_gravel", this.has(Blocks.GRAVEL))
			.save(this.output);
	}

	public void candle(ItemLike itemLike, ItemLike itemLike2) {
		this.shapeless(RecipeCategory.DECORATIONS, itemLike)
			.requires(Blocks.CANDLE)
			.requires(itemLike2)
			.group("dyed_candle")
			.unlockedBy(getHasName(itemLike2), this.has(itemLike2))
			.save(this.output);
	}

	public void wall(RecipeCategory recipeCategory, ItemLike itemLike, ItemLike itemLike2) {
		this.wallBuilder(recipeCategory, itemLike, Ingredient.of(itemLike2)).unlockedBy(getHasName(itemLike2), this.has(itemLike2)).save(this.output);
	}

	public final RecipeBuilder wallBuilder(RecipeCategory recipeCategory, ItemLike itemLike, Ingredient ingredient) {
		return this.shaped(recipeCategory, itemLike, 6).define('#', ingredient).pattern("###").pattern("###");
	}

	public void polished(RecipeCategory recipeCategory, ItemLike itemLike, ItemLike itemLike2) {
		this.polishedBuilder(recipeCategory, itemLike, Ingredient.of(itemLike2)).unlockedBy(getHasName(itemLike2), this.has(itemLike2)).save(this.output);
	}

	public final RecipeBuilder polishedBuilder(RecipeCategory recipeCategory, ItemLike itemLike, Ingredient ingredient) {
		return this.shaped(recipeCategory, itemLike, 4).define('S', ingredient).pattern("SS").pattern("SS");
	}

	public void cut(RecipeCategory recipeCategory, ItemLike itemLike, ItemLike itemLike2) {
		this.cutBuilder(recipeCategory, itemLike, Ingredient.of(itemLike2)).unlockedBy(getHasName(itemLike2), this.has(itemLike2)).save(this.output);
	}

	public final ShapedRecipeBuilder cutBuilder(RecipeCategory recipeCategory, ItemLike itemLike, Ingredient ingredient) {
		return this.shaped(recipeCategory, itemLike, 4).define('#', ingredient).pattern("##").pattern("##");
	}

	public void chiseled(RecipeCategory recipeCategory, ItemLike itemLike, ItemLike itemLike2) {
		this.chiseledBuilder(recipeCategory, itemLike, Ingredient.of(itemLike2)).unlockedBy(getHasName(itemLike2), this.has(itemLike2)).save(this.output);
	}

	public void mosaicBuilder(RecipeCategory recipeCategory, ItemLike itemLike, ItemLike itemLike2) {
		this.shaped(recipeCategory, itemLike)
			.define('#', itemLike2)
			.pattern("#")
			.pattern("#")
			.unlockedBy(getHasName(itemLike2), this.has(itemLike2))
			.save(this.output);
	}

	public ShapedRecipeBuilder chiseledBuilder(RecipeCategory recipeCategory, ItemLike itemLike, Ingredient ingredient) {
		return this.shaped(recipeCategory, itemLike).define('#', ingredient).pattern("#").pattern("#");
	}

	public void stonecutterResultFromBase(RecipeCategory recipeCategory, ItemLike itemLike, ItemLike itemLike2) {
		this.stonecutterResultFromBase(recipeCategory, itemLike, itemLike2, 1);
	}

	public void stonecutterResultFromBase(RecipeCategory recipeCategory, ItemLike itemLike, ItemLike itemLike2, int i) {
		SingleItemRecipeBuilder.stonecutting(Ingredient.of(itemLike2), recipeCategory, itemLike, i)
			.unlockedBy(getHasName(itemLike2), this.has(itemLike2))
			.save(this.output, getConversionRecipeName(itemLike, itemLike2) + "_stonecutting");
	}

	public final void smeltingResultFromBase(ItemLike itemLike, ItemLike itemLike2) {
		SimpleCookingRecipeBuilder.smelting(Ingredient.of(itemLike2), RecipeCategory.BUILDING_BLOCKS, itemLike, 0.1F, 200)
			.unlockedBy(getHasName(itemLike2), this.has(itemLike2))
			.save(this.output);
	}

	public void nineBlockStorageRecipes(RecipeCategory recipeCategory, ItemLike itemLike, RecipeCategory recipeCategory2, ItemLike itemLike2) {
		this.nineBlockStorageRecipes(recipeCategory, itemLike, recipeCategory2, itemLike2, getSimpleRecipeName(itemLike2), null, getSimpleRecipeName(itemLike), null);
	}

	public void nineBlockStorageRecipesWithCustomPacking(
		RecipeCategory recipeCategory, ItemLike itemLike, RecipeCategory recipeCategory2, ItemLike itemLike2, String string, String string2
	) {
		this.nineBlockStorageRecipes(recipeCategory, itemLike, recipeCategory2, itemLike2, string, string2, getSimpleRecipeName(itemLike), null);
	}

	public void nineBlockStorageRecipesRecipesWithCustomUnpacking(
		RecipeCategory recipeCategory, ItemLike itemLike, RecipeCategory recipeCategory2, ItemLike itemLike2, String string, String string2
	) {
		this.nineBlockStorageRecipes(recipeCategory, itemLike, recipeCategory2, itemLike2, getSimpleRecipeName(itemLike2), null, string, string2);
	}

	public final void nineBlockStorageRecipes(
		RecipeCategory recipeCategory,
		ItemLike itemLike,
		RecipeCategory recipeCategory2,
		ItemLike itemLike2,
		String string,
		@Nullable String string2,
		String string3,
		@Nullable String string4
	) {
		this.shapeless(recipeCategory, itemLike, 9)
			.requires(itemLike2)
			.group(string4)
			.unlockedBy(getHasName(itemLike2), this.has(itemLike2))
			.save(this.output, ResourceKey.create(Registries.RECIPE, Identifier.parse(string3)));
		this.shaped(recipeCategory2, itemLike2)
			.define('#', itemLike)
			.pattern("###")
			.pattern("###")
			.pattern("###")
			.group(string2)
			.unlockedBy(getHasName(itemLike), this.has(itemLike))
			.save(this.output, ResourceKey.create(Registries.RECIPE, Identifier.parse(string)));
	}

	public void copySmithingTemplate(ItemLike itemLike, ItemLike itemLike2) {
		this.shaped(RecipeCategory.MISC, itemLike, 2)
			.define('#', Items.DIAMOND)
			.define('C', itemLike2)
			.define('S', itemLike)
			.pattern("#S#")
			.pattern("#C#")
			.pattern("###")
			.unlockedBy(getHasName(itemLike), this.has(itemLike))
			.save(this.output);
	}

	public void copySmithingTemplate(ItemLike itemLike, Ingredient ingredient) {
		this.shaped(RecipeCategory.MISC, itemLike, 2)
			.define('#', Items.DIAMOND)
			.define('C', ingredient)
			.define('S', itemLike)
			.pattern("#S#")
			.pattern("#C#")
			.pattern("###")
			.unlockedBy(getHasName(itemLike), this.has(itemLike))
			.save(this.output);
	}

	public <T extends AbstractCookingRecipe> void cookRecipes(String string, RecipeSerializer<T> recipeSerializer, AbstractCookingRecipe.Factory<T> factory, int i) {
		this.simpleCookingRecipe(string, recipeSerializer, factory, i, Items.BEEF, Items.COOKED_BEEF, 0.35F);
		this.simpleCookingRecipe(string, recipeSerializer, factory, i, Items.CHICKEN, Items.COOKED_CHICKEN, 0.35F);
		this.simpleCookingRecipe(string, recipeSerializer, factory, i, Items.COD, Items.COOKED_COD, 0.35F);
		this.simpleCookingRecipe(string, recipeSerializer, factory, i, Items.KELP, Items.DRIED_KELP, 0.1F);
		this.simpleCookingRecipe(string, recipeSerializer, factory, i, Items.SALMON, Items.COOKED_SALMON, 0.35F);
		this.simpleCookingRecipe(string, recipeSerializer, factory, i, Items.MUTTON, Items.COOKED_MUTTON, 0.35F);
		this.simpleCookingRecipe(string, recipeSerializer, factory, i, Items.PORKCHOP, Items.COOKED_PORKCHOP, 0.35F);
		this.simpleCookingRecipe(string, recipeSerializer, factory, i, Items.POTATO, Items.BAKED_POTATO, 0.35F);
		this.simpleCookingRecipe(string, recipeSerializer, factory, i, Items.RABBIT, Items.COOKED_RABBIT, 0.35F);
	}

	public final <T extends AbstractCookingRecipe> void simpleCookingRecipe(
		String string, RecipeSerializer<T> recipeSerializer, AbstractCookingRecipe.Factory<T> factory, int i, ItemLike itemLike, ItemLike itemLike2, float f
	) {
		SimpleCookingRecipeBuilder.generic(Ingredient.of(itemLike), RecipeCategory.FOOD, itemLike2, f, i, recipeSerializer, factory)
			.unlockedBy(getHasName(itemLike), this.has(itemLike))
			.save(this.output, getItemName(itemLike2) + "_from_" + string);
	}

	public void waxRecipes(FeatureFlagSet featureFlagSet) {
		HoneycombItem.WAXABLES
			.get()
			.forEach(
				(block, block2) -> {
					if (block2.requiredFeatures().isSubsetOf(featureFlagSet)) {
						Pair<RecipeCategory, String> pair = HoneycombItem.WAXED_RECIPES.getOrDefault(block2, Pair.of(RecipeCategory.BUILDING_BLOCKS, getItemName(block2)));
						RecipeCategory recipeCategory = pair.getFirst();
						String string = pair.getSecond();
						this.shapeless(recipeCategory, block2)
							.requires(block)
							.requires(Items.HONEYCOMB)
							.group(string)
							.unlockedBy(getHasName(block), this.has(block))
							.save(this.output, getConversionRecipeName(block2, Items.HONEYCOMB));
					}
				}
			);
	}

	public void grate(Block block, Block block2) {
		this.shaped(RecipeCategory.BUILDING_BLOCKS, block, 4)
			.define('M', block2)
			.pattern(" M ")
			.pattern("M M")
			.pattern(" M ")
			.group(getItemName(block))
			.unlockedBy(getHasName(block2), this.has(block2))
			.save(this.output);
	}

	public void copperBulb(Block block, Block block2) {
		this.shaped(RecipeCategory.REDSTONE, block, 4)
			.define('C', block2)
			.define('R', Items.REDSTONE)
			.define('B', Items.BLAZE_ROD)
			.pattern(" C ")
			.pattern("CBC")
			.pattern(" R ")
			.unlockedBy(getHasName(block2), this.has(block2))
			.group(getItemName(block))
			.save(this.output);
	}

	public void waxedChiseled(Block block, Block block2) {
		this.shaped(RecipeCategory.BUILDING_BLOCKS, block)
			.define('M', block2)
			.pattern(" M ")
			.pattern(" M ")
			.group(getItemName(block))
			.unlockedBy(getHasName(block2), this.has(block2))
			.save(this.output);
	}

	public void suspiciousStew(Item item, SuspiciousEffectHolder suspiciousEffectHolder) {
		ItemStack itemStack = new ItemStack(
			Items.SUSPICIOUS_STEW.builtInRegistryHolder(),
			1,
			DataComponentPatch.builder().set(DataComponents.SUSPICIOUS_STEW_EFFECTS, suspiciousEffectHolder.getSuspiciousEffects()).build()
		);
		this.shapeless(RecipeCategory.FOOD, itemStack)
			.requires(Items.BOWL)
			.requires(Items.BROWN_MUSHROOM)
			.requires(Items.RED_MUSHROOM)
			.requires(item)
			.group("suspicious_stew")
			.unlockedBy(getHasName(item), this.has(item))
			.save(this.output, getItemName(itemStack.getItem()) + "_from_" + getItemName(item));
	}

	public void generateRecipes(BlockFamily blockFamily, FeatureFlagSet featureFlagSet) {
		blockFamily.getVariants()
			.forEach(
				(variant, block) -> {
					if (block.requiredFeatures().isSubsetOf(featureFlagSet)) {
						RecipeProvider.FamilyRecipeProvider familyRecipeProvider = SHAPE_BUILDERS.get(variant);
						ItemLike itemLike = this.getBaseBlock(blockFamily, variant);
						if (familyRecipeProvider != null) {
							RecipeBuilder recipeBuilder = familyRecipeProvider.create(this, block, itemLike);
							blockFamily.getRecipeGroupPrefix()
								.ifPresent(string -> recipeBuilder.group(string + (variant == BlockFamily.Variant.CUT ? "" : "_" + variant.getRecipeGroup())));
							recipeBuilder.unlockedBy(blockFamily.getRecipeUnlockedBy().orElseGet(() -> getHasName(itemLike)), this.has(itemLike));
							recipeBuilder.save(this.output);
						}

						if (variant == BlockFamily.Variant.CRACKED) {
							this.smeltingResultFromBase(block, itemLike);
						}
					}
				}
			);
	}

	public final Block getBaseBlock(BlockFamily blockFamily, BlockFamily.Variant variant) {
		if (variant == BlockFamily.Variant.CHISELED) {
			if (!blockFamily.getVariants().containsKey(BlockFamily.Variant.SLAB)) {
				throw new IllegalStateException("Slab is not defined for the family.");
			} else {
				return blockFamily.get(BlockFamily.Variant.SLAB);
			}
		} else {
			return blockFamily.getBaseBlock();
		}
	}

	public static Criterion<EnterBlockTrigger.TriggerInstance> insideOf(Block block) {
		return CriteriaTriggers.ENTER_BLOCK
			.createCriterion(new EnterBlockTrigger.TriggerInstance(Optional.empty(), Optional.of(block.builtInRegistryHolder()), Optional.empty()));
	}

	public final Criterion<InventoryChangeTrigger.TriggerInstance> has(MinMaxBounds.Ints ints, ItemLike itemLike) {
		return inventoryTrigger(ItemPredicate.Builder.item().of(this.items, itemLike).withCount(ints));
	}

	public Criterion<InventoryChangeTrigger.TriggerInstance> has(ItemLike itemLike) {
		return inventoryTrigger(ItemPredicate.Builder.item().of(this.items, itemLike));
	}

	public Criterion<InventoryChangeTrigger.TriggerInstance> has(TagKey<Item> tagKey) {
		return inventoryTrigger(ItemPredicate.Builder.item().of(this.items, tagKey));
	}

	public static Criterion<InventoryChangeTrigger.TriggerInstance> inventoryTrigger(ItemPredicate.Builder... builders) {
		return inventoryTrigger(Arrays.stream(builders).map(ItemPredicate.Builder::build).toArray(ItemPredicate[]::new));
	}

	public static Criterion<InventoryChangeTrigger.TriggerInstance> inventoryTrigger(ItemPredicate... itemPredicates) {
		return CriteriaTriggers.INVENTORY_CHANGED
			.createCriterion(new InventoryChangeTrigger.TriggerInstance(Optional.empty(), InventoryChangeTrigger.TriggerInstance.Slots.ANY, List.of(itemPredicates)));
	}

	public static String getHasName(ItemLike itemLike) {
		return "has_" + getItemName(itemLike);
	}

	public static String getItemName(ItemLike itemLike) {
		return BuiltInRegistries.ITEM.getKey(itemLike.asItem()).getPath();
	}

	public static String getSimpleRecipeName(ItemLike itemLike) {
		return getItemName(itemLike);
	}

	public static String getConversionRecipeName(ItemLike itemLike, ItemLike itemLike2) {
		return getItemName(itemLike) + "_from_" + getItemName(itemLike2);
	}

	public static String getSmeltingRecipeName(ItemLike itemLike) {
		return getItemName(itemLike) + "_from_smelting";
	}

	public static String getBlastingRecipeName(ItemLike itemLike) {
		return getItemName(itemLike) + "_from_blasting";
	}

	public Ingredient tag(TagKey<Item> tagKey) {
		return Ingredient.of(this.items.getOrThrow(tagKey));
	}

	public ShapedRecipeBuilder shaped(RecipeCategory recipeCategory, ItemLike itemLike) {
		return ShapedRecipeBuilder.shaped(this.items, recipeCategory, itemLike);
	}

	public ShapedRecipeBuilder shaped(RecipeCategory recipeCategory, ItemLike itemLike, int i) {
		return ShapedRecipeBuilder.shaped(this.items, recipeCategory, itemLike, i);
	}

	public ShapelessRecipeBuilder shapeless(RecipeCategory recipeCategory, ItemStack itemStack) {
		return ShapelessRecipeBuilder.shapeless(this.items, recipeCategory, itemStack);
	}

	public ShapelessRecipeBuilder shapeless(RecipeCategory recipeCategory, ItemLike itemLike) {
		return ShapelessRecipeBuilder.shapeless(this.items, recipeCategory, itemLike);
	}

	public ShapelessRecipeBuilder shapeless(RecipeCategory recipeCategory, ItemLike itemLike, int i) {
		return ShapelessRecipeBuilder.shapeless(this.items, recipeCategory, itemLike, i);
	}

	@FunctionalInterface
	interface FamilyRecipeProvider {
		RecipeBuilder create(RecipeProvider recipeProvider, ItemLike itemLike, ItemLike itemLike2);
	}

	public abstract static class Runner implements DataProvider {
		private final PackOutput packOutput;
		private final CompletableFuture<HolderLookup.Provider> registries;

		protected Runner(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> completableFuture) {
			this.packOutput = packOutput;
			this.registries = completableFuture;
		}

		@Override
		public CompletableFuture<?> run(CachedOutput cachedOutput) {
			return this.registries
				.thenCompose(
					provider -> {
						final PackOutput.PathProvider pathProvider = this.packOutput.createRegistryElementsPathProvider(Registries.RECIPE);
						final PackOutput.PathProvider pathProvider2 = this.packOutput.createRegistryElementsPathProvider(Registries.ADVANCEMENT);
						final Set<ResourceKey<Recipe<?>>> set = Sets.newHashSet();
						final List<CompletableFuture<?>> list = new ArrayList<>();
						RecipeOutput recipeOutput = new RecipeOutput() {
							@Override
							public void accept(ResourceKey<Recipe<?>> resourceKey, Recipe<?> recipe, @Nullable AdvancementHolder advancementHolder) {
								if (!set.add(resourceKey)) {
									throw new IllegalStateException("Duplicate recipe " + resourceKey.identifier());
								}

								this.saveRecipe(resourceKey, recipe);
								if (advancementHolder != null) {
									this.saveAdvancement(advancementHolder);
								}
							}

							@Override
							public Advancement.Builder advancement() {
								return Advancement.Builder.recipeAdvancement().parent(RecipeBuilder.ROOT_RECIPE_ADVANCEMENT);
							}

							@Override
							public void includeRootAdvancement() {
								AdvancementHolder advancementHolder = Advancement.Builder.recipeAdvancement()
									.addCriterion("impossible", CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance()))
									.build(RecipeBuilder.ROOT_RECIPE_ADVANCEMENT);
								this.saveAdvancement(advancementHolder);
							}

							private void saveRecipe(ResourceKey<Recipe<?>> resourceKey, Recipe<?> recipe) {
								list.add(DataProvider.saveStable(cachedOutput, provider, Recipe.CODEC, recipe, pathProvider.json(resourceKey.identifier())));
							}

							private void saveAdvancement(AdvancementHolder advancementHolder) {
								list.add(DataProvider.saveStable(cachedOutput, provider, Advancement.CODEC, advancementHolder.value(), pathProvider2.json(advancementHolder.id())));
							}
						};
						this.createRecipeProvider(provider, recipeOutput).buildRecipes();
						return CompletableFuture.allOf(list.toArray(CompletableFuture[]::new));
					}
				);
		}

		protected abstract RecipeProvider createRecipeProvider(HolderLookup.Provider provider, RecipeOutput recipeOutput);
	}
}
