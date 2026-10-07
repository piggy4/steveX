package net.minecraft.client.data.models.model;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.color.item.Constant;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.CompositeModel;
import net.minecraft.client.renderer.item.ConditionalItemModel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.RangeSelectItemModel;
import net.minecraft.client.renderer.item.SelectItemModel;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.client.renderer.item.properties.conditional.HasComponent;
import net.minecraft.client.renderer.item.properties.conditional.IsUsingItem;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.client.renderer.item.properties.select.ContextDimension;
import net.minecraft.client.renderer.item.properties.select.ItemBlockState;
import net.minecraft.client.renderer.item.properties.select.LocalTime;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.SpecialDates;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.Property;

@Environment(EnvType.CLIENT)
public class ItemModelUtils {
	public static ItemModel.Unbaked plainModel(Identifier identifier) {
		return new BlockModelWrapper.Unbaked(identifier, List.of());
	}

	public static ItemModel.Unbaked tintedModel(Identifier identifier, ItemTintSource... itemTintSources) {
		return new BlockModelWrapper.Unbaked(identifier, List.of(itemTintSources));
	}

	public static ItemTintSource constantTint(int i) {
		return new Constant(i);
	}

	public static ItemModel.Unbaked composite(ItemModel.Unbaked... unbakeds) {
		return new CompositeModel.Unbaked(List.of(unbakeds));
	}

	public static ItemModel.Unbaked specialModel(Identifier identifier, SpecialModelRenderer.Unbaked unbaked) {
		return new SpecialModelWrapper.Unbaked(identifier, unbaked);
	}

	public static RangeSelectItemModel.Entry override(ItemModel.Unbaked unbaked, float f) {
		return new RangeSelectItemModel.Entry(f, unbaked);
	}

	public static ItemModel.Unbaked rangeSelect(
		RangeSelectItemModelProperty rangeSelectItemModelProperty, ItemModel.Unbaked unbaked, RangeSelectItemModel.Entry... entrys
	) {
		return new RangeSelectItemModel.Unbaked(rangeSelectItemModelProperty, 1.0F, List.of(entrys), Optional.of(unbaked));
	}

	public static ItemModel.Unbaked rangeSelect(
		RangeSelectItemModelProperty rangeSelectItemModelProperty, float f, ItemModel.Unbaked unbaked, RangeSelectItemModel.Entry... entrys
	) {
		return new RangeSelectItemModel.Unbaked(rangeSelectItemModelProperty, f, List.of(entrys), Optional.of(unbaked));
	}

	public static ItemModel.Unbaked rangeSelect(
		RangeSelectItemModelProperty rangeSelectItemModelProperty, ItemModel.Unbaked unbaked, List<RangeSelectItemModel.Entry> list
	) {
		return new RangeSelectItemModel.Unbaked(rangeSelectItemModelProperty, 1.0F, list, Optional.of(unbaked));
	}

	public static ItemModel.Unbaked rangeSelect(RangeSelectItemModelProperty rangeSelectItemModelProperty, List<RangeSelectItemModel.Entry> list) {
		return new RangeSelectItemModel.Unbaked(rangeSelectItemModelProperty, 1.0F, list, Optional.empty());
	}

	public static ItemModel.Unbaked rangeSelect(RangeSelectItemModelProperty rangeSelectItemModelProperty, float f, List<RangeSelectItemModel.Entry> list) {
		return new RangeSelectItemModel.Unbaked(rangeSelectItemModelProperty, f, list, Optional.empty());
	}

	public static ItemModel.Unbaked conditional(ConditionalItemModelProperty conditionalItemModelProperty, ItemModel.Unbaked unbaked, ItemModel.Unbaked unbaked2) {
		return new ConditionalItemModel.Unbaked(conditionalItemModelProperty, unbaked, unbaked2);
	}

	public static <T> SelectItemModel.SwitchCase<T> when(T object, ItemModel.Unbaked unbaked) {
		return new SelectItemModel.SwitchCase<>(List.of(object), unbaked);
	}

	public static <T> SelectItemModel.SwitchCase<T> when(List<T> list, ItemModel.Unbaked unbaked) {
		return new SelectItemModel.SwitchCase<>(list, unbaked);
	}

	@SafeVarargs
	public static <T> ItemModel.Unbaked select(
		SelectItemModelProperty<T> selectItemModelProperty, ItemModel.Unbaked unbaked, SelectItemModel.SwitchCase<T>... switchCases
	) {
		return select(selectItemModelProperty, unbaked, List.of(switchCases));
	}

	public static <T> ItemModel.Unbaked select(
		SelectItemModelProperty<T> selectItemModelProperty, ItemModel.Unbaked unbaked, List<SelectItemModel.SwitchCase<T>> list
	) {
		return new SelectItemModel.Unbaked(new SelectItemModel.UnbakedSwitch<>(selectItemModelProperty, list), Optional.of(unbaked));
	}

	@SafeVarargs
	public static <T> ItemModel.Unbaked select(SelectItemModelProperty<T> selectItemModelProperty, SelectItemModel.SwitchCase<T>... switchCases) {
		return select(selectItemModelProperty, List.of(switchCases));
	}

	public static <T> ItemModel.Unbaked select(SelectItemModelProperty<T> selectItemModelProperty, List<SelectItemModel.SwitchCase<T>> list) {
		return new SelectItemModel.Unbaked(new SelectItemModel.UnbakedSwitch<>(selectItemModelProperty, list), Optional.empty());
	}

	public static ConditionalItemModelProperty isUsingItem() {
		return new IsUsingItem();
	}

	public static ConditionalItemModelProperty hasComponent(DataComponentType<?> dataComponentType) {
		return new HasComponent(dataComponentType, false);
	}

	public static ItemModel.Unbaked inOverworld(ItemModel.Unbaked unbaked, ItemModel.Unbaked unbaked2) {
		return select(new ContextDimension(), unbaked2, when(Level.OVERWORLD, unbaked));
	}

	public static <T extends Comparable<T>> ItemModel.Unbaked selectBlockItemProperty(
		Property<T> property, ItemModel.Unbaked unbaked, Map<T, ItemModel.Unbaked> map
	) {
		List<SelectItemModel.SwitchCase<String>> list = map.entrySet().stream().sorted(Entry.comparingByKey()).map(entry -> {
			String string = property.getName(entry.getKey());
			return new SelectItemModel.SwitchCase<>(List.of(string), entry.getValue());
		}).toList();
		return select(new ItemBlockState(property.getName()), unbaked, list);
	}

	public static ItemModel.Unbaked isXmas(ItemModel.Unbaked unbaked, ItemModel.Unbaked unbaked2) {
		DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd", Locale.ROOT);
		List<String> list = SpecialDates.CHRISTMAS_RANGE.stream().map(dateTimeFormatter::format).toList();
		return select(LocalTime.create("MM-dd", "", Optional.empty()), unbaked2, List.of(when(list, unbaked)));
	}
}
