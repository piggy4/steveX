package net.minecraft.world.entity.ai.attributes;

import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class AttributeMap {
	private final Map<Holder<Attribute>, AttributeInstance> attributes = new Object2ObjectOpenHashMap<>();
	private final Set<AttributeInstance> attributesToSync = new ObjectOpenHashSet<>();
	private final Set<AttributeInstance> attributesToUpdate = new ObjectOpenHashSet<>();
	private final AttributeSupplier supplier;

	public AttributeMap(AttributeSupplier attributeSupplier) {
		this.supplier = attributeSupplier;
	}

	private void onAttributeModified(AttributeInstance attributeInstance) {
		this.attributesToUpdate.add(attributeInstance);
		if (attributeInstance.getAttribute().value().isClientSyncable()) {
			this.attributesToSync.add(attributeInstance);
		}
	}

	public Set<AttributeInstance> getAttributesToSync() {
		return this.attributesToSync;
	}

	public Set<AttributeInstance> getAttributesToUpdate() {
		return this.attributesToUpdate;
	}

	public Collection<AttributeInstance> getSyncableAttributes() {
		return this.attributes
			.values()
			.stream()
			.filter(attributeInstance -> attributeInstance.getAttribute().value().isClientSyncable())
			.collect(Collectors.toList());
	}

	public @Nullable AttributeInstance getInstance(Holder<Attribute> holder) {
		return this.attributes.computeIfAbsent(holder, holderx -> this.supplier.createInstance(this::onAttributeModified, holderx));
	}

	public boolean hasAttribute(Holder<Attribute> holder) {
		return this.attributes.get(holder) != null || this.supplier.hasAttribute(holder);
	}

	public boolean hasModifier(Holder<Attribute> holder, Identifier identifier) {
		AttributeInstance attributeInstance = this.attributes.get(holder);
		return attributeInstance != null ? attributeInstance.getModifier(identifier) != null : this.supplier.hasModifier(holder, identifier);
	}

	public double getValue(Holder<Attribute> holder) {
		AttributeInstance attributeInstance = this.attributes.get(holder);
		return attributeInstance != null ? attributeInstance.getValue() : this.supplier.getValue(holder);
	}

	public double getBaseValue(Holder<Attribute> holder) {
		AttributeInstance attributeInstance = this.attributes.get(holder);
		return attributeInstance != null ? attributeInstance.getBaseValue() : this.supplier.getBaseValue(holder);
	}

	public double getModifierValue(Holder<Attribute> holder, Identifier identifier) {
		AttributeInstance attributeInstance = this.attributes.get(holder);
		return attributeInstance != null ? attributeInstance.getModifier(identifier).amount() : this.supplier.getModifierValue(holder, identifier);
	}

	public void addTransientAttributeModifiers(Multimap<Holder<Attribute>, AttributeModifier> multimap) {
		multimap.forEach((holder, attributeModifier) -> {
			AttributeInstance attributeInstance = this.getInstance((Holder<Attribute>)holder);
			if (attributeInstance != null) {
				attributeInstance.removeModifier(attributeModifier.id());
				attributeInstance.addTransientModifier(attributeModifier);
			}
		});
	}

	public void removeAttributeModifiers(Multimap<Holder<Attribute>, AttributeModifier> multimap) {
		multimap.asMap().forEach((holder, collection) -> {
			AttributeInstance attributeInstance = this.attributes.get(holder);
			if (attributeInstance != null) {
				collection.forEach(attributeModifier -> attributeInstance.removeModifier(attributeModifier.id()));
			}
		});
	}

	public void assignAllValues(AttributeMap attributeMap) {
		attributeMap.attributes.values().forEach(attributeInstance -> {
			AttributeInstance attributeInstance2 = this.getInstance(attributeInstance.getAttribute());
			if (attributeInstance2 != null) {
				attributeInstance2.replaceFrom(attributeInstance);
			}
		});
	}

	public void assignBaseValues(AttributeMap attributeMap) {
		attributeMap.attributes.values().forEach(attributeInstance -> {
			AttributeInstance attributeInstance2 = this.getInstance(attributeInstance.getAttribute());
			if (attributeInstance2 != null) {
				attributeInstance2.setBaseValue(attributeInstance.getBaseValue());
			}
		});
	}

	public void assignPermanentModifiers(AttributeMap attributeMap) {
		attributeMap.attributes.values().forEach(attributeInstance -> {
			AttributeInstance attributeInstance2 = this.getInstance(attributeInstance.getAttribute());
			if (attributeInstance2 != null) {
				attributeInstance2.addPermanentModifiers(attributeInstance.getPermanentModifiers());
			}
		});
	}

	public boolean resetBaseValue(Holder<Attribute> holder) {
		if (!this.supplier.hasAttribute(holder)) {
			return false;
		}

		AttributeInstance attributeInstance = this.attributes.get(holder);
		if (attributeInstance != null) {
			attributeInstance.setBaseValue(this.supplier.getBaseValue(holder));
		}

		return true;
	}

	public List<AttributeInstance.Packed> pack() {
		List<AttributeInstance.Packed> list = new ArrayList<>(this.attributes.values().size());

		for (AttributeInstance attributeInstance : this.attributes.values()) {
			list.add(attributeInstance.pack());
		}

		return list;
	}

	public void apply(List<AttributeInstance.Packed> list) {
		for (AttributeInstance.Packed packed : list) {
			AttributeInstance attributeInstance = this.getInstance(packed.attribute());
			if (attributeInstance != null) {
				attributeInstance.apply(packed);
			}
		}
	}
}
