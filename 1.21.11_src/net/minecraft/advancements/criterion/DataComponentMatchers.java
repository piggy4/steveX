package net.minecraft.advancements.criterion;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record DataComponentMatchers(DataComponentExactPredicate exact, Map<DataComponentPredicate.Type<?>, DataComponentPredicate> partial)
	implements Predicate<DataComponentGetter> {
	public static final DataComponentMatchers ANY = new DataComponentMatchers(DataComponentExactPredicate.EMPTY, Map.of());
	public static final MapCodec<DataComponentMatchers> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				DataComponentExactPredicate.CODEC.optionalFieldOf("components", DataComponentExactPredicate.EMPTY).forGetter(DataComponentMatchers::exact),
				DataComponentPredicate.CODEC.optionalFieldOf("predicates", Map.of()).forGetter(DataComponentMatchers::partial)
			)
			.apply(instance, DataComponentMatchers::new)
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, DataComponentMatchers> STREAM_CODEC = StreamCodec.composite(
		DataComponentExactPredicate.STREAM_CODEC,
		DataComponentMatchers::exact,
		DataComponentPredicate.STREAM_CODEC,
		DataComponentMatchers::partial,
		DataComponentMatchers::new
	);

	public boolean test(DataComponentGetter dataComponentGetter) {
		if (!this.exact.test(dataComponentGetter)) {
			return false;
		}

		for (DataComponentPredicate dataComponentPredicate : this.partial.values()) {
			if (!dataComponentPredicate.matches(dataComponentGetter)) {
				return false;
			}
		}

		return true;
	}

	public boolean isEmpty() {
		return this.exact.isEmpty() && this.partial.isEmpty();
	}

	public static class Builder {
		private DataComponentExactPredicate exact = DataComponentExactPredicate.EMPTY;
		private final ImmutableMap.Builder<DataComponentPredicate.Type<?>, DataComponentPredicate> partial = ImmutableMap.builder();

		private Builder() {
		}

		public static DataComponentMatchers.Builder components() {
			return new DataComponentMatchers.Builder();
		}

		public <T extends DataComponentType<?>> DataComponentMatchers.Builder any(DataComponentType<?> dataComponentType) {
			DataComponentPredicate.AnyValueType anyValueType = DataComponentPredicate.AnyValueType.create(dataComponentType);
			this.partial.put(anyValueType, anyValueType.predicate());
			return this;
		}

		public <T extends DataComponentPredicate> DataComponentMatchers.Builder partial(DataComponentPredicate.Type<T> type, T dataComponentPredicate) {
			this.partial.put(type, dataComponentPredicate);
			return this;
		}

		public DataComponentMatchers.Builder exact(DataComponentExactPredicate dataComponentExactPredicate) {
			this.exact = dataComponentExactPredicate;
			return this;
		}

		public DataComponentMatchers build() {
			return new DataComponentMatchers(this.exact, this.partial.buildOrThrow());
		}
	}
}
