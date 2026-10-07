package net.minecraft.world.level.gamerules;

import net.minecraft.util.StringRepresentable;

public enum GameRuleType implements StringRepresentable {
	INT("integer"),
	BOOL("boolean");

	private final String name;

	GameRuleType(final String string2) {
		this.name = string2;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
