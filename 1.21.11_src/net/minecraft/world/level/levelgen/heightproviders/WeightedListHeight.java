package net.minecraft.world.level.levelgen.heightproviders;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

public class WeightedListHeight extends HeightProvider {
	public static final MapCodec<WeightedListHeight> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				WeightedList.nonEmptyCodec(HeightProvider.CODEC).fieldOf("distribution").forGetter(weightedListHeight -> weightedListHeight.distribution)
			)
			.apply(instance, WeightedListHeight::new)
	);
	private final WeightedList<HeightProvider> distribution;

	public WeightedListHeight(WeightedList<HeightProvider> weightedList) {
		this.distribution = weightedList;
	}

	@Override
	public int sample(RandomSource randomSource, WorldGenerationContext worldGenerationContext) {
		return this.distribution.getRandomOrThrow(randomSource).sample(randomSource, worldGenerationContext);
	}

	@Override
	public HeightProviderType<?> getType() {
		return HeightProviderType.WEIGHTED_LIST;
	}
}
