package net.minecraft.client;

import com.mojang.serialization.Codec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

@Environment(EnvType.CLIENT)
public enum InactivityFpsLimit implements StringRepresentable {
	MINIMIZED("minimized", "options.inactivityFpsLimit.minimized"),
	AFK("afk", "options.inactivityFpsLimit.afk");

	public static final Codec<InactivityFpsLimit> CODEC = StringRepresentable.fromEnum(InactivityFpsLimit::values);
	private final String serializedName;
	private final Component caption;

	InactivityFpsLimit(final String string2, final String string3) {
		this.serializedName = string2;
		this.caption = Component.translatable(string3);
	}

	public Component caption() {
		return this.caption;
	}

	@Override
	public String getSerializedName() {
		return this.serializedName;
	}
}
