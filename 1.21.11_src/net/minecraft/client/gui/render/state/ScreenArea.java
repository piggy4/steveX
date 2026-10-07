package net.minecraft.client.gui.render.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface ScreenArea {
	@Nullable ScreenRectangle bounds();
}
