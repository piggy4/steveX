package net.minecraft.world.level.storage.loot;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.loot.v3.FabricLootTableBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.functions.FunctionUserBuilder;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.slf4j.Logger;

public class LootTable {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final Codec<ResourceKey<LootTable>> KEY_CODEC = ResourceKey.codec(Registries.LOOT_TABLE);
	public static final ContextKeySet DEFAULT_PARAM_SET = LootContextParamSets.ALL_PARAMS;
	public static final long RANDOMIZE_SEED = 0L;
	public static final Codec<LootTable> DIRECT_CODEC = Codec.lazyInitialized(
		() -> RecordCodecBuilder.create(
			instance -> instance.group(
					LootContextParamSets.CODEC.lenientOptionalFieldOf("type", DEFAULT_PARAM_SET).forGetter(lootTable -> lootTable.paramSet),
					Identifier.CODEC.optionalFieldOf("random_sequence").forGetter(lootTable -> lootTable.randomSequence),
					LootPool.CODEC.listOf().optionalFieldOf("pools", List.of()).forGetter(lootTable -> lootTable.pools),
					LootItemFunctions.ROOT_CODEC.listOf().optionalFieldOf("functions", List.of()).forGetter(lootTable -> lootTable.functions)
				)
				.apply(instance, LootTable::new)
		)
	);
	public static final Codec<Holder<LootTable>> CODEC = RegistryFileCodec.create(Registries.LOOT_TABLE, DIRECT_CODEC);
	public static final LootTable EMPTY = new LootTable(LootContextParamSets.EMPTY, Optional.empty(), List.of(), List.of());
	private final ContextKeySet paramSet;
	private final Optional<Identifier> randomSequence;
	private final List<LootPool> pools;
	private final List<LootItemFunction> functions;
	private final BiFunction<ItemStack, LootContext, ItemStack> compositeFunction;

	LootTable(ContextKeySet contextKeySet, Optional<Identifier> optional, List<LootPool> list, List<LootItemFunction> list2) {
		this.paramSet = contextKeySet;
		this.randomSequence = optional;
		this.pools = list;
		this.functions = list2;
		this.compositeFunction = LootItemFunctions.compose(list2);
	}

	public static Consumer<ItemStack> createStackSplitter(ServerLevel serverLevel, Consumer<ItemStack> consumer) {
		return itemStack -> {
			if (itemStack.isItemEnabled(serverLevel.enabledFeatures())) {
				if (itemStack.getCount() < itemStack.getMaxStackSize()) {
					consumer.accept(itemStack);
				} else {
					int i = itemStack.getCount();

					while (i > 0) {
						ItemStack itemStack2 = itemStack.copyWithCount(Math.min(itemStack.getMaxStackSize(), i));
						i -= itemStack2.getCount();
						consumer.accept(itemStack2);
					}
				}
			}
		};
	}

	public void getRandomItemsRaw(LootParams lootParams, Consumer<ItemStack> consumer) {
		this.getRandomItemsRaw(new LootContext.Builder(lootParams).create(this.randomSequence), consumer);
	}

	public void getRandomItemsRaw(LootContext lootContext, Consumer<ItemStack> consumer) {
		LootContext.VisitedEntry<?> visitedEntry = LootContext.createVisitedEntry(this);
		if (lootContext.pushVisitedElement(visitedEntry)) {
			Consumer<ItemStack> consumer2 = LootItemFunction.decorate(this.compositeFunction, consumer, lootContext);

			for (LootPool lootPool : this.pools) {
				lootPool.addRandomItems(consumer2, lootContext);
			}

			lootContext.popVisitedElement(visitedEntry);
		} else {
			LOGGER.warn("Detected infinite loop in loot tables");
		}
	}

	public void getRandomItems(LootParams lootParams, long l, Consumer<ItemStack> consumer) {
		this.getRandomItemsRaw(
			new LootContext.Builder(lootParams).withOptionalRandomSeed(l).create(this.randomSequence), createStackSplitter(lootParams.getLevel(), consumer)
		);
	}

	public void getRandomItems(LootParams lootParams, Consumer<ItemStack> consumer) {
		this.getRandomItemsRaw(lootParams, createStackSplitter(lootParams.getLevel(), consumer));
	}

	public void getRandomItems(LootContext lootContext, Consumer<ItemStack> consumer) {
		this.getRandomItemsRaw(lootContext, createStackSplitter(lootContext.getLevel(), consumer));
	}

	public ObjectArrayList<ItemStack> getRandomItems(LootParams lootParams, RandomSource randomSource) {
		return this.getRandomItems(new LootContext.Builder(lootParams).withOptionalRandomSource(randomSource).create(this.randomSequence));
	}

	public ObjectArrayList<ItemStack> getRandomItems(LootParams lootParams, long l) {
		return this.getRandomItems(new LootContext.Builder(lootParams).withOptionalRandomSeed(l).create(this.randomSequence));
	}

