package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import org.jspecify.annotations.Nullable;

public class SmithingTransformRecipe implements SmithingRecipe {
	final Optional<Ingredient> template;
	final Ingredient base;
	final Optional<Ingredient> addition;
	final TransmuteResult result;
	private @Nullable PlacementInfo placementInfo;

	public SmithingTransformRecipe(Optional<Ingredient> optional, Ingredient ingredient, Optional<Ingredient> optional2, TransmuteResult transmuteResult) {
		this.template = optional;
		this.base = ingredient;
		this.addition = optional2;
		this.result = transmuteResult;
	}

	public ItemStack assemble(SmithingRecipeInput smithingRecipeInput, HolderLookup.Provider provider) {
		return this.result.apply(smithingRecipeInput.base());
	}

	@Override
	public Optional<Ingredient> templateIngredient() {
		return this.template;
	}

	@Override
	public Ingredient baseIngredient() {
		return this.base;
	}

	@Override
	public Optional<Ingredient> additionIngredient() {
		return this.addition;
	}

	@Override
	public RecipeSerializer<SmithingTransformRecipe> getSerializer() {
		return RecipeSerializer.SMITHING_TRANSFORM;
	}

	@Override
	public PlacementInfo placementInfo() {
		if (this.placementInfo == null) {
			this.placementInfo = PlacementInfo.createFromOptionals(List.of(this.template, Optional.of(this.base), this.addition));
		}

		return this.placementInfo;
	}

	@Override
	public List<RecipeDisplay> display() {
		return List.of(
			new SmithingRecipeDisplay(
				Ingredient.optionalIngredientToDisplay(this.template),
				this.base.display(),
				Ingredient.optionalIngredientToDisplay(this.addition),
				this.result.display(),
				new SlotDisplay.ItemSlotDisplay(Items.SMITHING_TABLE)
			)
		);
	}

	public static class Serializer implements RecipeSerializer<SmithingTransformRecipe> {
		private static final MapCodec<SmithingTransformRecipe> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Ingredient.CODEC.optionalFieldOf("template").forGetter(smithingTransformRecipe -> smithingTransformRecipe.template),
					Ingredient.CODEC.fieldOf("base").forGetter(smithingTransformRecipe -> smithingTransformRecipe.base),
					Ingredient.CODEC.optionalFieldOf("addition").forGetter(smithingTransformRecipe -> smithingTransformRecipe.addition),
					TransmuteResult.CODEC.fieldOf("result").forGetter(smithingTransformRecipe -> smithingTransformRecipe.result)
				)
				.apply(instance, SmithingTransformRecipe::new)
		);
		public static final StreamCodec<RegistryFriendlyByteBuf, SmithingTransformRecipe> STREAM_CODEC = StreamCodec.composite(
			Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC,
			smithingTransformRecipe -> smithingTransformRecipe.template,
			Ingredient.CONTENTS_STREAM_CODEC,
			smithingTransformRecipe -> smithingTransformRecipe.base,
			Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC,
			smithingTransformRecipe -> smithingTransformRecipe.addition,
			TransmuteResult.STREAM_CODEC,
			smithingTransformRecipe -> smithingTransformRecipe.result,
			SmithingTransformRecipe::new
		);

		@Override
		public MapCodec<SmithingTransformRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, SmithingTransformRecipe> streamCodec() {
			return STREAM_CODEC;
		}
	}
}
