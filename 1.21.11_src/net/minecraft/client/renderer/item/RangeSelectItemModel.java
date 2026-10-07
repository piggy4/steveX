package net.minecraft.client.renderer.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class RangeSelectItemModel implements ItemModel {
	private static final int LINEAR_SEARCH_THRESHOLD = 16;
	private final RangeSelectItemModelProperty property;
	private final float scale;
	private final float[] thresholds;
	private final ItemModel[] models;
	private final ItemModel fallback;

	RangeSelectItemModel(RangeSelectItemModelProperty rangeSelectItemModelProperty, float f, float[] fs, ItemModel[] itemModels, ItemModel itemModel) {
		this.property = rangeSelectItemModelProperty;
		this.thresholds = fs;
		this.models = itemModels;
		this.fallback = itemModel;
		this.scale = f;
	}

	private static int lastIndexLessOrEqual(float[] fs, float f) {
		if (fs.length < 16) {
			for (int i = 0; i < fs.length; i++) {
				if (fs[i] > f) {
					return i - 1;
				}
			}

			return fs.length - 1;
		} else {
			int i = Arrays.binarySearch(fs, f);
			if (i < 0) {
				int j = ~i;
				return j - 1;
			} else {
				return i;
			}
		}
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
		float f = this.property.get(itemStack, clientLevel, itemOwner, i) * this.scale;
		ItemModel itemModel;
		if (Float.isNaN(f)) {
			itemModel = this.fallback;
		} else {
			int j = lastIndexLessOrEqual(this.thresholds, f);
			itemModel = j == -1 ? this.fallback : this.models[j];
		}

		itemModel.update(itemStackRenderState, itemStack, itemModelResolver, itemDisplayContext, clientLevel, itemOwner, i);
	}

	@Environment(EnvType.CLIENT)
	public record Entry(float threshold, ItemModel.Unbaked model) {
		public static final Codec<RangeSelectItemModel.Entry> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Codec.FLOAT.fieldOf("threshold").forGetter(RangeSelectItemModel.Entry::threshold),
					ItemModels.CODEC.fieldOf("model").forGetter(RangeSelectItemModel.Entry::model)
				)
				.apply(instance, RangeSelectItemModel.Entry::new)
		);
		public static final Comparator<RangeSelectItemModel.Entry> BY_THRESHOLD = Comparator.comparingDouble(RangeSelectItemModel.Entry::threshold);
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked(RangeSelectItemModelProperty property, float scale, List<RangeSelectItemModel.Entry> entries, Optional<ItemModel.Unbaked> fallback)
		implements ItemModel.Unbaked {
		public static final MapCodec<RangeSelectItemModel.Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					RangeSelectItemModelProperties.MAP_CODEC.forGetter(RangeSelectItemModel.Unbaked::property),
					Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(RangeSelectItemModel.Unbaked::scale),
					RangeSelectItemModel.Entry.CODEC.listOf().fieldOf("entries").forGetter(RangeSelectItemModel.Unbaked::entries),
					ItemModels.CODEC.optionalFieldOf("fallback").forGetter(RangeSelectItemModel.Unbaked::fallback)
				)
				.apply(instance, RangeSelectItemModel.Unbaked::new)
		);

		@Override
		public MapCodec<RangeSelectItemModel.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public ItemModel bake(ItemModel.BakingContext bakingContext) {
			float[] fs = new float[this.entries.size()];
			ItemModel[] itemModels = new ItemModel[this.entries.size()];
			List<RangeSelectItemModel.Entry> list = new ArrayList<>(this.entries);
			list.sort(RangeSelectItemModel.Entry.BY_THRESHOLD);

			for (int i = 0; i < list.size(); i++) {
				RangeSelectItemModel.Entry entry = list.get(i);
				fs[i] = entry.threshold;
				itemModels[i] = entry.model.bake(bakingContext);
			}

			ItemModel itemModel = this.fallback.<ItemModel>map(unbaked -> unbaked.bake(bakingContext)).orElse(bakingContext.missingItemModel());
			return new RangeSelectItemModel(this.property, this.scale, fs, itemModels, itemModel);
		}

		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
			this.fallback.ifPresent(unbaked -> unbaked.resolveDependencies(resolver));
			this.entries.forEach(entry -> entry.model.resolveDependencies(resolver));
		}
	}
}
