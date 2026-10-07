package net.minecraft.client.gui.components.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.StringRepresentable;

@Environment(EnvType.CLIENT)
public enum DebugScreenEntryStatus implements StringRepresentable {
	ALWAYS_ON("alwaysOn"),
	IN_OVERLAY("inOverlay"),
	NEVER("never");

	public static final StringRepresentable.EnumCodec<DebugScreenEntryStatus> CODEC = StringRepresentable.fromEnum(DebugScreenEntryStatus::values);
	private final String name;

	DebugScreenEntryStatus(final String string2) {
		this.name = string2;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
