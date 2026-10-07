package net.minecraft.world.entity.player;

import com.mojang.serialization.Codec;
import java.util.function.IntFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ByIdMap;

public enum ChatVisiblity {
	FULL(0, "options.chat.visibility.full"),
	SYSTEM(1, "options.chat.visibility.system"),
	HIDDEN(2, "options.chat.visibility.hidden");

	private static final IntFunction<ChatVisiblity> BY_ID = ByIdMap.continuous(chatVisiblity -> chatVisiblity.id, values(), ByIdMap.OutOfBoundsStrategy.WRAP);
	public static final Codec<ChatVisiblity> LEGACY_CODEC = Codec.INT.xmap(BY_ID::apply, chatVisiblity -> chatVisiblity.id);
	private final int id;
	private final Component caption;

	ChatVisiblity(final int j, final String string2) {
		this.id = j;
		this.caption = Component.translatable(string2);
	}

	public Component caption() {
		return this.caption;
	}
}
