package net.minecraft.client;

import com.mojang.serialization.Codec;
import java.util.function.IntFunction;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ByIdMap;

@Environment(EnvType.CLIENT)
public enum PrioritizeChunkUpdates {
	NONE(0, "options.prioritizeChunkUpdates.none"),
	PLAYER_AFFECTED(1, "options.prioritizeChunkUpdates.byPlayer"),
	NEARBY(2, "options.prioritizeChunkUpdates.nearby");

	private static final IntFunction<PrioritizeChunkUpdates> BY_ID = ByIdMap.continuous(
		prioritizeChunkUpdates -> prioritizeChunkUpdates.id, values(), ByIdMap.OutOfBoundsStrategy.WRAP
	);
	public static final Codec<PrioritizeChunkUpdates> LEGACY_CODEC = Codec.INT.xmap(BY_ID::apply, prioritizeChunkUpdates -> prioritizeChunkUpdates.id);
	private final int id;
	private final Component caption;

	PrioritizeChunkUpdates(final int j, final String string2) {
		this.id = j;
		this.caption = Component.translatable(string2);
	}

	public Component caption() {
		return this.caption;
	}
}
