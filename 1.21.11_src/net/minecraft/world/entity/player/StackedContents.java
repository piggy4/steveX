package net.minecraft.world.entity.player;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectIterable;
import it.unimi.dsi.fastutil.objects.Reference2IntMaps;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap.Entry;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.jspecify.annotations.Nullable;

public class StackedContents<T> {
	public final Reference2IntOpenHashMap<T> amounts = new Reference2IntOpenHashMap<>();

	boolean hasAtLeast(T object, int i) {
		return this.amounts.getInt(object) >= i;
	}

	void take(T object, int i) {
		int j = this.amounts.addTo(object, -i);
		if (j < i) {
			throw new IllegalStateException("Took " + i + " items, but only had " + j);
		}
	}

	void put(T object, int i) {
		this.amounts.addTo(object, i);
	}

	public boolean tryPick(List<? extends StackedContents.IngredientInfo<T>> list, int i, StackedContents.@Nullable Output<T> output) {
		return new StackedContents.RecipePicker(list).tryPick(i, output);
	}

	public int tryPickAll(List<? extends StackedContents.IngredientInfo<T>> list, int i, StackedContents.@Nullable Output<T> output) {
		return new StackedContents.RecipePicker(list).tryPickAll(i, output);
	}

	public void clear() {
		this.amounts.clear();
	}

	public void account(T object, int i) {
		this.put(object, i);
	}

	List<T> getUniqueAvailableIngredientItems(Iterable<? extends StackedContents.IngredientInfo<T>> iterable) {
		List<T> list = new ArrayList<>();

		for (Entry<T> entry : Reference2IntMaps.fastIterable(this.amounts)) {
			if (entry.getIntValue() > 0 && anyIngredientMatches(iterable, entry.getKey())) {
				list.add(entry.getKey());
			}
		}

		return list;
	}

	private static <T> boolean anyIngredientMatches(Iterable<? extends StackedContents.IngredientInfo<T>> iterable, T object) {
		for (StackedContents.IngredientInfo<T> ingredientInfo : iterable) {
			if (ingredientInfo.acceptsItem(object)) {
				return true;
			}
		}

		return false;
	}

	@VisibleForTesting
	public int getResultUpperBound(List<? extends StackedContents.IngredientInfo<T>> list) {
		int i = Integer.MAX_VALUE;
		ObjectIterable<Entry<T>> objectIterable = Reference2IntMaps.fastIterable(this.amounts);

		label31:
		for (StackedContents.IngredientInfo<T> ingredientInfo : list) {
			int j = 0;

			for (Entry<T> entry : objectIterable) {
				int k = entry.getIntValue();
				if (k > j) {
					if (ingredientInfo.acceptsItem(entry.getKey())) {
						j = k;
					}

					if (j >= i) {
						continue label31;
					}
				}
			}

			i = j;
			if (i == 0) {
				break;
			}
		}

		return i;
	}

	@FunctionalInterface
	public interface IngredientInfo<T> {
		boolean acceptsItem(T object);
	}

	@FunctionalInterface
	public interface Output<T> {
		void accept(T object);
	}

	class RecipePicker {
		private final List<? extends StackedContents.IngredientInfo<T>> ingredients;
		private final int ingredientCount;
		private final List<T> items;
		private final int itemCount;
		private final BitSet data;
		private final IntList path = new IntArrayList();

		public RecipePicker(final List<? extends StackedContents.IngredientInfo<T>> list) {
			this.ingredients = list;
			this.ingredientCount = list.size();
			this.items = StackedContents.this.getUniqueAvailableIngredientItems(list);
			this.itemCount = this.items.size();
			this.data = new BitSet(this.visitedIngredientCount() + this.visitedItemCount() + this.satisfiedCount() + this.connectionCount() + this.residualCount());
			this.setInitialConnections();
		}

		private void setInitialConnections() {
			for (int i = 0; i < this.ingredientCount; i++) {
				StackedContents.IngredientInfo<T> ingredientInfo = (StackedContents.IngredientInfo<T>)this.ingredients.get(i);

				for (int j = 0; j < this.itemCount; j++) {
					if (ingredientInfo.acceptsItem(this.items.get(j))) {
						this.setConnection(j, i);
					}
				}
			}
		}

		public boolean tryPick(int i, StackedContents.@Nullable Output<T> output) {
			if (i <= 0) {
				return true;
			}

			int j = 0;

			while (true) {
				IntList intList = this.tryAssigningNewItem(i);
				if (intList == null) {
					boolean bl = j == this.ingredientCount;
					boolean bl2 = bl && output != null;
					this.clearAllVisited();
					this.clearSatisfied();

					for (int l = 0; l < this.ingredientCount; l++) {
						for (int m = 0; m < this.itemCount; m++) {
							if (this.isAssigned(m, l)) {
								this.unassign(m, l);
								StackedContents.this.put(this.items.get(m), i);
								if (bl2) {
									output.accept(this.items.get(m));
								}
								break;
							}
						}
					}

					assert this.data.get(this.residualOffset(), this.residualOffset() + this.residualCount()).isEmpty();
					return bl;
				}

				int k = intList.getInt(0);
				StackedContents.this.take(this.items.get(k), i);
				int l = intList.size() - 1;
				this.setSatisfied(intList.getInt(l));
				j++;

				for (int m = 0; m < intList.size() - 1; m++) {
					if (isPathIndexItem(m)) {
						int n = intList.getInt(m);
						int o = intList.getInt(m + 1);
						this.assign(n, o);
					} else {
						int n = intList.getInt(m + 1);
						int o = intList.getInt(m);
						this.unassign(n, o);
					}
				}
			}
		}

