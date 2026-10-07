package net.minecraft.client.gui.components;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public record WidgetSprites(Identifier enabled, Identifier disabled, Identifier enabledFocused, Identifier disabledFocused) {
	public WidgetSprites(Identifier identifier) {
		this(identifier, identifier, identifier, identifier);
	}

	public WidgetSprites(Identifier identifier, Identifier identifier2) {
		this(identifier, identifier, identifier2, identifier2);
	}

	public WidgetSprites(Identifier identifier, Identifier identifier2, Identifier identifier3) {
		this(identifier, identifier2, identifier3, identifier2);
	}

	public Identifier get(boolean bl, boolean bl2) {
		if (bl) {
			return bl2 ? this.enabledFocused : this.enabled;
		} else {
			return bl2 ? this.disabledFocused : this.disabled;
		}
	}
}
