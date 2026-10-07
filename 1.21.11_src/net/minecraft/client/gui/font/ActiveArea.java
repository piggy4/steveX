package net.minecraft.client.gui.font;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Style;

@Environment(EnvType.CLIENT)
public interface ActiveArea {
	Style style();

	float activeLeft();

	float activeTop();

	float activeRight();

	float activeBottom();
}
