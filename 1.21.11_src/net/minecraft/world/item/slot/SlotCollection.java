package net.minecraft.world.item.slot;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.item.ItemStack;

public interface SlotCollection {
	SlotCollection EMPTY = Stream::empty;

	Stream<ItemStack> itemCopies();

	default SlotCollection filter(Predicate<ItemStack> predicate) {
		return new SlotCollection.Filtered(this, predicate);
	}

	default SlotCollection flatMap(Function<ItemStack, ? extends SlotCollection> function) {
		return new SlotCollection.FlatMapped(this, function);
	}

	default SlotCollection limit(int i) {
		return new SlotCollection.Limited(this, i);
	}

	static SlotCollection of(SlotAccess slotAccess) {
		return () -> Stream.of(slotAccess.get().copy());
	}

	static SlotCollection of(Collection<? extends SlotAccess> collection) {
		return switch (collection.size()) {
			case 0 -> EMPTY;
			case 1 -> of(collection.iterator().next());
			default -> () -> collection.stream().map(SlotAccess::get).map(ItemStack::copy);
		};
	}

	static SlotCollection concat(SlotCollection slotCollection, SlotCollection slotCollection2) {
		return () -> Stream.concat(slotCollection.itemCopies(), slotCollection2.itemCopies());
	}

	static SlotCollection concat(List<? extends SlotCollection> list) {
		return switch (list.size()) {
			case 0 -> EMPTY;
			case 1 -> (SlotCollection)list.getFirst();
			case 2 -> concat(list.get(0), list.get(1));
			default -> () -> list.stream().flatMap(SlotCollection::itemCopies);
		};
	}

	record Filtered(SlotCollection slots, Predicate<ItemStack> filter) implements SlotCollection {
		@Override
		public Stream<ItemStack> itemCopies() {
			return this.slots.itemCopies().filter(this.filter);
		}

		@Override
		public SlotCollection filter(Predicate<ItemStack> predicate) {
			return new SlotCollection.Filtered(this.slots, this.filter.and(predicate));
		}
	}

	record FlatMapped(SlotCollection slots, Function<ItemStack, ? extends SlotCollection> mapper) implements SlotCollection {
		@Override
		public Stream<ItemStack> itemCopies() {
			return this.slots.itemCopies().map(this.mapper).flatMap(SlotCollection::itemCopies);
		}
	}

	record Limited(SlotCollection slots, int limit) implements SlotCollection {
		@Override
		public Stream<ItemStack> itemCopies() {
			return this.slots.itemCopies().limit(this.limit);
		}

		@Override
		public SlotCollection limit(int i) {
			return new SlotCollection.Limited(this.slots, Math.min(this.limit, i));
		}
	}
}
