package net.minecraft.core;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Util;

public record Rotations(float x, float y, float z) {
	public static final Codec<Rotations> CODEC = Codec.FLOAT
		.listOf()
		.comapFlatMap(
			list -> Util.fixedSize((List<Float>)list, 3).map(listx -> new Rotations((Float)listx.get(0), (Float)listx.get(1), (Float)listx.get(2))),
			rotations -> List.of(rotations.x(), rotations.y(), rotations.z())
		);
	public static final StreamCodec<ByteBuf, Rotations> STREAM_CODEC = new StreamCodec<ByteBuf, Rotations>() {
		public Rotations decode(ByteBuf byteBuf) {
			return new Rotations(byteBuf.readFloat(), byteBuf.readFloat(), byteBuf.readFloat());
		}

		public void encode(ByteBuf byteBuf, Rotations rotations) {
			byteBuf.writeFloat(rotations.x);
			byteBuf.writeFloat(rotations.y);
			byteBuf.writeFloat(rotations.z);
		}
	};

	public Rotations {
		x = !Float.isInfinite(x) && !Float.isNaN(x) ? x % 360.0F : 0.0F;
		y = !Float.isInfinite(y) && !Float.isNaN(y) ? y % 360.0F : 0.0F;
		z = !Float.isInfinite(z) && !Float.isNaN(z) ? z % 360.0F : 0.0F;
	}
}
