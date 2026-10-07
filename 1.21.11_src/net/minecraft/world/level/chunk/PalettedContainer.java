package net.minecraft.world.level.chunk;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.LongStream;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.BitStorage;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ThreadingDetector;
import net.minecraft.util.ZeroBitStorage;
import org.jspecify.annotations.Nullable;

public class PalettedContainer<T> implements PaletteResize<T>, PalettedContainerRO<T> {
	private static final int MIN_PALETTE_BITS = 0;
	private volatile PalettedContainer.Data<T> data;
	private final Strategy<T> strategy;
	private final ThreadingDetector threadingDetector = new ThreadingDetector("PalettedContainer");

	public void acquire() {
		this.threadingDetector.checkAndLock();
	}

	public void release() {
		this.threadingDetector.checkAndUnlock();
	}

	public static <T> Codec<PalettedContainer<T>> codecRW(Codec<T> codec, Strategy<T> strategy, T object) {
		PalettedContainerRO.Unpacker<T, PalettedContainer<T>> unpacker = PalettedContainer::unpack;
		return codec(codec, strategy, object, unpacker);
	}

	public static <T> Codec<PalettedContainerRO<T>> codecRO(Codec<T> codec, Strategy<T> strategy, T object) {
		PalettedContainerRO.Unpacker<T, PalettedContainerRO<T>> unpacker = (strategyx, packedData) -> unpack(strategyx, packedData)
			.map(palettedContainer -> (PalettedContainerRO<T>)palettedContainer);
		return codec(codec, strategy, object, unpacker);
	}

	private static <T, C extends PalettedContainerRO<T>> Codec<C> codec(
		Codec<T> codec, Strategy<T> strategy, T object, PalettedContainerRO.Unpacker<T, C> unpacker
	) {
		return RecordCodecBuilder.<PalettedContainerRO.PackedData>create(
				instance -> instance.group(
						codec.mapResult(ExtraCodecs.orElsePartial(object)).listOf().fieldOf("palette").forGetter(PalettedContainerRO.PackedData::paletteEntries),
						Codec.LONG_STREAM.lenientOptionalFieldOf("data").forGetter(PalettedContainerRO.PackedData::storage)
					)
					.apply(instance, PalettedContainerRO.PackedData::new)
			)
			.comapFlatMap(
				packedData -> unpacker.read(strategy, (PalettedContainerRO.PackedData<T>)packedData), palettedContainerRO -> palettedContainerRO.pack(strategy)
			);
	}

	private PalettedContainer(Strategy<T> strategy, Configuration configuration, BitStorage bitStorage, Palette<T> palette) {
		this.strategy = strategy;
		this.data = new PalettedContainer.Data<>(configuration, bitStorage, palette);
	}

	private PalettedContainer(PalettedContainer<T> palettedContainer) {
		this.strategy = palettedContainer.strategy;
		this.data = palettedContainer.data.copy();
	}

	public PalettedContainer(T object, Strategy<T> strategy) {
		this.strategy = strategy;
		this.data = this.createOrReuseData(null, 0);
		this.data.palette.idFor(object, this);
	}

	private PalettedContainer.Data<T> createOrReuseData(PalettedContainer.@Nullable Data<T> data, int i) {
		Configuration configuration = this.strategy.getConfigurationForBitCount(i);
		if (data != null && configuration.equals(data.configuration())) {
			return data;
		}

		BitStorage bitStorage = configuration.bitsInMemory() == 0
			? new ZeroBitStorage(this.strategy.entryCount())
			: new SimpleBitStorage(configuration.bitsInMemory(), this.strategy.entryCount());
		Palette<T> palette = configuration.createPalette(this.strategy, List.of());
		return new PalettedContainer.Data<>(configuration, bitStorage, palette);
	}

	@Override
	public int onResize(int i, T object) {
		PalettedContainer.Data<T> data = this.data;
		PalettedContainer.Data<T> data2 = this.createOrReuseData(data, i);
		data2.copyFrom(data.palette, data.storage);
		this.data = data2;
		return data2.palette.idFor(object, PaletteResize.noResizeExpected());
	}

