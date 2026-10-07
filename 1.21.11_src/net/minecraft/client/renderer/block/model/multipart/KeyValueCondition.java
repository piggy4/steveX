package net.minecraft.client.renderer.block.model.multipart;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public record KeyValueCondition(Map<String, KeyValueCondition.Terms> tests) implements Condition {
	static final Logger LOGGER = LogUtils.getLogger();
	public static final Codec<KeyValueCondition> CODEC = ExtraCodecs.nonEmptyMap(Codec.unboundedMap(Codec.STRING, KeyValueCondition.Terms.CODEC))
		.xmap(KeyValueCondition::new, KeyValueCondition::tests);

	@Override
	public <O, S extends StateHolder<O, S>> Predicate<S> instantiate(StateDefinition<O, S> stateDefinition) {
		List<Predicate<S>> list = new ArrayList<>(this.tests.size());
		this.tests.forEach((string, terms) -> list.add(instantiate(stateDefinition, string, terms)));
		return Util.allOf(list);
	}

	private static <O, S extends StateHolder<O, S>> Predicate<S> instantiate(StateDefinition<O, S> stateDefinition, String string, KeyValueCondition.Terms terms) {
		Property<?> property = stateDefinition.getProperty(string);
		if (property == null) {
			throw new IllegalArgumentException(String.format(Locale.ROOT, "Unknown property '%s' on '%s'", string, stateDefinition.getOwner()));
		} else {
			return terms.instantiate(stateDefinition.getOwner(), property);
		}
	}

	@Environment(EnvType.CLIENT)
	public record Term(String value, boolean negated) {
		private static final String NEGATE = "!";

		public Term {
			if (value.isEmpty()) {
				throw new IllegalArgumentException("Empty term");
			}
		}

		public static KeyValueCondition.Term parse(String string) {
			return string.startsWith("!") ? new KeyValueCondition.Term(string.substring(1), true) : new KeyValueCondition.Term(string, false);
		}

		@Override
		public String toString() {
			return this.negated ? "!" + this.value : this.value;
		}
	}

	@Environment(EnvType.CLIENT)
	public record Terms(List<KeyValueCondition.Term> entries) {
		private static final char SEPARATOR = '|';
		private static final Joiner JOINER = Joiner.on('|');
		private static final Splitter SPLITTER = Splitter.on('|');
		private static final Codec<String> LEGACY_REPRESENTATION_CODEC = Codec.either(Codec.INT, Codec.BOOL)
			.flatComapMap(either -> either.map(String::valueOf, String::valueOf), string -> DataResult.error(() -> "This codec can't be used for encoding"));
		public static final Codec<KeyValueCondition.Terms> CODEC = Codec.withAlternative(Codec.STRING, LEGACY_REPRESENTATION_CODEC)
			.comapFlatMap(KeyValueCondition.Terms::parse, KeyValueCondition.Terms::toString);

		public Terms {
			if (entries.isEmpty()) {
				throw new IllegalArgumentException("Empty value for property");
			}
		}

		public static DataResult<KeyValueCondition.Terms> parse(String string) {
			List<KeyValueCondition.Term> list = SPLITTER.splitToStream(string).map(KeyValueCondition.Term::parse).toList();
			if (list.isEmpty()) {
				return DataResult.error(() -> "Empty value for property");
			}

			for (KeyValueCondition.Term term : list) {
				if (term.value.isEmpty()) {
					return DataResult.error(() -> "Empty term in value '" + string + "'");
				}
			}

			return DataResult.success(new KeyValueCondition.Terms(list));
		}

		@Override
		public String toString() {
			return JOINER.join(this.entries);
		}

		public <O, S extends StateHolder<O, S>, T extends Comparable<T>> Predicate<S> instantiate(O object, Property<T> property) {
			Predicate<T> predicate = Util.anyOf(Lists.transform(this.entries, term -> this.instantiate(object, property, term)));
			List<T> list = new ArrayList<>(property.getPossibleValues());
			int i = list.size();
			list.removeIf(predicate.negate());
			int j = list.size();
			if (j == 0) {
				KeyValueCondition.LOGGER.warn("Condition {} for property {} on {} is always false", this, property.getName(), object);
				return stateHolder -> false;
			}

			int k = i - j;
			if (k == 0) {
				KeyValueCondition.LOGGER.warn("Condition {} for property {} on {} is always true", this, property.getName(), object);
				return stateHolder -> true;
			}

			boolean bl;
			List<T> list2;
			if (j <= k) {
				bl = false;
				list2 = list;
			} else {
				bl = true;
				List<T> list3 = new ArrayList<>(property.getPossibleValues());
				list3.removeIf(predicate);
				list2 = list3;
			}

			if (list2.size() == 1) {
				T comparable = (T)list2.getFirst();
				return stateHolder -> {
					T comparable2 = stateHolder.getValue(property);
					return comparable.equals(comparable2) ^ bl;
				};
			} else {
				return stateHolder -> {
					T comparablex = stateHolder.getValue(property);
					return list2.contains(comparablex) ^ bl;
				};
			}
		}

		private <T extends Comparable<T>> T getValueOrThrow(Object object, Property<T> property, String string) {
			Optional<T> optional = property.getValue(string);
			if (optional.isEmpty()) {
				throw new RuntimeException(String.format(Locale.ROOT, "Unknown value '%s' for property '%s' on '%s' in '%s'", string, property, object, this));
			} else {
				return optional.get();
			}
		}

		private <T extends Comparable<T>> Predicate<T> instantiate(Object object, Property<T> property, KeyValueCondition.Term term) {
			T comparable = this.getValueOrThrow(object, property, term.value);
			return term.negated ? comparable2 -> !comparable2.equals(comparable) : comparable2 -> comparable2.equals(comparable);
		}
	}
}
