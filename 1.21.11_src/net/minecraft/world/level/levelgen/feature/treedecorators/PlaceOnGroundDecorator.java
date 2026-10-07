package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class PlaceOnGroundDecorator extends TreeDecorator {
	public static final MapCodec<PlaceOnGroundDecorator> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				ExtraCodecs.POSITIVE_INT.fieldOf("tries").orElse(128).forGetter(placeOnGroundDecorator -> placeOnGroundDecorator.tries),
				ExtraCodecs.NON_NEGATIVE_INT.fieldOf("radius").orElse(2).forGetter(placeOnGroundDecorator -> placeOnGroundDecorator.radius),
				ExtraCodecs.NON_NEGATIVE_INT.fieldOf("height").orElse(1).forGetter(placeOnGroundDecorator -> placeOnGroundDecorator.height),
				BlockStateProvider.CODEC.fieldOf("block_state_provider").forGetter(placeOnGroundDecorator -> placeOnGroundDecorator.blockStateProvider)
			)
			.apply(instance, PlaceOnGroundDecorator::new)
	);
	private final int tries;
	private final int radius;
	private final int height;
	private final BlockStateProvider blockStateProvider;

	public PlaceOnGroundDecorator(int i, int j, int k, BlockStateProvider blockStateProvider) {
		this.tries = i;
		this.radius = j;
		this.height = k;
		this.blockStateProvider = blockStateProvider;
	}

	@Override
	protected TreeDecoratorType<?> type() {
		return TreeDecoratorType.PLACE_ON_GROUND;
	}

	@Override
	public void place(TreeDecorator.Context context) {
		List<BlockPos> list = TreeFeature.getLowestTrunkOrRootOfTree(context);
		if (!list.isEmpty()) {
			BlockPos blockPos = list.getFirst();
			int i = blockPos.getY();
			int j = blockPos.getX();
			int k = blockPos.getX();
			int l = blockPos.getZ();
			int m = blockPos.getZ();

			for (BlockPos blockPos2 : list) {
				if (blockPos2.getY() == i) {
					j = Math.min(j, blockPos2.getX());
					k = Math.max(k, blockPos2.getX());
					l = Math.min(l, blockPos2.getZ());
					m = Math.max(m, blockPos2.getZ());
				}
			}

			RandomSource randomSource = context.random();
			BoundingBox boundingBox = new BoundingBox(j, i, l, k, i, m).inflatedBy(this.radius, this.height, this.radius);
			BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

			for (int n = 0; n < this.tries; n++) {
				mutableBlockPos.set(
					randomSource.nextIntBetweenInclusive(boundingBox.minX(), boundingBox.maxX()),
					randomSource.nextIntBetweenInclusive(boundingBox.minY(), boundingBox.maxY()),
					randomSource.nextIntBetweenInclusive(boundingBox.minZ(), boundingBox.maxZ())
				);
				this.attemptToPlaceBlockAbove(context, mutableBlockPos);
			}
		}
	}

	private void attemptToPlaceBlockAbove(TreeDecorator.Context context, BlockPos blockPos) {
		BlockPos blockPos2 = blockPos.above();
		if (context.level().isStateAtPosition(blockPos2, blockState -> blockState.isAir() || blockState.is(Blocks.VINE))
			&& context.checkBlock(blockPos, BlockBehaviour.BlockStateBase::isSolidRender)
			&& context.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockPos).getY() <= blockPos2.getY()) {
			context.setBlock(blockPos2, this.blockStateProvider.getState(context.random(), blockPos2));
		}
	}
}
