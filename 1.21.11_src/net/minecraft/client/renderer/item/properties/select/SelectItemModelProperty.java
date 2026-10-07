package net.minecraft.client.renderer.item.properties.select;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.SelectItemModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface SelectItemModelProperty<T> {
	@Nullable T get(ItemStack itemStack, @Nullable ClientLevel clientLevel, @Nullable LivingEntity livingEntity, int i, ItemDisplayContext itemDisplayContext);

	Codec<T> valueCodec();

	SelectItemModelProperty.Type<? extends SelectItemModelProperty<T>, T> type();

	@Environment(EnvType.CLIENT)
	record Type<P extends SelectItemModelProperty<T>, T>(MapCodec<SelectItemModel.UnbakedSwitch<P, T>> switchCodec) {
		public static <P extends SelectItemModelProperty<T>, T> SelectItemModelProperty.Type<P, T> create(MapCodec<P> mapCodec, Codec<T> codec) {
			MapCodec<SelectItemModel.UnbakedSwitch<P, T>> mapCodec2 = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						mapCodec.forGetter(SelectItemModel.UnbakedSwitch::property), createCasesFieldCodec(codec).forGetter(SelectItemModel.UnbakedSwitch::cases)
					)
					.apply(instance, SelectItemModel.UnbakedSwitch::new)
			);
			return new SelectItemModelProperty.Type<>(mapCodec2);
		}

		public static <T> MapCodec<List<SelectItemModel.SwitchCase<T>>> createCasesFieldCodec(Codec<T> codec) {
			return SelectItemModel.SwitchCase.codec(codec).listOf().validate(SelectItemModelProperty.Type::validateCases).fieldOf("cases");
		}

		private static <T> DataResult<List<SelectItemModel.SwitchCase<T>>> validateCases(List<SelectItemModel.SwitchCase<T>> list) {
			if (list.isEmpty()) {
				return DataResult.error(() -> "Empty case list");
			}

			Multiset<T> multiset = HashMultiset.create();

			for (SelectItemModel.SwitchCase<T> switchCase : list) {
				multiset.addAll(switchCase.values());
			}

			return multiset.size() != multiset.entrySet().size()
				? DataResult.error(
					() -> "Duplicate case conditions: "
						+ multiset.entrySet().stream().filter(entry -> entry.getCount() > 1).map(entry -> entry.getElement().toString()).collect(Collectors.joining(", "))
				)
				: DataResult.success(list);
		}
	}
}
