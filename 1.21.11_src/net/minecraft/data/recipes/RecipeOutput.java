package net.minecraft.data.recipes;

import net.fabricmc.fabric.api.datagen.v1.recipe.FabricRecipeExporter;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import org.jspecify.annotations.Nullable;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.datagen.v1.recipe.FabricRecipeExporter} injected by mod fabric-data-generation-api-v1</p>
 */
public interface RecipeOutput extends FabricRecipeExporter {
	void accept(ResourceKey<Recipe<?>> resourceKey, Recipe<?> recipe, @Nullable AdvancementHolder advancementHolder);

	Advancement.Builder advancement();

	void includeRootAdvancement();
}
