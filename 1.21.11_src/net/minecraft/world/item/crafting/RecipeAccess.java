package net.minecraft.world.item.crafting;

import net.fabricmc.fabric.api.recipe.v1.FabricRecipeManager;
import net.minecraft.resources.ResourceKey;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.recipe.v1.FabricRecipeManager} injected by mod fabric-recipe-api-v1</p>
 */
public interface RecipeAccess extends FabricRecipeManager {
	RecipePropertySet propertySet(ResourceKey<RecipePropertySet> resourceKey);

	SelectableRecipe.SingleInputSet<StonecutterRecipe> stonecutterRecipes();
}
