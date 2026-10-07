package net.minecraft.client.input;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record MouseButtonInfo(@MouseButtonInfo.MouseButton int button, @InputWithModifiers.Modifiers int modifiers) implements InputWithModifiers {
	@MouseButtonInfo.MouseButton
	@Override
	public int input() {
		return this.button;
	}

	@Retention(RetentionPolicy.CLASS)
	@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.METHOD, ElementType.TYPE_USE})
	@Environment(EnvType.CLIENT)
	public @interface Action {
	}

	@Retention(RetentionPolicy.CLASS)
	@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.METHOD, ElementType.TYPE_USE})
	@Environment(EnvType.CLIENT)
	public @interface MouseButton {
	}
}
