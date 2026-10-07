package net.minecraft.world.flag;

public class FeatureFlagUniverse {
	private final String id;

	public FeatureFlagUniverse(String string) {
		this.id = string;
	}

	@Override
	public String toString() {
		return this.id;
	}
}
