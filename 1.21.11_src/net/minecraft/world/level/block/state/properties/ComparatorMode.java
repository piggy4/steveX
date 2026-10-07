package net.minecraft.world.level.block.state.properties;

import net.minecraft.util.StringRepresentable;

public enum ComparatorMode implements StringRepresentable {
	COMPARE("compare"),
	SUBTRACT("subtract");

	private final String name;

	ComparatorMode(final String string2) {
		this.name = string2;
	}

	@Override
	public String toString() {
		return this.name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
