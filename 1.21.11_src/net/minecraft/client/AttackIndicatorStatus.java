package net.minecraft.client;

import com.mojang.serialization.Codec;
import java.util.function.IntFunction;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ByIdMap;

@Environment(EnvType.CLIENT)
public enum AttackIndicatorStatus {
	OFF(0, "options.off"),
	CROSSHAIR(1, "options.attack.crosshair"),
	HOTBAR(2, "options.attack.hotbar");

	private static final IntFunction<AttackIndicatorStatus> BY_ID = ByIdMap.continuous(
		attackIndicatorStatus -> attackIndicatorStatus.id, values(), ByIdMap.OutOfBoundsStrategy.WRAP
	);
	public static final Codec<AttackIndicatorStatus> LEGACY_CODEC = Codec.INT.xmap(BY_ID::apply, attackIndicatorStatus -> attackIndicatorStatus.id);
	private final int id;
	private final Component caption;

	AttackIndicatorStatus(final int j, final String string2) {
		this.id = j;
		this.caption = Component.translatable(string2);
	}

	public Component caption() {
		return this.caption;
	}
}