		private static boolean isPathIndexItem(int i) {
			return (i & 1) == 0;
		}

		private @Nullable IntList tryAssigningNewItem(int i) {
			this.clearAllVisited();

			for (int j = 0; j < this.itemCount; j++) {
				if (StackedContents.this.hasAtLeast(this.items.get(j), i)) {
					IntList intList = this.findNewItemAssignmentPath(j);
					if (intList != null) {
						return intList;
					}
				}
			}

			return null;
		}

		private @Nullable IntList findNewItemAssignmentPath(int i) {
			this.path.clear();
			this.visitItem(i);
			this.path.add(i);

			while (!this.path.isEmpty()) {
				int j = this.path.size();
				if (isPathIndexItem(j - 1)) {
					int k = this.path.getInt(j - 1);

					for (int l = 0; l < this.ingredientCount; l++) {
						if (!this.hasVisitedIngredient(l) && this.hasConnection(k, l) && !this.isAssigned(k, l)) {
							this.visitIngredient(l);
							this.path.add(l);
							break;
						}
					}
				} else {
					int k = this.path.getInt(j - 1);
					if (!this.isSatisfied(k)) {
						return this.path;
					}

					for (int l = 0; l < this.itemCount; l++) {
						if (!this.hasVisitedItem(l) && this.isAssigned(l, k)) {
							assert this.hasConnection(l, k);
							this.visitItem(l);
							this.path.add(l);
							break;
						}
					}
				}

				int k = this.path.size();
				if (k == j) {
					this.path.removeInt(k - 1);
				}
			}

			return null;
		}

		private int visitedIngredientOffset() {
			return 0;
		}

		private int visitedIngredientCount() {
			return this.ingredientCount;
		}

		private int visitedItemOffset() {
			return this.visitedIngredientOffset() + this.visitedIngredientCount();
		}

		private int visitedItemCount() {
			return this.itemCount;
		}

		private int satisfiedOffset() {
			return this.visitedItemOffset() + this.visitedItemCount();
		}

		private int satisfiedCount() {
			return this.ingredientCount;
		}

		private int connectionOffset() {
			return this.satisfiedOffset() + this.satisfiedCount();
		}

		private int connectionCount() {
			return this.ingredientCount * this.itemCount;
		}

		private int residualOffset() {
			return this.connectionOffset() + this.connectionCount();
		}

		private int residualCount() {
			return this.ingredientCount * this.itemCount;
		}

		private boolean isSatisfied(int i) {
			return this.data.get(this.getSatisfiedIndex(i));
		}

		private void setSatisfied(int i) {
			this.data.set(this.getSatisfiedIndex(i));
		}

		private int getSatisfiedIndex(int i) {
			assert i >= 0 && i < this.ingredientCount;
			return this.satisfiedOffset() + i;
		}

		private void clearSatisfied() {
			this.clearRange(this.satisfiedOffset(), this.satisfiedCount());
		}

		private void setConnection(int i, int j) {
			this.data.set(this.getConnectionIndex(i, j));
		}

		private boolean hasConnection(int i, int j) {
			return this.data.get(this.getConnectionIndex(i, j));
		}

		private int getConnectionIndex(int i, int j) {
			assert i >= 0 && i < this.itemCount;
			assert j >= 0 && j < this.ingredientCount;
			return this.connectionOffset() + i * this.ingredientCount + j;
		}

		private boolean isAssigned(int i, int j) {
			return this.data.get(this.getResidualIndex(i, j));
		}

		private void assign(int i, int j) {
			int k = this.getResidualIndex(i, j);
			assert !this.data.get(k);
			this.data.set(k);
		}

		private void unassign(int i, int j) {
			int k = this.getResidualIndex(i, j);
			assert this.data.get(k);
			this.data.clear(k);
		}

		private int getResidualIndex(int i, int j) {
			assert i >= 0 && i < this.itemCount;
			assert j >= 0 && j < this.ingredientCount;
			return this.residualOffset() + i * this.ingredientCount + j;
		}

		private void visitIngredient(int i) {
			this.data.set(this.getVisitedIngredientIndex(i));
		}

		private boolean hasVisitedIngredient(int i) {
			return this.data.get(this.getVisitedIngredientIndex(i));
		}

		private int getVisitedIngredientIndex(int i) {
			assert i >= 0 && i < this.ingredientCount;
			return this.visitedIngredientOffset() + i;
		}

		private void visitItem(int i) {
			this.data.set(this.getVisitiedItemIndex(i));
		}

		private boolean hasVisitedItem(int i) {
			return this.data.get(this.getVisitiedItemIndex(i));
		}

		private int getVisitiedItemIndex(int i) {
			assert i >= 0 && i < this.itemCount;
			return this.visitedItemOffset() + i;
		}

		private void clearAllVisited() {
			this.clearRange(this.visitedIngredientOffset(), this.visitedIngredientCount());
			this.clearRange(this.visitedItemOffset(), this.visitedItemCount());
		}

		private void clearRange(int i, int j) {
			this.data.clear(i, i + j);
		}

		public int tryPickAll(int i, StackedContents.@Nullable Output<T> output) {
			int j = 0;
			int k = Math.min(i, StackedContents.this.getResultUpperBound(this.ingredients)) + 1;

			while (true) {
				int l = (j + k) / 2;
				if (this.tryPick(l, null)) {
					if (k - j <= 1) {
						if (l > 0) {
							this.tryPick(l, output);
						}

						return l;
					}

					j = l;
				} else {
					k = l;
				}
			}
		}
	}
}
