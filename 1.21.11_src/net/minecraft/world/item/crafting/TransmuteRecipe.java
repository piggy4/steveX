package net.minecraft.world.item.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class TransmuteRecipe implements CraftingRecipe {
	final String group;
	final CraftingBookCategory category;
	final Ingredient input;
	final Ingredient material;
	final TransmuteResult result;
	private @Nullable PlacementInfo placementInfo;

	public TransmuteRecipe(
		String string, CraftingBookCategory craftingBookCategory, Ingredient ingredient, Ingredient ingredient2, TransmuteResult transmuteResult
	) {
		this.group = string;
		this.category = craftingBookCategory;
		this.input = ingredient;
		this.material = ingredient2;
		this.result = transmuteResult;
	}

	public boolean matches(CraftingInput craftingInput, Level level) {
		if (craftingInput.ingredientCount() != 2) {
			return false;
		}

		boolean bl = false;
		boolean bl2 = false;

		for (int i = 0; i < craftingInput.size(); i++) {
			ItemStack itemStack = craftingInput.getItem(i);
			if (!itemStack.isEmpty()) {
				if (!bl && this.input.test(itemStack)) {
					if (this.result.isResultUnchanged(itemStack)) {
						return false;
					}

					bl = true;
				} else {
					if (bl2 || !this.material.test(itemStack)) {
						return false;
					}

					bl2 = true;
				}
			}
		}

		return bl && bl2;
	}

	public ItemStack assemble(CraftingInput craftingInput, HolderLookup.Provider provider) {
		for (int i = 0; i < craftingInput.size(); i++) {
			ItemStack itemStack = craftingInput.getItem(i);
			if (!itemStack.isEmpty() && this.input.test(itemStack)) {
				return this.result.apply(itemStack);
			}
		}

		return ItemStack.EMPTY;
	}

	@Override
	public List<RecipeDisplay> display() {
		return List.of(
			new ShapelessCraftingRecipeDisplay(
				List.of(this.input.display(), this.material.display()), this.result.display(), new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)
			)
		);
	}

	@Override
	public RecipeSerializer<TransmuteRecipe> getSerializer() {
		return RecipeSerializer.TRANSMUTE;
	}

	@Override
	public String group() {
		return this.group;
	}

	@Override
	public PlacementInfo placementInfo() {
		if (this.placementInfo == null) {
			this.placementInfo = PlacementInfo.create(List.of(this.input, this.material));
		}

		return this.placementInfo;
	}

	@Override
	public CraftingBookCategory category() {
		return this.category;
	}

	public static class Serializer implements RecipeSerializer<TransmuteRecipe> {
		private static final MapCodec<TransmuteRecipe> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.STRING.optionalFieldOf("group", "").forGetter(transmuteRecipe -> transmuteRecipe.group),
					CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(transmuteRecipe -> transmuteRecipe.category),
					Ingredient.CODEC.fieldOf("input").forGetter(transmuteRecipe -> transmuteRecipe.input),
					Ingredient.CODEC.fieldOf("material").forGetter(transmuteRecipe -> transmuteRecipe.material),
					TransmuteResult.CODEC.fieldOf("result").forGetter(transmuteRecipe -> transmuteRecipe.result)
				)
				.apply(instance, TransmuteRecipe::new)
		);
		public static final StreamCodec<RegistryFriendlyByteBuf, TransmuteRecipe> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8,
			transmuteRecipe -> transmuteRecipe.group,
			CraftingBookCategory.STREAM_CODEC,
			transmuteRecipe -> transmuteRecipe.category,
			Ingredient.CONTENTS_STREAM_CODEC,
			transmuteRecipe -> transmuteRecipe.input,
			Ingredient.CONTENTS_STREAM_CODEC,
			transmuteRecipe -> transmuteRecipe.material,
			TransmuteResult.STREAM_CODEC,
			transmuteRecipe -> transmuteRecipe.result,
			TransmuteRecipe::new
		);

		@Override
		public MapCodec<TransmuteRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, TransmuteRecipe> streamCodec() {
			return STREAM_CODEC;
		}
	}
}
