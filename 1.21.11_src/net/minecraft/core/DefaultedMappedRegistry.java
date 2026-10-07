package net.minecraft.core;

import com.mojang.serialization.Lifecycle;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

public class DefaultedMappedRegistry<T> extends MappedRegistry<T> implements DefaultedRegistry<T> {
	private final Identifier defaultKey;
	private Holder.Reference<T> defaultValue;

	public DefaultedMappedRegistry(String string, ResourceKey<? extends Registry<T>> resourceKey, Lifecycle lifecycle, boolean bl) {
		super(resourceKey, lifecycle, bl);
		this.defaultKey = Identifier.parse(string);
	}

	@Override
	public Holder.Reference<T> register(ResourceKey<T> resourceKey, T object, RegistrationInfo registrationInfo) {
		Holder.Reference<T> reference = super.register(resourceKey, object, registrationInfo);
		if (this.defaultKey.equals(resourceKey.identifier())) {
			this.defaultValue = reference;
		}

		return reference;
	}

	@Override
	public int getId(@Nullable T object) {
		int i = super.getId(object);
		return i == -1 ? super.getId(this.defaultValue.value()) : i;
	}

	@Override
	public Identifier getKey(T object) {
		Identifier identifier = super.getKey(object);
		return identifier == null ? this.defaultKey : identifier;
	}

	@Override
	public T getValue(@Nullable Identifier identifier) {
		T object = super.getValue(identifier);
		return object == null ? this.defaultValue.value() : object;
	}

	@Override
	public Optional<T> getOptional(@Nullable Identifier identifier) {
		return Optional.ofNullable(super.getValue(identifier));
	}

	@Override
	public Optional<Holder.Reference<T>> getAny() {
		return Optional.ofNullable(this.defaultValue);
	}

	@Override
	public T byId(int i) {
		T object = super.byId(i);
		return object == null ? this.defaultValue.value() : object;
	}

	@Override
	public Optional<Holder.Reference<T>> getRandom(RandomSource randomSource) {
		return super.getRandom(randomSource).or(() -> Optional.of(this.defaultValue));
	}

	@Override
	public Identifier getDefaultKey() {
		return this.defaultKey;
	}
}
