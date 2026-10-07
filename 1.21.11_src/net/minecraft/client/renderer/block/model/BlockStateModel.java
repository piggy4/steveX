package net.minecraft.client.renderer.block.model;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.WeightedVariants;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.block.state.BlockState;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel} injected by mod fabric-renderer-api-v1</p>
 */
@Environment(EnvType.CLIENT)
public interface BlockStateModel extends FabricBlockStateModel {
	void collectParts(RandomSource randomSource, List<BlockModelPart> list);

	default List<BlockModelPart> collectParts(RandomSource randomSource) {
		List<BlockModelPart> list = new ObjectArrayList<>();
		this.collectParts(randomSource, list);
		return list;
	}

	TextureAtlasSprite particleIcon();

	@Environment(EnvType.CLIENT)
	class SimpleCachedUnbakedRoot implements BlockStateModel.UnbakedRoot {
		final BlockStateModel.Unbaked contents;
		private final ModelBaker.SharedOperationKey<BlockStateModel> bakingKey = new ModelBaker.SharedOperationKey<BlockStateModel>() {
			public BlockStateModel compute(ModelBaker modelBaker) {
				return SimpleCachedUnbakedRoot.this.contents.bake(modelBaker);
			}
		};

		public SimpleCachedUnbakedRoot(BlockStateModel.Unbaked unbaked) {
			this.contents = unbaked;
		}

		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
			this.contents.resolveDependencies(resolver);
		}

		@Override
		public BlockStateModel bake(BlockState blockState, ModelBaker modelBaker) {
			return modelBaker.compute(this.bakingKey);
		}

		@Override
		public Object visualEqualityGroup(BlockState blockState) {
			return this;
		}
	}

	@Environment(EnvType.CLIENT)
	interface Unbaked extends ResolvableModel {
		Codec<Weighted<Variant>> ELEMENT_CODEC = RecordCodecBuilder.create(
			instance -> instance.group(Variant.MAP_CODEC.forGetter(Weighted::value), ExtraCodecs.POSITIVE_INT.optionalFieldOf("weight", 1).forGetter(Weighted::weight))
				.apply(instance, Weighted::new)
		);
		Codec<WeightedVariants.Unbaked> HARDCODED_WEIGHTED_CODEC = ExtraCodecs.nonEmptyList(ELEMENT_CODEC.listOf())
			.flatComapMap(
				list -> new WeightedVariants.Unbaked(WeightedList.of(Lists.transform((List<Weighted<Variant>>)list, weighted -> weighted.map(SingleVariant.Unbaked::new)))),
				unbaked -> {
					List<Weighted<BlockStateModel.Unbaked>> list = unbaked.entries().unwrap();
					List<Weighted<Variant>> list2 = new ArrayList<>(list.size());

					for (Weighted<BlockStateModel.Unbaked> weighted : list) {
						if (!(weighted.value() instanceof SingleVariant.Unbaked unbaked2)) {
							return DataResult.error(() -> "Only single variants are supported");
						}

						list2.add(new Weighted<>(unbaked2.variant(), weighted.weight()));
					}

					return DataResult.success(list2);
				}
			);
		Codec<BlockStateModel.Unbaked> CODEC = Codec.either(HARDCODED_WEIGHTED_CODEC, SingleVariant.Unbaked.CODEC)
			.flatComapMap(either -> either.map(unbaked -> unbaked, unbaked -> unbaked), unbaked -> {
				return switch (unbaked) {
					case SingleVariant.Unbaked unbaked3 -> DataResult.success(Either.right(unbaked3));
					case WeightedVariants.Unbaked unbaked4 -> DataResult.success(Either.left(unbaked4));
					default -> DataResult.error(() -> "Only a single variant or a list of variants are supported");
				};
			});

		BlockStateModel bake(ModelBaker modelBaker);

		default BlockStateModel.UnbakedRoot asRoot() {
			return new BlockStateModel.SimpleCachedUnbakedRoot(this);
		}
	}

	@Environment(EnvType.CLIENT)
	interface UnbakedRoot extends ResolvableModel {
		BlockStateModel bake(BlockState blockState, ModelBaker modelBaker);

		Object visualEqualityGroup(BlockState blockState);
	}
}
