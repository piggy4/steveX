package net.minecraft.world.entity;

public interface PlayerRideableJumping extends PlayerRideable {
	void onPlayerJump(int i);

	boolean canJump();

	void handleStartJump(int i);

	void handleStopJump();

	default int getJumpCooldown() {
		return 0;
	}

	default float getPlayerJumpPendingScale(int i) {
		return i >= 90 ? 1.0F : 0.4F + 0.4F * i / 90.0F;
	}
}
