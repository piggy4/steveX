package net.minecraft.world.ticks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.Hash.Strategy;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public record SavedTick<T>(T type, BlockPos pos, int delay, TickPriority priority) {
	public static final Strategy<SavedTick<?>> UNIQUE_TICK_HASH = new Strategy<SavedTick<?>>() {
		public int hashCode(SavedTick<?> savedTick) {
			return 31 * savedTick.pos().hashCode() + savedTick.type().hashCode();
		}

		public boolean equals(@Nullable SavedTick<?> savedTick, @Nullable SavedTick<?> savedTick2) {
			if (savedTick == savedTick2) {
				return true;
			} else {
				return savedTick != null && savedTick2 != null ? savedTick.type() == savedTick2.type() && savedTick.pos().equals(savedTick2.pos()) : false;
			}
		}
	};

	public static <T> Codec<SavedTick<T>> codec(Codec<T> codec) {
		MapCodec<BlockPos> mapCodec = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.INT.fieldOf("x").forGetter(Vec3i::getX), Codec.INT.fieldOf("y").forGetter(Vec3i::getY), Codec.INT.fieldOf("z").forGetter(Vec3i::getZ)
				)
				.apply(instance, BlockPos::new)
		);
		return RecordCodecBuilder.create(
			instance -> instance.group(
					codec.fieldOf("i").forGetter(SavedTick::type),
					mapCodec.forGetter(SavedTick::pos),
					Codec.INT.fieldOf("t").forGetter(SavedTick::delay),
					TickPriority.CODEC.fieldOf("p").forGetter(SavedTick::priority)
				)
				.apply(instance, SavedTick::new)
		);
	}

	public static <T> List<SavedTick<T>> filterTickListForChunk(List<SavedTick<T>> list, ChunkPos chunkPos) {
		long l = chunkPos.toLong();
		return list.stream().filter(savedTick -> ChunkPos.asLong(savedTick.pos()) == l).toList();
	}

	public ScheduledTick<T> unpack(long l, long m) {
		return new ScheduledTick<>(this.type, this.pos, l + this.delay, this.priority, m);
	}

	public static <T> SavedTick<T> probe(T object, BlockPos blockPos) {
		return new SavedTick<>(object, blockPos, 0, TickPriority.NORMAL);
	}
}
