package net.minecraft.client;

import com.mojang.serialization.Codec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

@Environment(EnvType.CLIENT)
public enum CloudStatus implements StringRepresentable {
	OFF("false", "options.off"),
	FAST("fast", "options.clouds.fast"),
	FANCY("true", "options.clouds.fancy");

	public static final Codec<CloudStatus> CODEC = StringRepresentable.fromEnum(CloudStatus::values);
	private final String legacyName;
	private final Component caption;

	CloudStatus(final String string2, final String string3) {
		this.legacyName = string2;
		this.caption = Component.translatable(string3);
	}

	public Component caption() {
		return this.caption;
	}

	@Override
	public String getSerializedName() {
		return this.legacyName;
	}
}
