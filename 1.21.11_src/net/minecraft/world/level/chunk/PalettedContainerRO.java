package net.minecraft.world.level.chunk;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.DataResult;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.LongStream;
import net.minecraft.network.FriendlyByteBuf;

public interface PalettedContainerRO<T> {
	T get(int i, int j, int k);

	void getAll(Consumer<T> consumer);

	void write(FriendlyByteBuf friendlyByteBuf);

	int getSerializedSize();

	@VisibleForTesting
	int bitsPerEntry();

	boolean maybeHas(Predicate<T> predicate);

	void count(PalettedContainer.CountConsumer<T> countConsumer);

	PalettedContainer<T> copy();

	PalettedContainer<T> recreate();

	PalettedContainerRO.PackedData<T> pack(Strategy<T> strategy);

	record PackedData<T>(List<T> paletteEntries, Optional<LongStream> storage, int bitsPerEntry) {
		public static final int UNKNOWN_BITS_PER_ENTRY = -1;

		public PackedData(List<T> list, Optional<LongStream> optional) {
			this(list, optional, -1);
		}
	}

	interface Unpacker<T, C extends PalettedContainerRO<T>> {
		DataResult<C> read(Strategy<T> strategy, PalettedContainerRO.PackedData<T> packedData);
	}
}
