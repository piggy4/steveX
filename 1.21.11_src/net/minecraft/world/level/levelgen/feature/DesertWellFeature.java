package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class DesertWellFeature extends Feature<NoneFeatureConfiguration> {
	private static final BlockStatePredicate IS_SAND = BlockStatePredicate.forBlock(Blocks.SAND);
	private final BlockState sand = Blocks.SAND.defaultBlockState();
	private final BlockState sandSlab = Blocks.SANDSTONE_SLAB.defaultBlockState();
	private final BlockState sandstone = Blocks.SANDSTONE.defaultBlockState();
	private final BlockState water = Blocks.WATER.defaultBlockState();

	public DesertWellFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> featurePlaceContext) {
		WorldGenLevel worldGenLevel = featurePlaceContext.level();
		BlockPos blockPos = featurePlaceContext.origin();
		blockPos = blockPos.above();

		while (worldGenLevel.isEmptyBlock(blockPos) && blockPos.getY() > worldGenLevel.getMinY() + 2) {
			blockPos = blockPos.below();
		}

		if (!IS_SAND.test(worldGenLevel.getBlockState(blockPos))) {
			return false;
		}

		for (int i = -2; i <= 2; i++) {
			for (int j = -2; j <= 2; j++) {
				if (worldGenLevel.isEmptyBlock(blockPos.offset(i, -1, j)) && worldGenLevel.isEmptyBlock(blockPos.offset(i, -2, j))) {
					return false;
				}
			}
		}

		for (int i = -2; i <= 0; i++) {
			for (int j = -2; j <= 2; j++) {
				for (int k = -2; k <= 2; k++) {
					worldGenLevel.setBlock(blockPos.offset(j, i, k), this.sandstone, 2);
				}
			}
		}

		worldGenLevel.setBlock(blockPos, this.water, 2);

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			worldGenLevel.setBlock(blockPos.relative(direction), this.water, 2);
		}

		BlockPos blockPos2 = blockPos.below();
		worldGenLevel.setBlock(blockPos2, this.sand, 2);

		for (Direction direction2 : Direction.Plane.HORIZONTAL) {
			worldGenLevel.setBlock(blockPos2.relative(direction2), this.sand, 2);
		}

		for (int j = -2; j <= 2; j++) {
			for (int k = -2; k <= 2; k++) {
				if (j == -2 || j == 2 || k == -2 || k == 2) {
					worldGenLevel.setBlock(blockPos.offset(j, 1, k), this.sandstone, 2);
				}
			}
		}

		worldGenLevel.setBlock(blockPos.offset(2, 1, 0), this.sandSlab, 2);
		worldGenLevel.setBlock(blockPos.offset(-2, 1, 0), this.sandSlab, 2);
		worldGenLevel.setBlock(blockPos.offset(0, 1, 2), this.sandSlab, 2);
		worldGenLevel.setBlock(blockPos.offset(0, 1, -2), this.sandSlab, 2);

		for (int j = -1; j <= 1; j++) {
			for (int k = -1; k <= 1; k++) {
				if (j == 0 && k == 0) {
					worldGenLevel.setBlock(blockPos.offset(j, 4, k), this.sandstone, 2);
				} else {
					worldGenLevel.setBlock(blockPos.offset(j, 4, k), this.sandSlab, 2);
				}
			}
		}

		for (int j = 1; j <= 3; j++) {
			worldGenLevel.setBlock(blockPos.offset(-1, j, -1), this.sandstone, 2);
			worldGenLevel.setBlock(blockPos.offset(-1, j, 1), this.sandstone, 2);
			worldGenLevel.setBlock(blockPos.offset(1, j, -1), this.sandstone, 2);
			worldGenLevel.setBlock(blockPos.offset(1, j, 1), this.sandstone, 2);
		}

		BlockPos blockPos3 = blockPos;
		List<BlockPos> list = List.of(blockPos3, blockPos3.east(), blockPos3.south(), blockPos3.west(), blockPos3.north());
		RandomSource randomSource = featurePlaceContext.random();
		placeSusSand(worldGenLevel, Util.getRandom(list, randomSource).below(1));
		placeSusSand(worldGenLevel, Util.getRandom(list, randomSource).below(2));
		return true;
	}

	private static void placeSusSand(WorldGenLevel worldGenLevel, BlockPos blockPos) {
		worldGenLevel.setBlock(blockPos, Blocks.SUSPICIOUS_SAND.defaultBlockState(), 3);
		worldGenLevel.getBlockEntity(blockPos, BlockEntityType.BRUSHABLE_BLOCK)
			.ifPresent(brushableBlockEntity -> brushableBlockEntity.setLootTable(BuiltInLootTables.DESERT_WELL_ARCHAEOLOGY, blockPos.asLong()));
	}
}
