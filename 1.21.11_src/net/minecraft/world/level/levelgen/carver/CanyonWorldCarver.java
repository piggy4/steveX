package net.minecraft.world.level.levelgen.carver;

import com.mojang.serialization.Codec;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;

public class CanyonWorldCarver extends WorldCarver<CanyonCarverConfiguration> {
	public CanyonWorldCarver(Codec<CanyonCarverConfiguration> codec) {
		super(codec);
	}

	public boolean isStartChunk(CanyonCarverConfiguration canyonCarverConfiguration, RandomSource randomSource) {
		return randomSource.nextFloat() <= canyonCarverConfiguration.probability;
	}

	public boolean carve(
		CarvingContext carvingContext,
		CanyonCarverConfiguration canyonCarverConfiguration,
		ChunkAccess chunkAccess,
		Function<BlockPos, Holder<Biome>> function,
		RandomSource randomSource,
		Aquifer aquifer,
		ChunkPos chunkPos,
		CarvingMask carvingMask
	) {
		int i = (this.getRange() * 2 - 1) * 16;
		double d = chunkPos.getBlockX(randomSource.nextInt(16));
		int j = canyonCarverConfiguration.y.sample(randomSource, carvingContext);
		double e = chunkPos.getBlockZ(randomSource.nextInt(16));
		float f = randomSource.nextFloat() * (float) (Math.PI * 2);
		float g = canyonCarverConfiguration.verticalRotation.sample(randomSource);
		double h = canyonCarverConfiguration.yScale.sample(randomSource);
		float k = canyonCarverConfiguration.shape.thickness.sample(randomSource);
		int l = (int)(i * canyonCarverConfiguration.shape.distanceFactor.sample(randomSource));
		int m = 0;
		this.doCarve(carvingContext, canyonCarverConfiguration, chunkAccess, function, randomSource.nextLong(), aquifer, d, j, e, k, f, g, 0, l, h, carvingMask);
		return true;
	}

	private void doCarve(
		CarvingContext carvingContext,
		CanyonCarverConfiguration canyonCarverConfiguration,
		ChunkAccess chunkAccess,
		Function<BlockPos, Holder<Biome>> function,
		long l,
		Aquifer aquifer,
		double d,
		double e,
		double f,
		float g,
		float h,
		float i,
		int j,
		int k,
		double m,
		CarvingMask carvingMask
	) {
		RandomSource randomSource = RandomSource.create(l);
		float[] fs = this.initWidthFactors(carvingContext, canyonCarverConfiguration, randomSource);
		float n = 0.0F;
		float o = 0.0F;

		for (int p = j; p < k; p++) {
			double q = 1.5 + Mth.sin(p * (float) Math.PI / k) * g;
			double r = q * m;
			q *= canyonCarverConfiguration.shape.horizontalRadiusFactor.sample(randomSource);
			r = this.updateVerticalRadius(canyonCarverConfiguration, randomSource, r, k, p);
			float s = Mth.cos(i);
			float t = Mth.sin(i);
			d += Mth.cos(h) * s;
			e += t;
			f += Mth.sin(h) * s;
			i *= 0.7F;
			i += o * 0.05F;
			h += n * 0.05F;
			o *= 0.8F;
			n *= 0.5F;
			o += (randomSource.nextFloat() - randomSource.nextFloat()) * randomSource.nextFloat() * 2.0F;
			n += (randomSource.nextFloat() - randomSource.nextFloat()) * randomSource.nextFloat() * 4.0F;
			if (randomSource.nextInt(4) != 0) {
				if (!canReach(chunkAccess.getPos(), d, f, p, k, g)) {
					return;
				}

				this.carveEllipsoid(
					carvingContext,
					canyonCarverConfiguration,
					chunkAccess,
					function,
					aquifer,
					d,
					e,
					f,
					q,
					r,
					carvingMask,
					(carvingContextx, dx, ex, fx, ix) -> this.shouldSkip(carvingContextx, fs, dx, ex, fx, ix)
				);
			}
		}
	}

	private float[] initWidthFactors(CarvingContext carvingContext, CanyonCarverConfiguration canyonCarverConfiguration, RandomSource randomSource) {
		int i = carvingContext.getGenDepth();
		float[] fs = new float[i];
		float f = 1.0F;

		for (int j = 0; j < i; j++) {
			if (j == 0 || randomSource.nextInt(canyonCarverConfiguration.shape.widthSmoothness) == 0) {
				f = 1.0F + randomSource.nextFloat() * randomSource.nextFloat();
			}

			fs[j] = f * f;
		}

		return fs;
	}

	private double updateVerticalRadius(CanyonCarverConfiguration canyonCarverConfiguration, RandomSource randomSource, double d, float f, float g) {
		float h = 1.0F - Mth.abs(0.5F - g / f) * 2.0F;
		float i = canyonCarverConfiguration.shape.verticalRadiusDefaultFactor + canyonCarverConfiguration.shape.verticalRadiusCenterFactor * h;
		return i * d * Mth.randomBetween(randomSource, 0.75F, 1.0F);
	}

	private boolean shouldSkip(CarvingContext carvingContext, float[] fs, double d, double e, double f, int i) {
		int j = i - carvingContext.getMinGenY();
		return (d * d + f * f) * fs[j - 1] + e * e / 6.0 >= 1.0;
	}
}
