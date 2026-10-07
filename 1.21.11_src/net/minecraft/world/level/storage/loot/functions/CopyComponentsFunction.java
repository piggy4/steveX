package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Util;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootContextArg;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class CopyComponentsFunction extends LootItemConditionalFunction {
	private static final Codec<LootContextArg<DataComponentGetter>> GETTER_CODEC = LootContextArg.createArgCodec(
		argCodecBuilder -> argCodecBuilder.anyEntity(CopyComponentsFunction.DirectSource::new)
			.anyBlockEntity(CopyComponentsFunction.BlockEntitySource::new)
			.anyItemStack(CopyComponentsFunction.DirectSource::new)
	);
	public static final MapCodec<CopyComponentsFunction> CODEC = RecordCodecBuilder.mapCodec(
		instance -> commonFields(instance)
			.and(
				instance.group(
					GETTER_CODEC.fieldOf("source").forGetter(copyComponentsFunction -> copyComponentsFunction.source),
					DataComponentType.CODEC.listOf().optionalFieldOf("include").forGetter(copyComponentsFunction -> copyComponentsFunction.include),
					DataComponentType.CODEC.listOf().optionalFieldOf("exclude").forGetter(copyComponentsFunction -> copyComponentsFunction.exclude)
				)
			)
			.apply(instance, CopyComponentsFunction::new)
	);
	private final LootContextArg<DataComponentGetter> source;
	private final Optional<List<DataComponentType<?>>> include;
	private final Optional<List<DataComponentType<?>>> exclude;
	private final Predicate<DataComponentType<?>> bakedPredicate;

	CopyComponentsFunction(
		List<LootItemCondition> list,
		LootContextArg<DataComponentGetter> lootContextArg,
		Optional<List<DataComponentType<?>>> optional,
		Optional<List<DataComponentType<?>>> optional2
	) {
		super(list);
		this.source = lootContextArg;
		this.include = optional.map(List::copyOf);
		this.exclude = optional2.map(List::copyOf);
		List<Predicate<DataComponentType<?>>> list2 = new ArrayList<>(2);
		optional2.ifPresent(list2x -> list2.add(dataComponentType -> !list2x.contains(dataComponentType)));
		optional.ifPresent(list2x -> list2.add(list2x::contains));
		this.bakedPredicate = Util.allOf(list2);
	}

	@Override
	public LootItemFunctionType<CopyComponentsFunction> getType() {
		return LootItemFunctions.COPY_COMPONENTS;
	}

	@Override
	public Set<ContextKey<?>> getReferencedContextParams() {
		return Set.of(this.source.contextParam());
	}

	@Override
	public ItemStack run(ItemStack itemStack, LootContext lootContext) {
		DataComponentGetter dataComponentGetter = this.source.get(lootContext);
		if (dataComponentGetter != null) {
			if (dataComponentGetter instanceof DataComponentMap dataComponentMap) {
				itemStack.applyComponents(dataComponentMap.filter(this.bakedPredicate));
			} else {
				Collection<DataComponentType<?>> collection = this.exclude.orElse(List.of());
				this.include.map(Collection::stream).orElse(BuiltInRegistries.DATA_COMPONENT_TYPE.listElements().map(Holder::value)).forEach(dataComponentType -> {
					if (!collection.contains(dataComponentType)) {
						TypedDataComponent<?> typedDataComponent = dataComponentGetter.getTyped(dataComponentType);
						if (typedDataComponent != null) {
							itemStack.set(typedDataComponent);
						}
					}
				});
			}
		}

		return itemStack;
	}

	public static CopyComponentsFunction.Builder copyComponentsFromEntity(ContextKey<? extends Entity> contextKey) {
		return new CopyComponentsFunction.Builder(new CopyComponentsFunction.DirectSource<>(contextKey));
	}

	public static CopyComponentsFunction.Builder copyComponentsFromBlockEntity(ContextKey<? extends BlockEntity> contextKey) {
		return new CopyComponentsFunction.Builder(new CopyComponentsFunction.BlockEntitySource(contextKey));
	}

	record BlockEntitySource(ContextKey<? extends BlockEntity> contextParam) implements LootContextArg.Getter<BlockEntity, DataComponentGetter> {
		public DataComponentGetter get(BlockEntity blockEntity) {
			return blockEntity.collectComponents();
		}
	}

	public static class Builder extends LootItemConditionalFunction.Builder<CopyComponentsFunction.Builder> {
		private final LootContextArg<DataComponentGetter> source;
		private Optional<ImmutableList.Builder<DataComponentType<?>>> include = Optional.empty();
		private Optional<ImmutableList.Builder<DataComponentType<?>>> exclude = Optional.empty();

		Builder(LootContextArg<DataComponentGetter> lootContextArg) {
			this.source = lootContextArg;
		}

		public CopyComponentsFunction.Builder include(DataComponentType<?> dataComponentType) {
			if (this.include.isEmpty()) {
				this.include = Optional.of(ImmutableList.builder());
			}

			this.include.get().add(dataComponentType);
			return this;
		}

		public CopyComponentsFunction.Builder exclude(DataComponentType<?> dataComponentType) {
			if (this.exclude.isEmpty()) {
				this.exclude = Optional.of(ImmutableList.builder());
			}

			this.exclude.get().add(dataComponentType);
			return this;
		}

		protected CopyComponentsFunction.Builder getThis() {
			return this;
		}

		@Override
		public LootItemFunction build() {
			return new CopyComponentsFunction(
				this.getConditions(), this.source, this.include.map(ImmutableList.Builder::build), this.exclude.map(ImmutableList.Builder::build)
			);
		}
	}

	record DirectSource<T extends DataComponentGetter>(ContextKey<? extends T> contextParam) implements LootContextArg.Getter<T, DataComponentGetter> {
		public DataComponentGetter get(T dataComponentGetter) {
			return dataComponentGetter;
		}
	}
}
