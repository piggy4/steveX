package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public class AttachedToLogsDecorator extends TreeDecorator {
	public static final MapCodec<AttachedToLogsDecorator> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				Codec.floatRange(0.0F, 1.0F).fieldOf("probability").forGetter(attachedToLogsDecorator -> attachedToLogsDecorator.probability),
				BlockStateProvider.CODEC.fieldOf("block_provider").forGetter(attachedToLogsDecorator -> attachedToLogsDecorator.blockProvider),
				ExtraCodecs.nonEmptyList(Direction.CODEC.listOf()).fieldOf("directions").forGetter(attachedToLogsDecorator -> attachedToLogsDecorator.directions)
			)
			.apply(instance, AttachedToLogsDecorator::new)
	);
	private final float probability;
	private final BlockStateProvider blockProvider;
	private final List<Direction> directions;

	public AttachedToLogsDecorator(float f, BlockStateProvider blockStateProvider, List<Direction> list) {
		this.probability = f;
		this.blockProvider = blockStateProvider;
		this.directions = list;
	}

	@Override
	public void place(TreeDecorator.Context context) {
		RandomSource randomSource = context.random();

		for (BlockPos blockPos : Util.shuffledCopy(context.logs(), randomSource)) {
			Direction direction = Util.getRandom(this.directions, randomSource);
			BlockPos blockPos2 = blockPos.relative(direction);
			if (randomSource.nextFloat() <= this.probability && context.isAir(blockPos2)) {
				context.setBlock(blockPos2, this.blockProvider.getState(randomSource, blockPos2));
			}
		}
	}

	@Override
	protected TreeDecoratorType<?> type() {
		return TreeDecoratorType.ATTACHED_TO_LOGS;
	}
}
