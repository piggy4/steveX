package net.minecraft.world.attribute;

import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.Util;
import net.minecraft.world.attribute.modifier.AttributeModifier;
import org.jspecify.annotations.Nullable;

public final class EnvironmentAttributeMap {
	public static final EnvironmentAttributeMap EMPTY = new EnvironmentAttributeMap(Map.of());
	public static final Codec<EnvironmentAttributeMap> CODEC = Codec.lazyInitialized(
		() -> Codec.dispatchedMap(EnvironmentAttributes.CODEC, Util.memoize(EnvironmentAttributeMap.Entry::createCodec))
			.xmap(EnvironmentAttributeMap::new, environmentAttributeMap -> environmentAttributeMap.entries)
	);
	public static final Codec<EnvironmentAttributeMap> NETWORK_CODEC = CODEC.xmap(EnvironmentAttributeMap::filterSyncable, EnvironmentAttributeMap::filterSyncable);
	public static final Codec<EnvironmentAttributeMap> CODEC_ONLY_POSITIONAL = CODEC.validate(environmentAttributeMap -> {
		List<EnvironmentAttribute<?>> list = environmentAttributeMap.keySet().stream().filter(environmentAttribute -> !environmentAttribute.isPositional()).toList();
		return !list.isEmpty() ? DataResult.error(() -> "The following attributes cannot be positional: " + list) : DataResult.success(environmentAttributeMap);
	});
	final Map<EnvironmentAttribute<?>, EnvironmentAttributeMap.Entry<?, ?>> entries;

	private static EnvironmentAttributeMap filterSyncable(EnvironmentAttributeMap environmentAttributeMap) {
		return new EnvironmentAttributeMap(Map.copyOf(Maps.filterKeys(environmentAttributeMap.entries, EnvironmentAttribute::isSyncable)));
	}

	EnvironmentAttributeMap(Map<EnvironmentAttribute<?>, EnvironmentAttributeMap.Entry<?, ?>> map) {
		this.entries = map;
	}

	public static EnvironmentAttributeMap.Builder builder() {
		return new EnvironmentAttributeMap.Builder();
	}

	public <Value> EnvironmentAttributeMap.@Nullable Entry<Value, ?> get(EnvironmentAttribute<Value> environmentAttribute) {
		return (EnvironmentAttributeMap.Entry<Value, ?>)this.entries.get(environmentAttribute);
	}

	public <Value> Value applyModifier(EnvironmentAttribute<Value> environmentAttribute, Value object) {
		EnvironmentAttributeMap.Entry<Value, ?> entry = this.get(environmentAttribute);
		return entry != null ? entry.applyModifier(object) : object;
	}

	public boolean contains(EnvironmentAttribute<?> environmentAttribute) {
		return this.entries.containsKey(environmentAttribute);
	}

	public Set<EnvironmentAttribute<?>> keySet() {
		return this.entries.keySet();
	}

	@Override
	public boolean equals(Object object) {
		return object == this ? true : object instanceof EnvironmentAttributeMap environmentAttributeMap && this.entries.equals(environmentAttributeMap.entries);
	}

	@Override
	public int hashCode() {
		return this.entries.hashCode();
	}

	@Override
	public String toString() {
		return this.entries.toString();
	}

	public static class Builder {
		private final Map<EnvironmentAttribute<?>, EnvironmentAttributeMap.Entry<?, ?>> entries = new HashMap<>();

		Builder() {
		}

		public EnvironmentAttributeMap.Builder putAll(EnvironmentAttributeMap environmentAttributeMap) {
			this.entries.putAll(environmentAttributeMap.entries);
			return this;
		}

		public <Value, Parameter> EnvironmentAttributeMap.Builder modify(
			EnvironmentAttribute<Value> environmentAttribute, AttributeModifier<Value, Parameter> attributeModifier, Parameter object
		) {
			environmentAttribute.type().checkAllowedModifier(attributeModifier);
			this.entries.put(environmentAttribute, new EnvironmentAttributeMap.Entry<>(object, attributeModifier));
			return this;
		}

		public <Value> EnvironmentAttributeMap.Builder set(EnvironmentAttribute<Value> environmentAttribute, Value object) {
			return this.modify(environmentAttribute, AttributeModifier.override(), object);
		}

		public EnvironmentAttributeMap build() {
			return this.entries.isEmpty() ? EnvironmentAttributeMap.EMPTY : new EnvironmentAttributeMap(Map.copyOf(this.entries));
		}
	}

	public record Entry<Value, Argument>(Argument argument, AttributeModifier<Value, Argument> modifier) {
		private static <Value> Codec<EnvironmentAttributeMap.Entry<Value, ?>> createCodec(EnvironmentAttribute<Value> environmentAttribute) {
			Codec<EnvironmentAttributeMap.Entry<Value, ?>> codec = environmentAttribute.type()
				.modifierCodec()
				.dispatch(
					"modifier",
					EnvironmentAttributeMap.Entry::modifier,
					Util.memoize(attributeModifier -> createFullCodec(environmentAttribute, (AttributeModifier<Value, ?>)attributeModifier))
				);
			return Codec.either(environmentAttribute.valueCodec(), codec)
				.xmap(
					either -> either.map(object -> new EnvironmentAttributeMap.Entry<>(object, AttributeModifier.override()), entry -> entry),
					entry -> entry.modifier == AttributeModifier.override()
						? Either.left((Value)entry.argument())
						: Either.right((EnvironmentAttributeMap.Entry<Value, ?>)entry)
				);
		}

		private static <Value, Argument> MapCodec<EnvironmentAttributeMap.Entry<Value, Argument>> createFullCodec(
			EnvironmentAttribute<Value> environmentAttribute, AttributeModifier<Value, Argument> attributeModifier
		) {
			return RecordCodecBuilder.mapCodec(
				instance -> instance.group(attributeModifier.argumentCodec(environmentAttribute).fieldOf("argument").forGetter(EnvironmentAttributeMap.Entry::argument))
					.apply(instance, object -> new EnvironmentAttributeMap.Entry<>(object, attributeModifier))
			);
		}

		public Value applyModifier(Value object) {
			return this.modifier.apply(object, this.argument);
		}
	}
}
