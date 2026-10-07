package net.minecraft.world.level.border;

public interface BorderChangeListener {
	void onSetSize(WorldBorder worldBorder, double d);

	void onLerpSize(WorldBorder worldBorder, double d, double e, long l, long m);

	void onSetCenter(WorldBorder worldBorder, double d, double e);

	void onSetWarningTime(WorldBorder worldBorder, int i);

	void onSetWarningBlocks(WorldBorder worldBorder, int i);

	void onSetDamagePerBlock(WorldBorder worldBorder, double d);

	void onSetSafeZone(WorldBorder worldBorder, double d);
}
