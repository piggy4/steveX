package net.minecraft.world.entity;

import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;

public record ConversionParams(ConversionType type, boolean keepEquipment, boolean preserveCanPickUpLoot, @Nullable PlayerTeam team) {
	public static ConversionParams single(Mob mob, boolean bl, boolean bl2) {
		return new ConversionParams(ConversionType.SINGLE, bl, bl2, mob.getTeam());
	}

	@FunctionalInterface
	public interface AfterConversion<T extends Mob> {
		void finalizeConversion(T mob);
	}
}
