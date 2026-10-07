package net.minecraft.world.entity.ai.attributes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class AttributeInstance {
	private final Holder<Attribute> attribute;
	private final Map<AttributeModifier.Operation, Map<Identifier, AttributeModifier>> modifiersByOperation = Maps.newEnumMap(AttributeModifier.Operation.class);
	private final Map<Identifier, AttributeModifier> modifierById = new Object2ObjectArrayMap<>();
	private final Map<Identifier, AttributeModifier> permanentModifiers = new Object2ObjectArrayMap<>();
	private double baseValue;
	private boolean dirty = true;
	private double cachedValue;
	private final Consumer<AttributeInstance> onDirty;

	public AttributeInstance(Holder<Attribute> holder, Consumer<AttributeInstance> consumer) {
		this.attribute = holder;
		this.onDirty = consumer;
		this.baseValue = holder.value().getDefaultValue();
	}

	public Holder<Attribute> getAttribute() {
		return this.attribute;
	}

	public double getBaseValue() {
		return this.baseValue;
	}

	public void setBaseValue(double d) {
		if (d != this.baseValue) {
			this.baseValue = d;
			this.setDirty();
		}
	}

	@VisibleForTesting
	Map<Identifier, AttributeModifier> getModifiers(AttributeModifier.Operation operation) {
		return this.modifiersByOperation.computeIfAbsent(operation, operationx -> new Object2ObjectOpenHashMap<>());
	}

	public Set<AttributeModifier> getModifiers() {
		return ImmutableSet.copyOf(this.modifierById.values());
	}

	public Set<AttributeModifier> getPermanentModifiers() {
		return ImmutableSet.copyOf(this.permanentModifiers.values());
	}

	public @Nullable AttributeModifier getModifier(Identifier identifier) {
		return this.modifierById.get(identifier);
	}

	public boolean hasModifier(Identifier identifier) {
		return this.modifierById.get(identifier) != null;
	}

	private void addModifier(AttributeModifier attributeModifier) {
		AttributeModifier attributeModifier2 = this.modifierById.putIfAbsent(attributeModifier.id(), attributeModifier);
		if (attributeModifier2 != null) {
			throw new IllegalArgumentException("Modifier is already applied on this attribute!");
		}

		this.getModifiers(attributeModifier.operation()).put(attributeModifier.id(), attributeModifier);
		this.setDirty();
	}

	public void addOrUpdateTransientModifier(AttributeModifier attributeModifier) {
		AttributeModifier attributeModifier2 = this.modifierById.put(attributeModifier.id(), attributeModifier);
		if (attributeModifier != attributeModifier2) {
			this.getModifiers(attributeModifier.operation()).put(attributeModifier.id(), attributeModifier);
			this.setDirty();
		}
	}

	public void addTransientModifier(AttributeModifier attributeModifier) {
		this.addModifier(attributeModifier);
	}

	public void addOrReplacePermanentModifier(AttributeModifier attributeModifier) {
		this.removeModifier(attributeModifier.id());
		this.addModifier(attributeModifier);
		this.permanentModifiers.put(attributeModifier.id(), attributeModifier);
	}

	public void addPermanentModifier(AttributeModifier attributeModifier) {
		this.addModifier(attributeModifier);
		this.permanentModifiers.put(attributeModifier.id(), attributeModifier);
	}

	public void addPermanentModifiers(Collection<AttributeModifier> collection) {
		for (AttributeModifier attributeModifier : collection) {
			this.addPermanentModifier(attributeModifier);
		}
	}

	protected void setDirty() {
		this.dirty = true;
		this.onDirty.accept(this);
	}

	public void removeModifier(AttributeModifier attributeModifier) {
		this.removeModifier(attributeModifier.id());
	}

	public boolean removeModifier(Identifier identifier) {
		AttributeModifier attributeModifier = this.modifierById.remove(identifier);
		if (attributeModifier == null) {
			return false;
		}

		this.getModifiers(attributeModifier.operation()).remove(identifier);
		this.permanentModifiers.remove(identifier);
		this.setDirty();
		return true;
	}

	public void removeModifiers() {
		for (AttributeModifier attributeModifier : this.getModifiers()) {
			this.removeModifier(attributeModifier);
		}
	}

	public double getValue() {
		if (this.dirty) {
			this.cachedValue = this.calculateValue();
			this.dirty = false;
		}

		return this.cachedValue;
	}

	private double calculateValue() {
		double d = this.getBaseValue();

		for (AttributeModifier attributeModifier : this.getModifiersOrEmpty(AttributeModifier.Operation.ADD_VALUE)) {
			d += attributeModifier.amount();
		}

		double e = d;

		for (AttributeModifier attributeModifier2 : this.getModifiersOrEmpty(AttributeModifier.Operation.ADD_MULTIPLIED_BASE)) {
			e += d * attributeModifier2.amount();
		}

		for (AttributeModifier attributeModifier2 : this.getModifiersOrEmpty(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)) {
			e *= 1.0 + attributeModifier2.amount();
		}

		return this.attribute.value().sanitizeValue(e);
	}

	private Collection<AttributeModifier> getModifiersOrEmpty(AttributeModifier.Operation operation) {
		return this.modifiersByOperation.getOrDefault(operation, Map.of()).values();
	}

	public void replaceFrom(AttributeInstance attributeInstance) {
		this.baseValue = attributeInstance.baseValue;
		this.modifierById.clear();
		this.modifierById.putAll(attributeInstance.modifierById);
		this.permanentModifiers.clear();
		this.permanentModifiers.putAll(attributeInstance.permanentModifiers);
		this.modifiersByOperation.clear();
		attributeInstance.modifiersByOperation
			.forEach((operation, map) -> this.getModifiers(operation).putAll((Map<? extends Identifier, ? extends AttributeModifier>)map));
		this.setDirty();
	}

	public AttributeInstance.Packed pack() {
		return new AttributeInstance.Packed(this.attribute, this.baseValue, List.copyOf(this.permanentModifiers.values()));
	}

	public void apply(AttributeInstance.Packed packed) {
		this.baseValue = packed.baseValue;

		for (AttributeModifier attributeModifier : packed.modifiers) {
			this.modifierById.put(attributeModifier.id(), attributeModifier);
			this.getModifiers(attributeModifier.operation()).put(attributeModifier.id(), attributeModifier);
			this.permanentModifiers.put(attributeModifier.id(), attributeModifier);
		}

		this.setDirty();
	}

	public record Packed(Holder<Attribute> attribute, double baseValue, List<AttributeModifier> modifiers) {
		public static final Codec<AttributeInstance.Packed> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					BuiltInRegistries.ATTRIBUTE.holderByNameCodec().fieldOf("id").forGetter(AttributeInstance.Packed::attribute),
					Codec.DOUBLE.fieldOf("base").orElse(0.0).forGetter(AttributeInstance.Packed::baseValue),
					AttributeModifier.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(AttributeInstance.Packed::modifiers)
				)
				.apply(instance, AttributeInstance.Packed::new)
		);
		public static final Codec<List<AttributeInstance.Packed>> LIST_CODEC = CODEC.listOf();
	}
}
