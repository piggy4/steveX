package net.minecraft.world.entity;

import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import net.minecraft.world.item.ItemStack;

public class EntityEquipment {
	public static final Codec<EntityEquipment> CODEC = Codec.unboundedMap(EquipmentSlot.CODEC, ItemStack.CODEC).xmap(map -> {
		EnumMap<EquipmentSlot, ItemStack> enumMap = new EnumMap<>(EquipmentSlot.class);
		enumMap.putAll((Map<? extends EquipmentSlot, ? extends ItemStack>)map);
		return new EntityEquipment(enumMap);
	}, entityEquipment -> {
		Map<EquipmentSlot, ItemStack> map = new EnumMap<>(entityEquipment.items);
		map.values().removeIf(ItemStack::isEmpty);
		return map;
	});
	private final EnumMap<EquipmentSlot, ItemStack> items;

	private EntityEquipment(EnumMap<EquipmentSlot, ItemStack> enumMap) {
		this.items = enumMap;
	}

	public EntityEquipment() {
		this(new EnumMap<>(EquipmentSlot.class));
	}

	public ItemStack set(EquipmentSlot equipmentSlot, ItemStack itemStack) {
		return Objects.requireNonNullElse(this.items.put(equipmentSlot, itemStack), ItemStack.EMPTY);
	}

	public ItemStack get(EquipmentSlot equipmentSlot) {
		return this.items.getOrDefault(equipmentSlot, ItemStack.EMPTY);
	}

	public boolean isEmpty() {
		for (ItemStack itemStack : this.items.values()) {
			if (!itemStack.isEmpty()) {
				return false;
			}
		}

		return true;
	}

	public void tick(Entity entity) {
		for (Entry<EquipmentSlot, ItemStack> entry : this.items.entrySet()) {
			ItemStack itemStack = entry.getValue();
			if (!itemStack.isEmpty()) {
				itemStack.inventoryTick(entity.level(), entity, entry.getKey());
			}
		}
	}

	public void setAll(EntityEquipment entityEquipment) {
		this.items.clear();
		this.items.putAll(entityEquipment.items);
	}

	public void dropAll(LivingEntity livingEntity) {
		for (ItemStack itemStack : this.items.values()) {
			livingEntity.drop(itemStack, true, false);
		}

		this.clear();
	}

	public void clear() {
		this.items.replaceAll((equipmentSlot, itemStack) -> ItemStack.EMPTY);
	}
}
