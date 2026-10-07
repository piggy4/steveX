package net.minecraft.world.entity.player;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import org.jspecify.annotations.Nullable;

public class StackedItemContents {
	private final StackedContents<Holder<Item>> raw = new StackedContents<>();

	public void accountSimpleStack(ItemStack itemStack) {
		if (Inventory.isUsableForCrafting(itemStack)) {
			this.accountStack(itemStack);
		}
	}

	public void accountStack(ItemStack itemStack) {
		this.accountStack(itemStack, itemStack.getMaxStackSize());
	}

	public void accountStack(ItemStack itemStack, int i) {
		if (!itemStack.isEmpty()) {
			int j = Math.min(i, itemStack.getCount());
			this.raw.account(itemStack.getItemHolder(), j);
		}
	}

	public boolean canCraft(Recipe<?> recipe, StackedContents.@Nullable Output<Holder<Item>> output) {
		return this.canCraft(recipe, 1, output);
	}

	public boolean canCraft(Recipe<?> recipe, int i, StackedContents.@Nullable Output<Holder<Item>> output) {
		PlacementInfo placementInfo = recipe.placementInfo();
		return placementInfo.isImpossibleToPlace() ? false : this.canCraft(placementInfo.ingredients(), i, output);
	}

	public boolean canCraft(List<? extends StackedContents.IngredientInfo<Holder<Item>>> list, StackedContents.@Nullable Output<Holder<Item>> output) {
		return this.canCraft(list, 1, output);
	}

	private boolean canCraft(List<? extends StackedContents.IngredientInfo<Holder<Item>>> list, int i, StackedContents.@Nullable Output<Holder<Item>> output) {
		return this.raw.tryPick(list, i, output);
	}

	public int getBiggestCraftableStack(Recipe<?> recipe, StackedContents.@Nullable Output<Holder<Item>> output) {
		return this.getBiggestCraftableStack(recipe, Integer.MAX_VALUE, output);
	}

	public int getBiggestCraftableStack(Recipe<?> recipe, int i, StackedContents.@Nullable Output<Holder<Item>> output) {
		return this.raw.tryPickAll(recipe.placementInfo().ingredients(), i, output);
	}

	public void clear() {
		this.raw.clear();
	}
}
