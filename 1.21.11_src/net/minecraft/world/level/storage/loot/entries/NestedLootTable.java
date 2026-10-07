package net.minecraft.world.level.storage.loot.entries;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class NestedLootTable extends LootPoolSingletonContainer {
	public static final MapCodec<NestedLootTable> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(Codec.either(LootTable.KEY_CODEC, LootTable.DIRECT_CODEC).fieldOf("value").forGetter(nestedLootTable -> nestedLootTable.contents))
			.and(singletonFields(instance))
			.apply(instance, NestedLootTable::new)
	);
	public static final ProblemReporter.PathElement INLINE_LOOT_TABLE_PATH_ELEMENT = new ProblemReporter.PathElement() {
		@Override
		public String get() {
			return "->{inline}";
		}
	};
	private final Either<ResourceKey<LootTable>, LootTable> contents;

	private NestedLootTable(Either<ResourceKey<LootTable>, LootTable> either, int i, int j, List<LootItemCondition> list, List<LootItemFunction> list2) {
		super(i, j, list, list2);
		this.contents = either;
	}

	@Override
	public LootPoolEntryType getType() {
		return LootPoolEntries.LOOT_TABLE;
	}

	@Override
	public void createItemStack(Consumer<ItemStack> consumer, LootContext lootContext) {
		this.contents
			.map(
				resourceKey -> lootContext.getResolver().get((ResourceKey<LootTable>)resourceKey).map(Holder::value).orElse(LootTable.EMPTY),
				lootTable -> (LootTable)lootTable
			)
			.getRandomItemsRaw(lootContext, consumer);
	}

	@Override
	public void validate(ValidationContext validationContext) {
		Optional<ResourceKey<LootTable>> optional = this.contents.left();
		if (optional.isPresent()) {
			ResourceKey<LootTable> resourceKey = optional.get();
			if (!validationContext.allowsReferences()) {
				validationContext.reportProblem(new ValidationContext.ReferenceNotAllowedProblem(resourceKey));
				return;
			}

			if (validationContext.hasVisitedElement(resourceKey)) {
				validationContext.reportProblem(new ValidationContext.RecursiveReferenceProblem(resourceKey));
				return;
			}
		}

		super.validate(validationContext);
		this.contents
			.ifLeft(
				resourceKeyx -> validationContext.resolver()
					.get(resourceKeyx)
					.ifPresentOrElse(
						reference -> ((LootTable)reference.value())
							.validate(validationContext.enterElement(new ProblemReporter.ElementReferencePathElement(resourceKeyx), resourceKeyx)),
						() -> validationContext.reportProblem(new ValidationContext.MissingReferenceProblem(resourceKeyx))
					)
			)
			.ifRight(lootTable -> lootTable.validate(validationContext.forChild(INLINE_LOOT_TABLE_PATH_ELEMENT)));
	}

	public static LootPoolSingletonContainer.Builder<?> lootTableReference(ResourceKey<LootTable> resourceKey) {
		return simpleBuilder((i, j, list, list2) -> new NestedLootTable(Either.left(resourceKey), i, j, list, list2));
	}

	public static LootPoolSingletonContainer.Builder<?> inlineLootTable(LootTable lootTable) {
		return simpleBuilder((i, j, list, list2) -> new NestedLootTable(Either.right(lootTable), i, j, list, list2));
	}
}
