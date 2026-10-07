package net.minecraft.world.entity;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Util;

public record DropChances(Map<EquipmentSlot, Float> byEquipment) {
	public static final float DEFAULT_EQUIPMENT_DROP_CHANCE = 0.085F;
	public static final float PRESERVE_ITEM_DROP_CHANCE_THRESHOLD = 1.0F;
	public static final int PRESERVE_ITEM_DROP_CHANCE = 2;
	public static final DropChances DEFAULT = new DropChances(Util.makeEnumMap(EquipmentSlot.class, equipmentSlot -> 0.085F));
	public static final Codec<DropChances> CODEC = Codec.unboundedMap(EquipmentSlot.CODEC, ExtraCodecs.NON_NEGATIVE_FLOAT)
		.xmap(DropChances::toEnumMap, DropChances::filterDefaultValues)
		.xmap(DropChances::new, DropChances::byEquipment);

	private static Map<EquipmentSlot, Float> filterDefaultValues(Map<EquipmentSlot, Float> map) {
		Map<EquipmentSlot, Float> map2 = new HashMap<>(map);
		map2.values().removeIf(float_ -> float_ == 0.085F);
		return map2;
	}

	private static Map<EquipmentSlot, Float> toEnumMap(Map<EquipmentSlot, Float> map) {
		return Util.makeEnumMap(EquipmentSlot.class, equipmentSlot -> map.getOrDefault(equipmentSlot, 0.085F));
	}

	public DropChances withGuaranteedDrop(EquipmentSlot equipmentSlot) {
		return this.withEquipmentChance(equipmentSlot, 2.0F);
	}

	public DropChances withEquipmentChance(EquipmentSlot equipmentSlot, float f) {
		if (f < 0.0F) {
			throw new IllegalArgumentException("Tried to set invalid equipment chance " + f + " for " + equipmentSlot);
		} else {
			return this.byEquipment(equipmentSlot) == f
				? this
				: new DropChances(Util.makeEnumMap(EquipmentSlot.class, equipmentSlot2 -> equipmentSlot2 == equipmentSlot ? f : this.byEquipment(equipmentSlot2)));
		}
	}

	public float byEquipment(EquipmentSlot equipmentSlot) {
		return this.byEquipment.getOrDefault(equipmentSlot, 0.085F);
	}

	public boolean isPreserved(EquipmentSlot equipmentSlot) {
		return this.byEquipment(equipmentSlot) > 1.0F;
	}
}
