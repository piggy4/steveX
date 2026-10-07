package net.minecraft.client.renderer.block.model.multipart;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class MultiPartModel implements BlockStateModel {
	private final MultiPartModel.SharedBakedState shared;
	private final BlockState blockState;
	private @Nullable List<BlockStateModel> models;

	MultiPartModel(MultiPartModel.SharedBakedState sharedBakedState, BlockState blockState) {
		this.shared = sharedBakedState;
		this.blockState = blockState;
	}

	@Override
	public TextureAtlasSprite particleIcon() {
		return this.shared.particleIcon;
	}

	@Override
	public void collectParts(RandomSource randomSource, List<BlockModelPart> list) {
		if (this.models == null) {
			this.models = this.shared.selectModels(this.blockState);
		}

		long l = randomSource.nextLong();

		for (BlockStateModel blockStateModel : this.models) {
			randomSource.setSeed(l);
			blockStateModel.collectParts(randomSource, list);
		}
	}

	@Environment(EnvType.CLIENT)
	public record Selector<T>(Predicate<BlockState> condition, T model) {

		public <S> MultiPartModel.Selector<S> with(S object) {
			return new MultiPartModel.Selector<>(this.condition, object);
		}
	}

	@Environment(EnvType.CLIENT)
	static final class SharedBakedState {
		private final List<MultiPartModel.Selector<BlockStateModel>> selectors;
		final TextureAtlasSprite particleIcon;
		private final Map<BitSet, List<BlockStateModel>> subsets = new ConcurrentHashMap<>();

		private static BlockStateModel getFirstModel(List<MultiPartModel.Selector<BlockStateModel>> list) {
			if (list.isEmpty()) {
				throw new IllegalArgumentException("Model must have at least one selector");
			} else {
				return list.getFirst().model();
			}
		}

		public SharedBakedState(List<MultiPartModel.Selector<BlockStateModel>> list) {
			this.selectors = list;
			BlockStateModel blockStateModel = getFirstModel(list);
			this.particleIcon = blockStateModel.particleIcon();
		}

		public List<BlockStateModel> selectModels(BlockState blockState) {
			BitSet bitSet = new BitSet();

			for (int i = 0; i < this.selectors.size(); i++) {
				if (this.selectors.get(i).condition.test(blockState)) {
					bitSet.set(i);
				}
			}

			return this.subsets.computeIfAbsent(bitSet, bitSetx -> {
				Builder<BlockStateModel> builder = ImmutableList.builder();

				for (int ix = 0; ix < this.selectors.size(); ix++) {
					if (bitSetx.get(ix)) {
						builder.add((BlockStateModel)this.selectors.get(ix).model);
					}
				}

				return builder.build();
			});
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Unbaked implements BlockStateModel.UnbakedRoot {
		final List<MultiPartModel.Selector<BlockStateModel.Unbaked>> selectors;
		private final ModelBaker.SharedOperationKey<MultiPartModel.SharedBakedState> sharedStateKey = new ModelBaker.SharedOperationKey<MultiPartModel.SharedBakedState>(
			
		) {
			public MultiPartModel.SharedBakedState compute(ModelBaker modelBaker) {
				Builder<MultiPartModel.Selector<BlockStateModel>> builder = ImmutableList.builderWithExpectedSize(Unbaked.this.selectors.size());

				for (MultiPartModel.Selector<BlockStateModel.Unbaked> selector : Unbaked.this.selectors) {
					builder.add(selector.with(selector.model.bake(modelBaker)));
				}

				return new MultiPartModel.SharedBakedState(builder.build());
			}
		};

		public Unbaked(List<MultiPartModel.Selector<BlockStateModel.Unbaked>> list) {
			this.selectors = list;
		}

		@Override
		public Object visualEqualityGroup(BlockState blockState) {
			IntList intList = new IntArrayList();

			for (int i = 0; i < this.selectors.size(); i++) {
				if (this.selectors.get(i).condition.test(blockState)) {
					intList.add(i);
				}
			}

			@Environment(EnvType.CLIENT)
			record Key(MultiPartModel.Unbaked model, IntList selectors) {
			}

			return new Key(this, intList);
		}

		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
			this.selectors.forEach(selector -> selector.model.resolveDependencies(resolver));
		}

		@Override
		public BlockStateModel bake(BlockState blockState, ModelBaker modelBaker) {
			MultiPartModel.SharedBakedState sharedBakedState = modelBaker.compute(this.sharedStateKey);
			return new MultiPartModel(sharedBakedState, blockState);
		}
	}
}
