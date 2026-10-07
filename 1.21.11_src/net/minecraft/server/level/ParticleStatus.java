package net.minecraft.server.level;

import com.mojang.serialization.Codec;
import java.util.function.IntFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ByIdMap;

public enum ParticleStatus {
	ALL(0, "options.particles.all"),
	DECREASED(1, "options.particles.decreased"),
	MINIMAL(2, "options.particles.minimal");

	private static final IntFunction<ParticleStatus> BY_ID = ByIdMap.continuous(particleStatus -> particleStatus.id, values(), ByIdMap.OutOfBoundsStrategy.WRAP);
	public static final Codec<ParticleStatus> LEGACY_CODEC = Codec.INT.xmap(BY_ID::apply, particleStatus -> particleStatus.id);
	private final int id;
	private final Component caption;

	ParticleStatus(final int j, final String string2) {
		this.id = j;
		this.caption = Component.translatable(string2);
	}

	public Component caption() {
		return this.caption;
	}
}
