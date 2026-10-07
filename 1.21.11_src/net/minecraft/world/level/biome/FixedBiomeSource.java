package net.minecraft.world.level.biome;

import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import org.jspecify.annotations.Nullable;

public class FixedBiomeSource extends BiomeSource implements BiomeManager.NoiseBiomeSource {
	public static final MapCodec<FixedBiomeSource> CODEC = Biome.CODEC
		.fieldOf("biome")
		.xmap(FixedBiomeSource::new, fixedBiomeSource -> fixedBiomeSource.biome)
		.stable();
	private final Holder<Biome> biome;

	public FixedBiomeSource(Holder<Biome> holder) {
		this.biome = holder;
	}

	@Override
	protected Stream<Holder<Biome>> collectPossibleBiomes() {
		return Stream.of(this.biome);
	}

	@Override
	protected MapCodec<? extends BiomeSource> codec() {
		return CODEC;
	}

	@Override
	public Holder<Biome> getNoiseBiome(int i, int j, int k, Climate.Sampler sampler) {
		return this.biome;
	}

	@Override
	public Holder<Biome> getNoiseBiome(int i, int j, int k) {
		return this.biome;
	}

	@Override
	public @Nullable Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(
		int i, int j, int k, int l, int m, Predicate<Holder<Biome>> predicate, RandomSource randomSource, boolean bl, Climate.Sampler sampler
	) {
		if (predicate.test(this.biome)) {
			return bl
				? Pair.of(new BlockPos(i, j, k), this.biome)
				: Pair.of(new BlockPos(i - l + randomSource.nextInt(l * 2 + 1), j, k - l + randomSource.nextInt(l * 2 + 1)), this.biome);
		} else {
			return null;
		}
	}

	@Override
	public @Nullable Pair<BlockPos, Holder<Biome>> findClosestBiome3d(
		BlockPos blockPos, int i, int j, int k, Predicate<Holder<Biome>> predicate, Climate.Sampler sampler, LevelReader levelReader
	) {
		return predicate.test(this.biome)
			? Pair.of(blockPos.atY(Mth.clamp(blockPos.getY(), levelReader.getMinY() + 1, levelReader.getMaxY() + 1)), this.biome)
			: null;
	}

	@Override
	public Set<Holder<Biome>> getBiomesWithin(int i, int j, int k, int l, Climate.Sampler sampler) {
		return Sets.newHashSet(Set.of(this.biome));
	}
}
