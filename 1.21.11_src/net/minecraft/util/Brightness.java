package net.minecraft.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record Brightness(int block, int sky) {
	public static final Codec<Integer> LIGHT_VALUE_CODEC = ExtraCodecs.intRange(0, 15);
	public static final Codec<Brightness> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(LIGHT_VALUE_CODEC.fieldOf("block").forGetter(Brightness::block), LIGHT_VALUE_CODEC.fieldOf("sky").forGetter(Brightness::sky))
			.apply(instance, Brightness::new)
	);
	public static final Brightness FULL_BRIGHT = new Brightness(15, 15);

	public static int pack(int i, int j) {
		return i << 4 | j << 20;
	}

	public int pack() {
		return pack(this.block, this.sky);
	}

	public static int block(int i) {
		return i >> 4 & 65535;
	}

	public static int sky(int i) {
		return i >> 20 & 65535;
	}

	public static Brightness unpack(int i) {
		return new Brightness(block(i), sky(i));
	}
}