	public ObjectArrayList<ItemStack> getRandomItems(LootParams lootParams) {
		return this.getRandomItems(new LootContext.Builder(lootParams).create(this.randomSequence));
	}

	private ObjectArrayList<ItemStack> getRandomItems(LootContext lootContext) {
		ObjectArrayList<ItemStack> objectArrayList = new ObjectArrayList<>();
		this.getRandomItems(lootContext, objectArrayList::add);
		return objectArrayList;
	}

	public ContextKeySet getParamSet() {
		return this.paramSet;
	}

	public void validate(ValidationContext validationContext) {
		for (int i = 0; i < this.pools.size(); i++) {
			this.pools.get(i).validate(validationContext.forChild(new ProblemReporter.IndexedFieldPathElement("pools", i)));
		}

		for (int i = 0; i < this.functions.size(); i++) {
			this.functions.get(i).validate(validationContext.forChild(new ProblemReporter.IndexedFieldPathElement("functions", i)));
		}
	}

	public void fill(Container container, LootParams lootParams, long l) {
		LootContext lootContext = new LootContext.Builder(lootParams).withOptionalRandomSeed(l).create(this.randomSequence);
		ObjectArrayList<ItemStack> objectArrayList = this.getRandomItems(lootContext);
		RandomSource randomSource = lootContext.getRandom();
		List<Integer> list = this.getAvailableSlots(container, randomSource);
		this.shuffleAndSplitItems(objectArrayList, list.size(), randomSource);

		for (ItemStack itemStack : objectArrayList) {
			if (list.isEmpty()) {
				LOGGER.warn("Tried to over-fill a container");
				return;
			}

			if (itemStack.isEmpty()) {
				container.setItem(list.remove(list.size() - 1), ItemStack.EMPTY);
			} else {
				container.setItem(list.remove(list.size() - 1), itemStack);
			}
		}
	}

	private void shuffleAndSplitItems(ObjectArrayList<ItemStack> objectArrayList, int i, RandomSource randomSource) {
		List<ItemStack> list = Lists.newArrayList();
		Iterator<ItemStack> iterator = objectArrayList.iterator();

		while (iterator.hasNext()) {
			ItemStack itemStack = iterator.next();
			if (itemStack.isEmpty()) {
				iterator.remove();
			} else if (itemStack.getCount() > 1) {
				list.add(itemStack);
				iterator.remove();
			}
		}

		while (i - objectArrayList.size() - list.size() > 0 && !list.isEmpty()) {
			ItemStack itemStack2 = list.remove(Mth.nextInt(randomSource, 0, list.size() - 1));
			int j = Mth.nextInt(randomSource, 1, itemStack2.getCount() / 2);
			ItemStack itemStack3 = itemStack2.split(j);
			if (itemStack2.getCount() > 1 && randomSource.nextBoolean()) {
				list.add(itemStack2);
			} else {
				objectArrayList.add(itemStack2);
			}

			if (itemStack3.getCount() > 1 && randomSource.nextBoolean()) {
				list.add(itemStack3);
			} else {
				objectArrayList.add(itemStack3);
			}
		}

		objectArrayList.addAll(list);
		Util.shuffle(objectArrayList, randomSource);
	}

	private List<Integer> getAvailableSlots(Container container, RandomSource randomSource) {
		ObjectArrayList<Integer> objectArrayList = new ObjectArrayList<>();

		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).isEmpty()) {
				objectArrayList.add(i);
			}
		}

		Util.shuffle(objectArrayList, randomSource);
		return objectArrayList;
	}

	public static LootTable.Builder lootTable() {
		return new LootTable.Builder();
	}

	/**
	 * <p>Interface {@link net.fabricmc.fabric.api.loot.v3.FabricLootTableBuilder} injected by mod fabric-loot-api-v3</p>
	 */
	public static class Builder implements FunctionUserBuilder<LootTable.Builder>, FabricLootTableBuilder {
		private final ImmutableList.Builder<LootPool> pools = ImmutableList.builder();
		private final ImmutableList.Builder<LootItemFunction> functions = ImmutableList.builder();
		private ContextKeySet paramSet = LootTable.DEFAULT_PARAM_SET;
		private Optional<Identifier> randomSequence = Optional.empty();

		public LootTable.Builder withPool(LootPool.Builder builder) {
			this.pools.add(builder.build());
			return this;
		}

		public LootTable.Builder setParamSet(ContextKeySet contextKeySet) {
			this.paramSet = contextKeySet;
			return this;
		}

		public LootTable.Builder setRandomSequence(Identifier identifier) {
			this.randomSequence = Optional.of(identifier);
			return this;
		}

		public LootTable.Builder apply(LootItemFunction.Builder builder) {
			this.functions.add(builder.build());
			return this;
		}

		public LootTable.Builder unwrap() {
			return this;
		}

		public LootTable build() {
			return new LootTable(this.paramSet, this.randomSequence, this.pools.build(), this.functions.build());
		}
	}
}
