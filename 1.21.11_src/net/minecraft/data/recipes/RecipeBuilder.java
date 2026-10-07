package net.minecraft.data.recipes;

import net.minecraft.advancements.Criterion;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.Nullable;

public interface RecipeBuilder {
	Identifier ROOT_RECIPE_ADVANCEMENT = Identifier.withDefaultNamespace("recipes/root");

	RecipeBuilder unlockedBy(String string, Criterion<?> criterion);

	RecipeBuilder group(@Nullable String string);

	Item getResult();

	void save(RecipeOutput recipeOutput, ResourceKey<Recipe<?>> resourceKey);

	default void save(RecipeOutput recipeOutput) {
		this.save(recipeOutput, ResourceKey.create(Registries.RECIPE, getDefaultRecipeId(this.getResult())));
	}

	default void save(RecipeOutput recipeOutput, String string) {
		Identifier identifier = getDefaultRecipeId(this.getResult());
		Identifier identifier2 = Identifier.parse(string);
		if (identifier2.equals(identifier)) {
			throw new IllegalStateException("Recipe " + string + " should remove its 'save' argument as it is equal to default one");
		}

		this.save(recipeOutput, ResourceKey.create(Registries.RECIPE, identifier2));
	}

	static Identifier getDefaultRecipeId(ItemLike itemLike) {
		return BuiltInRegistries.ITEM.getKey(itemLike.asItem());
	}

	static CraftingBookCategory determineBookCategory(RecipeCategory recipeCategory) {
		return switch (recipeCategory) {
			case BUILDING_BLOCKS -> CraftingBookCategory.BUILDING;
			case TOOLS, COMBAT -> CraftingBookCategory.EQUIPMENT;
			case REDSTONE -> CraftingBookCategory.REDSTONE;
			default -> CraftingBookCategory.MISC;
		};
	}
}
