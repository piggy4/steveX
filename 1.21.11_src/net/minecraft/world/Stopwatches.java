package net.minecraft.world;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

public class Stopwatches extends SavedData {
	private static final Codec<Stopwatches> CODEC = Codec.unboundedMap(Identifier.CODEC, Codec.LONG)
		.fieldOf("stopwatches")
		.codec()
		.xmap(Stopwatches::unpack, Stopwatches::pack);
	public static final SavedDataType<Stopwatches> TYPE = new SavedDataType<>("stopwatches", Stopwatches::new, CODEC, DataFixTypes.SAVED_DATA_STOPWATCHES);
	private final Map<Identifier, Stopwatch> stopwatches = new Object2ObjectOpenHashMap<>();

	private Stopwatches() {
	}

	private static Stopwatches unpack(Map<Identifier, Long> map) {
		Stopwatches stopwatches = new Stopwatches();
		long l = currentTime();
		map.forEach((identifier, long_) -> stopwatches.stopwatches.put(identifier, new Stopwatch(l, long_)));
		return stopwatches;
	}

	private Map<Identifier, Long> pack() {
		long l = currentTime();
		Map<Identifier, Long> map = new TreeMap<>();
		this.stopwatches.forEach((identifier, stopwatch) -> map.put(identifier, stopwatch.elapsedMilliseconds(l)));
		return map;
	}

	public @Nullable Stopwatch get(Identifier identifier) {
		return this.stopwatches.get(identifier);
	}

	public boolean add(Identifier identifier, Stopwatch stopwatch) {
		if (this.stopwatches.putIfAbsent(identifier, stopwatch) == null) {
			this.setDirty();
			return true;
		} else {
			return false;
		}
	}

	public boolean update(Identifier identifier, UnaryOperator<Stopwatch> unaryOperator) {
		if (this.stopwatches.computeIfPresent(identifier, (identifierx, stopwatch) -> unaryOperator.apply(stopwatch)) != null) {
			this.setDirty();
			return true;
		} else {
			return false;
		}
	}

	public boolean remove(Identifier identifier) {
		boolean bl = this.stopwatches.remove(identifier) != null;
		if (bl) {
			this.setDirty();
		}

		return bl;
	}

	@Override
	public boolean isDirty() {
		return super.isDirty() || !this.stopwatches.isEmpty();
	}

	public List<Identifier> ids() {
		return List.copyOf(this.stopwatches.keySet());
	}

	public static long currentTime() {
		return Util.getMillis();
	}
}
