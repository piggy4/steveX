package net.minecraft.util;

public class BinaryAnimator {
	private final int animationLength;
	private final EasingType easing;
	private int ticks;
	private int ticksOld;

	public BinaryAnimator(int i, EasingType easingType) {
		this.animationLength = i;
		this.easing = easingType;
	}

	public BinaryAnimator(int i) {
		this(i, EasingType.LINEAR);
	}

	public void tick(boolean bl) {
		this.ticksOld = this.ticks;
		if (bl) {
			if (this.ticks < this.animationLength) {
				this.ticks++;
			}
		} else if (this.ticks > 0) {
			this.ticks--;
		}
	}

	public float getFactor(float f) {
		float g = Mth.lerp(f, this.ticksOld, this.ticks) / this.animationLength;
		return this.easing.apply(g);
	}
}
