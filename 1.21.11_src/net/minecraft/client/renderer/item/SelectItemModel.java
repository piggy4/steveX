package net.minecraft.client.renderer.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.CacheSlot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RegistryContextSwapper;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class SelectItemModel<T> implements ItemModel {
	private final SelectItemModelProperty<T> property;
	private final SelectItemModel.ModelSelector<T> models;

	public SelectItemModel(SelectItemModelProperty<T> selectItemModelProperty, SelectItemModel.ModelSelector<T> modelSelector) {
		this.property = selectItemModelProperty;
		this.models = modelSelector;
	}

	@Override
	public void update(
		ItemStackRenderState itemStackRenderState,
		ItemStack itemStack,
		ItemModelResolver itemModelResolver,
		ItemDisplayContext itemDisplayContext,
		@Nullable ClientLevel clientLevel,
		@Nullable ItemOwner itemOwner,
		int i
	) {
		itemStackRenderState.appendModelIdentityElement(this);
		T object = this.property.get(itemStack, clientLevel, itemOwner == null ? null : itemOwner.asLivingEntity(), i, itemDisplayContext);
		ItemModel itemModel = this.models.get(object, clientLevel);
		if (itemModel != null) {
			itemModel.update(itemStackRenderState, itemStack, itemModelResolver, itemDisplayContext, clientLevel, itemOwner, i);
		}
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	public interface ModelSelector<T> {
		@Nullable ItemModel get(@Nullable T object, @Nullable ClientLevel clientLevel);
	}

	@Environment(EnvType.CLIENT)
	public record SwitchCase<T>(List<T> values, ItemModel.Unbaked model) {

		public static <T> Codec<SelectItemModel.SwitchCase<T>> codec(Codec<T> codec) {
			return RecordCodecBuilder.create(
				instance -> instance.group(
						ExtraCodecs.nonEmptyList(ExtraCodecs.compactListCodec(codec)).fieldOf("when").forGetter(SelectItemModel.SwitchCase::values),
						ItemModels.CODEC.fieldOf("model").forGetter(SelectItemModel.SwitchCase::model)
					)
					.apply(instance, SelectItemModel.SwitchCase::new)
			);
		}
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked(SelectItemModel.UnbakedSwitch<?, ?> unbakedSwitch, Optional<ItemModel.Unbaked> fallback) implements ItemModel.Unbaked {
		public static final MapCodec<SelectItemModel.Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					SelectItemModel.UnbakedSwitch.MAP_CODEC.forGetter(SelectItemModel.Unbaked::unbakedSwitch),
					ItemModels.CODEC.optionalFieldOf("fallback").forGetter(SelectItemModel.Unbaked::fallback)
				)
				.apply(instance, SelectItemModel.Unbaked::new)
		);

		@Override
		public MapCodec<SelectItemModel.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public ItemModel bake(ItemModel.BakingContext bakingContext) {
			ItemModel itemModel = this.fallback.<ItemModel>map(unbaked -> unbaked.bake(bakingContext)).orElse(bakingContext.missingItemModel());
			return this.unbakedSwitch.bake(bakingContext, itemModel);
		}

		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
			this.unbakedSwitch.resolveDependencies(resolver);
			this.fallback.ifPresent(unbaked -> unbaked.resolveDependencies(resolver));
		}
	}

	@Environment(EnvType.CLIENT)
	public record UnbakedSwitch<P extends SelectItemModelProperty<T>, T>(P property, List<SelectItemModel.SwitchCase<T>> cases) {
		public static final MapCodec<SelectItemModel.UnbakedSwitch<?, ?>> MAP_CODEC = SelectItemModelProperties.CODEC
			.dispatchMap("property", unbakedSwitch -> unbakedSwitch.property().type(), SelectItemModelProperty.Type::switchCodec);

		public ItemModel bake(ItemModel.BakingContext bakingContext, ItemModel itemModel) {
			Object2ObjectMap<T, ItemModel> object2ObjectMap = new Object2ObjectOpenHashMap<>();

			for (SelectItemModel.SwitchCase<T> switchCase : this.cases) {
				ItemModel.Unbaked unbaked = switchCase.model;
				ItemModel itemModel2 = unbaked.bake(bakingContext);

				for (T object : switchCase.values) {
					object2ObjectMap.put(object, itemModel2);
				}
			}

			object2ObjectMap.defaultReturnValue(itemModel);
			return new SelectItemModel<>(this.property, this.createModelGetter(object2ObjectMap, bakingContext.contextSwapper()));
		}

		private SelectItemModel.ModelSelector<T> createModelGetter(
			Object2ObjectMap<T, ItemModel> object2ObjectMap, @Nullable RegistryContextSwapper registryContextSwapper
		) {
			if (registryContextSwapper == null) {
				return (object, clientLevel) -> object2ObjectMap.get(object);
			}

			ItemModel itemModel = object2ObjectMap.defaultReturnValue();
			CacheSlot<ClientLevel, Object2ObjectMap<T, ItemModel>> cacheSlot = new CacheSlot<>(
				clientLevel -> {
					Object2ObjectMap<T, ItemModel> object2ObjectMap2 = new Object2ObjectOpenHashMap<>(object2ObjectMap.size());
					object2ObjectMap2.defaultReturnValue(itemModel);
					object2ObjectMap.forEach(
						(object, itemModelxx) -> registryContextSwapper.swapTo(this.property.valueCodec(), (T)object, clientLevel.registryAccess())
							.ifSuccess(objectx -> object2ObjectMap2.put((T)objectx, itemModelxx))
					);
					return object2ObjectMap2;
				}
			);
			return (object, clientLevel) -> {
				if (clientLevel == null) {
					return object2ObjectMap.get(object);
				} else {
					return object == null ? itemModel : cacheSlot.compute(clientLevel).get(object);
				}
			};
		}

		public void resolveDependencies(ResolvableModel.Resolver resolver) {
			for (SelectItemModel.SwitchCase<?> switchCase : this.cases) {
				switchCase.model.resolveDependencies(resolver);
			}
		}
	}
}
