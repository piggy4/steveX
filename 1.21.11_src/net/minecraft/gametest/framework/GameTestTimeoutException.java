package net.minecraft.gametest.framework;

import net.minecraft.network.chat.Component;

public class GameTestTimeoutException extends GameTestException {
	protected final Component message;

	public GameTestTimeoutException(Component component) {
		super(component.getString());
		this.message = component;
	}

	@Override
	public Component getDescription() {
		return this.message;
	}
}
