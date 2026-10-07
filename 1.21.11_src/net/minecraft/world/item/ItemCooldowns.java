package net.minecraft.world.item;

import com.google.common.collect.Maps;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.component.UseCooldown;

public class ItemCooldowns {
	private final Map<Identifier, ItemCooldowns.CooldownInstance> cooldowns = Maps.newHashMap();
	private int tickCount;

	public boolean isOnCooldown(ItemStack itemStack) {
		return this.getCooldownPercent(itemStack, 0.0F) > 0.0F;
	}

	public float getCooldownPercent(ItemStack itemStack, float f) {
		Identifier identifier = this.getCooldownGroup(itemStack);
		ItemCooldowns.CooldownInstance cooldownInstance = this.cooldowns.get(identifier);
		if (cooldownInstance != null) {
			float g = cooldownInstance.endTime - cooldownInstance.startTime;
			float h = cooldownInstance.endTime - (this.tickCount + f);
			return Mth.clamp(h / g, 0.0F, 1.0F);
		} else {
			return 0.0F;
		}
	}

	public void tick() {
		this.tickCount++;
		if (!this.cooldowns.isEmpty()) {
			Iterator<Entry<Identifier, ItemCooldowns.CooldownInstance>> iterator = this.cooldowns.entrySet().iterator();

			while (iterator.hasNext()) {
				Entry<Identifier, ItemCooldowns.CooldownInstance> entry = iterator.next();
				if (entry.getValue().endTime <= this.tickCount) {
					iterator.remove();
					this.onCooldownEnded(entry.getKey());
				}
			}
		}
	}

	public Identifier getCooldownGroup(ItemStack itemStack) {
		UseCooldown useCooldown = itemStack.get(DataComponents.USE_COOLDOWN);
		Identifier identifier = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
		return useCooldown == null ? identifier : useCooldown.cooldownGroup().orElse(identifier);
	}

	public void addCooldown(ItemStack itemStack, int i) {
		this.addCooldown(this.getCooldownGroup(itemStack), i);
	}

	public void addCooldown(Identifier identifier, int i) {
		this.cooldowns.put(identifier, new ItemCooldowns.CooldownInstance(this.tickCount, this.tickCount + i));
		this.onCooldownStarted(identifier, i);
	}

	public void removeCooldown(Identifier identifier) {
		this.cooldowns.remove(identifier);
		this.onCooldownEnded(identifier);
	}

	protected void onCooldownStarted(Identifier identifier, int i) {
	}

	protected void onCooldownEnded(Identifier identifier) {
	}

	record CooldownInstance(int startTime, int endTime) {
	}
}
