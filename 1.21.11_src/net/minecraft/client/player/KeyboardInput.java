package net.minecraft.client.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Options;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

@Environment(EnvType.CLIENT)
public class KeyboardInput extends ClientInput {
	private final Options options;

	public KeyboardInput(Options options) {
		this.options = options;
	}

	private static float calculateImpulse(boolean bl, boolean bl2) {
		if (bl == bl2) {
			return 0.0F;
		} else {
			return bl ? 1.0F : -1.0F;
		}
	}

	@Override
	public void tick() {
		this.keyPresses = new Input(
			this.options.keyUp.isDown(),
			this.options.keyDown.isDown(),
			this.options.keyLeft.isDown(),
			this.options.keyRight.isDown(),
			this.options.keyJump.isDown(),
			this.options.keyShift.isDown(),
			this.options.keySprint.isDown()
		);
		float f = calculateImpulse(this.keyPresses.forward(), this.keyPresses.backward());
		float g = calculateImpulse(this.keyPresses.left(), this.keyPresses.right());
		this.moveVector = new Vec2(g, f).normalized();
	}
}
