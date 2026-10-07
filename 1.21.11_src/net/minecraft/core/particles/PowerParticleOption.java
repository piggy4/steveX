package net.minecraft.core.particles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public class PowerParticleOption implements ParticleOptions {
	private final ParticleType<PowerParticleOption> type;
	private final float power;

	public static MapCodec<PowerParticleOption> codec(ParticleType<PowerParticleOption> particleType) {
		return Codec.FLOAT
			.xmap(float_ -> new PowerParticleOption(particleType, float_), powerParticleOption -> powerParticleOption.power)
			.optionalFieldOf("power", create(particleType, 1.0F));
	}

	public static StreamCodec<? super ByteBuf, PowerParticleOption> streamCodec(ParticleType<PowerParticleOption> particleType) {
		return ByteBufCodecs.FLOAT.map(float_ -> new PowerParticleOption(particleType, float_), powerParticleOption -> powerParticleOption.power);
	}

	private PowerParticleOption(ParticleType<PowerParticleOption> particleType, float f) {
		this.type = particleType;
		this.power = f;
	}

	@Override
	public ParticleType<PowerParticleOption> getType() {
		return this.type;
	}

	public float getPower() {
		return this.power;
	}

	public static PowerParticleOption create(ParticleType<PowerParticleOption> particleType, float f) {
		return new PowerParticleOption(particleType, f);
	}
}
