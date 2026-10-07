package net.minecraft.client.input;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record MouseButtonEvent(double x, double y, MouseButtonInfo buttonInfo) implements InputWithModifiers {
	@Override
	public int input() {
		return this.button();
	}

	@MouseButtonInfo.MouseButton
	public int button() {
		return this.buttonInfo().button();
	}

	@InputWithModifiers.Modifiers
	@Override
	public int modifiers() {
		return this.buttonInfo().modifiers();
	}
}
