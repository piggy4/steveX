package net.minecraft.world.item.crafting;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PlacementInfo {
	public static final int EMPTY_SLOT = -1;
	public static final PlacementInfo NOT_PLACEABLE = new PlacementInfo(List.of(), IntList.of());
	private final List<Ingredient> ingredients;
	private final IntList slotsToIngredientIndex;

	private PlacementInfo(List<Ingredient> list, IntList intList) {
		this.ingredients = list;
		this.slotsToIngredientIndex = intList;
	}

	public static PlacementInfo create(Ingredient ingredient) {
		return ingredient.isEmpty() ? NOT_PLACEABLE : new PlacementInfo(List.of(ingredient), IntList.of(0));
	}

	public static PlacementInfo createFromOptionals(List<Optional<Ingredient>> list) {
		int i = list.size();
		List<Ingredient> list2 = new ArrayList<>(i);
		IntList intList = new IntArrayList(i);
		int j = 0;

		for (Optional<Ingredient> optional : list) {
			if (optional.isPresent()) {
				Ingredient ingredient = optional.get();
				if (ingredient.isEmpty()) {
					return NOT_PLACEABLE;
				}

				list2.add(ingredient);
				intList.add(j++);
			} else {
				intList.add(-1);
			}
		}

		return new PlacementInfo(list2, intList);
	}

	public static PlacementInfo create(List<Ingredient> list) {
		int i = list.size();
		IntList intList = new IntArrayList(i);

		for (int j = 0; j < i; j++) {
			Ingredient ingredient = list.get(j);
			if (ingredient.isEmpty()) {
				return NOT_PLACEABLE;
			}

			intList.add(j);
		}

		return new PlacementInfo(list, intList);
	}

	public IntList slotsToIngredientIndex() {
		return this.slotsToIngredientIndex;
	}

	public List<Ingredient> ingredients() {
		return this.ingredients;
	}

	public boolean isImpossibleToPlace() {
		return this.slotsToIngredientIndex.isEmpty();
	}
}