	public T getAndSet(int i, int j, int k, T object) {
		this.acquire();

		try {
			return this.getAndSet(this.strategy.getIndex(i, j, k), object);
		} finally {
			this.release();
		}
	}

	public T getAndSetUnchecked(int i, int j, int k, T object) {
		return this.getAndSet(this.strategy.getIndex(i, j, k), object);
	}

	private T getAndSet(int i, T object) {
		int j = this.data.palette.idFor(object, this);
		int k = this.data.storage.getAndSet(i, j);
		return this.data.palette.valueFor(k);
	}

	public void set(int i, int j, int k, T object) {
		this.acquire();

		try {
			this.set(this.strategy.getIndex(i, j, k), object);
		} finally {
			this.release();
		}
	}

	private void set(int i, T object) {
		int j = this.data.palette.idFor(object, this);
		this.data.storage.set(i, j);
	}

	@Override
	public T get(int i, int j, int k) {
		return this.get(this.strategy.getIndex(i, j, k));
	}

	protected T get(int i) {
		PalettedContainer.Data<T> data = this.data;
		return data.palette.valueFor(data.storage.get(i));
	}

	@Override
	public void getAll(Consumer<T> consumer) {
		Palette<T> palette = this.data.palette();
		IntSet intSet = new IntArraySet();
		this.data.storage.getAll(intSet::add);
		intSet.forEach(i -> consumer.accept(palette.valueFor(i)));
	}

	public void read(FriendlyByteBuf friendlyByteBuf) {
		this.acquire();

		try {
			int i = friendlyByteBuf.readByte();
			PalettedContainer.Data<T> data = this.createOrReuseData(this.data, i);
			data.palette.read(friendlyByteBuf, this.strategy.globalMap());
			friendlyByteBuf.readFixedSizeLongArray(data.storage.getRaw());
			this.data = data;
		} finally {
			this.release();
		}
	}

	@Override
	public void write(FriendlyByteBuf friendlyByteBuf) {
		this.acquire();

		try {
			this.data.write(friendlyByteBuf, this.strategy.globalMap());
		} finally {
			this.release();
		}
	}

	@VisibleForTesting
	public static <T> DataResult<PalettedContainer<T>> unpack(Strategy<T> strategy, PalettedContainerRO.PackedData<T> packedData) {
		List<T> list = packedData.paletteEntries();
		int i = strategy.entryCount();
		Configuration configuration = strategy.getConfigurationForPaletteSize(list.size());
		int j = configuration.bitsInStorage();
		if (packedData.bitsPerEntry() != -1 && j != packedData.bitsPerEntry()) {
			return DataResult.error(() -> "Invalid bit count, calculated " + j + ", but container declared " + packedData.bitsPerEntry());
		}

		BitStorage bitStorage;
		Palette<T> palette;
		if (configuration.bitsInMemory() == 0) {
			palette = configuration.createPalette(strategy, list);
			bitStorage = new ZeroBitStorage(i);
		} else {
			Optional<LongStream> optional = packedData.storage();
			if (optional.isEmpty()) {
				return DataResult.error(() -> "Missing values for non-zero storage");
			}

			long[] ls = optional.get().toArray();

			try {
				if (!configuration.alwaysRepack() && configuration.bitsInMemory() == j) {
					palette = configuration.createPalette(strategy, list);
					bitStorage = new SimpleBitStorage(configuration.bitsInMemory(), i, ls);
				} else {
					Palette<T> palette2 = new HashMapPalette<>(j, list);
					SimpleBitStorage simpleBitStorage = new SimpleBitStorage(j, i, ls);
					Palette<T> palette3 = configuration.createPalette(strategy, list);
					int[] is = reencodeContents(simpleBitStorage, palette2, palette3);
					palette = palette3;
					bitStorage = new SimpleBitStorage(configuration.bitsInMemory(), i, is);
				}
			} catch (SimpleBitStorage.InitializationException initializationException) {
				return DataResult.error(() -> "Failed to read PalettedContainer: " + initializationException.getMessage());
			}
		}

		return DataResult.success(new PalettedContainer<>(strategy, configuration, bitStorage, palette));
	}

