package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import org.jspecify.annotations.Nullable;

public record CustomModelData(List<Float> floats, List<Boolean> flags, List<String> strings, List<Integer> colors) {
	public static final CustomModelData EMPTY = new CustomModelData(List.of(), List.of(), List.of(), List.of());
	public static final Codec<CustomModelData> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				Codec.FLOAT.listOf().optionalFieldOf("floats", List.of()).forGetter(CustomModelData::floats),
				Codec.BOOL.listOf().optionalFieldOf("flags", List.of()).forGetter(CustomModelData::flags),
				Codec.STRING.listOf().optionalFieldOf("strings", List.of()).forGetter(CustomModelData::strings),
				ExtraCodecs.RGB_COLOR_CODEC.listOf().optionalFieldOf("colors", List.of()).forGetter(CustomModelData::colors)
			)
			.apply(instance, CustomModelData::new)
	);
	public static final StreamCodec<ByteBuf, CustomModelData> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()),
		CustomModelData::floats,
		ByteBufCodecs.BOOL.apply(ByteBufCodecs.list()),
		CustomModelData::flags,
		ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
		CustomModelData::strings,
		ByteBufCodecs.INT.apply(ByteBufCodecs.list()),
		CustomModelData::colors,
		CustomModelData::new
	);

	private static <T> @Nullable T getSafe(List<T> list, int i) {
		return i >= 0 && i < list.size() ? list.get(i) : null;
	}

	public @Nullable Float getFloat(int i) {
		return getSafe(this.floats, i);
	}

	public @Nullable Boolean getBoolean(int i) {
		return getSafe(this.flags, i);
	}

	public @Nullable String getString(int i) {
		return getSafe(this.strings, i);
	}

	public @Nullable Integer getColor(int i) {
		return getSafe(this.colors, i);
	}
}
