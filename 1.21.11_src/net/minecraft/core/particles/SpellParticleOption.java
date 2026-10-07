package net.minecraft.core.particles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;

public class SpellParticleOption implements ParticleOptions {
	private final ParticleType<SpellParticleOption> type;
	private final int color;
	private final float power;

	public static MapCodec<SpellParticleOption> codec(ParticleType<SpellParticleOption> particleType) {
		return RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ExtraCodecs.RGB_COLOR_CODEC.optionalFieldOf("color", -1).forGetter(spellParticleOption -> spellParticleOption.color),
					Codec.FLOAT.optionalFieldOf("power", 1.0F).forGetter(spellParticleOption -> spellParticleOption.power)
				)
				.apply(instance, (integer, float_) -> new SpellParticleOption(particleType, integer, float_))
		);
	}

	public static StreamCodec<? super ByteBuf, SpellParticleOption> streamCodec(ParticleType<SpellParticleOption> particleType) {
		return StreamCodec.composite(
			ByteBufCodecs.INT,
			spellParticleOption -> spellParticleOption.color,
			ByteBufCodecs.FLOAT,
			spellParticleOption -> spellParticleOption.power,
			(integer, float_) -> new SpellParticleOption(particleType, integer, float_)
		);
	}

	private SpellParticleOption(ParticleType<SpellParticleOption> particleType, int i, float f) {
		this.type = particleType;
		this.color = i;
		this.power = f;
	}

	@Override
	public ParticleType<SpellParticleOption> getType() {
		return this.type;
	}

	public float getRed() {
		return ARGB.red(this.color) / 255.0F;
	}

	public float getGreen() {
		return ARGB.green(this.color) / 255.0F;
	}

	public float getBlue() {
		return ARGB.blue(this.color) / 255.0F;
	}

	public float getPower() {
		return this.power;
	}

	public static SpellParticleOption create(ParticleType<SpellParticleOption> particleType, int i, float f) {
		return new SpellParticleOption(particleType, i, f);
	}

	public static SpellParticleOption create(ParticleType<SpellParticleOption> particleType, float f, float g, float h, float i) {
		return create(particleType, ARGB.colorFromFloat(1.0F, f, g, h), i);
	}
}