	@Override
	public PalettedContainerRO.PackedData<T> pack(Strategy<T> strategy) {
		this.acquire();

		try {
			BitStorage bitStorage = this.data.storage;
			Palette<T> palette = this.data.palette;
			HashMapPalette<T> hashMapPalette = new HashMapPalette<>(bitStorage.getBits());
			int i = strategy.entryCount();
			int[] is = reencodeContents(bitStorage, palette, hashMapPalette);
			Configuration configuration = strategy.getConfigurationForPaletteSize(hashMapPalette.getSize());
			int j = configuration.bitsInStorage();
			Optional<LongStream> optional;
			if (j != 0) {
				SimpleBitStorage simpleBitStorage = new SimpleBitStorage(j, i, is);
				optional = Optional.of(Arrays.stream(simpleBitStorage.getRaw()));
			} else {
				optional = Optional.empty();
			}

			return new PalettedContainerRO.PackedData<>(hashMapPalette.getEntries(), optional, j);
		} finally {
			this.release();
		}
	}

	private static <T> int[] reencodeContents(BitStorage bitStorage, Palette<T> palette, Palette<T> palette2) {
		int[] is = new int[bitStorage.getSize()];
		bitStorage.unpack(is);
		PaletteResize<T> paletteResize = PaletteResize.noResizeExpected();
		int i = -1;
		int j = -1;

		for (int k = 0; k < is.length; k++) {
			int l = is[k];
			if (l != i) {
				i = l;
				j = palette2.idFor(palette.valueFor(l), paletteResize);
			}

			is[k] = j;
		}

		return is;
	}

	@Override
	public int getSerializedSize() {
		return this.data.getSerializedSize(this.strategy.globalMap());
	}

	@Override
	public int bitsPerEntry() {
		return this.data.storage().getBits();
	}

	@Override
	public boolean maybeHas(Predicate<T> predicate) {
		return this.data.palette.maybeHas(predicate);
	}

	@Override
	public PalettedContainer<T> copy() {
		return new PalettedContainer<>(this);
	}

	@Override
	public PalettedContainer<T> recreate() {
		return new PalettedContainer<>(this.data.palette.valueFor(0), this.strategy);
	}

	@Override
	public void count(PalettedContainer.CountConsumer<T> countConsumer) {
		if (this.data.palette.getSize() == 1) {
			countConsumer.accept(this.data.palette.valueFor(0), this.data.storage.getSize());
		} else {
			Int2IntOpenHashMap int2IntOpenHashMap = new Int2IntOpenHashMap();
			this.data.storage.getAll(i -> int2IntOpenHashMap.addTo(i, 1));
			int2IntOpenHashMap.int2IntEntrySet().forEach(entry -> countConsumer.accept(this.data.palette.valueFor(entry.getIntKey()), entry.getIntValue()));
		}
	}

	@FunctionalInterface
	public interface CountConsumer<T> {
		void accept(T object, int i);
	}

	record Data<T>(Configuration configuration, BitStorage storage, Palette<T> palette) {

		public void copyFrom(Palette<T> palette, BitStorage bitStorage) {
			PaletteResize<T> paletteResize = PaletteResize.noResizeExpected();

			for (int i = 0; i < bitStorage.getSize(); i++) {
				T object = palette.valueFor(bitStorage.get(i));
				this.storage.set(i, this.palette.idFor(object, paletteResize));
			}
		}

		public int getSerializedSize(IdMap<T> idMap) {
			return 1 + this.palette.getSerializedSize(idMap) + this.storage.getRaw().length * 8;
		}

		public void write(FriendlyByteBuf friendlyByteBuf, IdMap<T> idMap) {
			friendlyByteBuf.writeByte(this.storage.getBits());
			this.palette.write(friendlyByteBuf, idMap);
			friendlyByteBuf.writeFixedSizeLongArray(this.storage.getRaw());
		}

		public PalettedContainer.Data<T> copy() {
			return new PalettedContainer.Data<>(this.configuration, this.storage.copy(), this.palette.copy());
		}
	}
}
