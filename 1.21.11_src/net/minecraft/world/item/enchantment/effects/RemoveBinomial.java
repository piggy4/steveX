package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.LevelBasedValue;

public record RemoveBinomial(LevelBasedValue chance) implements EnchantmentValueEffect {
	public static final MapCodec<RemoveBinomial> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(LevelBasedValue.CODEC.fieldOf("chance").forGetter(RemoveBinomial::chance)).apply(instance, RemoveBinomial::new)
	);

	@Override
	public float process(int i, RandomSource randomSource, float f) {
		float g = this.chance.calculate(i);
		int j = 0;
		if (!(f <= 128.0F) && !(f * g < 20.0F) && !(f * (1.0F - g) < 20.0F)) {
			double d = Math.floor(f * g);
			double e = Math.sqrt(f * g * (1.0F - g));
			j = (int)Math.round(d + randomSource.nextGaussian() * e);
			j = Math.clamp(j, 0, (int)f);
		} else {
			for (int k = 0; k < f; k++) {
				if (randomSource.nextFloat() < g) {
					j++;
				}
			}
		}

		return f - j;
	}

	@Override
	public MapCodec<RemoveBinomial> codec() {
		return CODEC;
	}
}
