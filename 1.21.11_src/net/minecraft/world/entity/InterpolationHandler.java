package net.minecraft.world.entity;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class InterpolationHandler {
	public static final int DEFAULT_INTERPOLATION_STEPS = 3;
	private final Entity entity;
	private int interpolationSteps;
	private final InterpolationHandler.InterpolationData interpolationData = new InterpolationHandler.InterpolationData(0, Vec3.ZERO, 0.0F, 0.0F);
	private @Nullable Vec3 previousTickPosition;
	private @Nullable Vec2 previousTickRot;
	private final @Nullable Consumer<InterpolationHandler> onInterpolationStart;

	public InterpolationHandler(Entity entity) {
		this(entity, 3, null);
	}

	public InterpolationHandler(Entity entity, int i) {
		this(entity, i, null);
	}

	public InterpolationHandler(Entity entity, @Nullable Consumer<InterpolationHandler> consumer) {
		this(entity, 3, consumer);
	}

	public InterpolationHandler(Entity entity, int i, @Nullable Consumer<InterpolationHandler> consumer) {
		this.interpolationSteps = i;
		this.entity = entity;
		this.onInterpolationStart = consumer;
	}

	public Vec3 position() {
		return this.interpolationData.steps > 0 ? this.interpolationData.position : this.entity.position();
	}

	public float yRot() {
		return this.interpolationData.steps > 0 ? this.interpolationData.yRot : this.entity.getYRot();
	}

	public float xRot() {
		return this.interpolationData.steps > 0 ? this.interpolationData.xRot : this.entity.getXRot();
	}

	public void interpolateTo(Vec3 vec3, float f, float g) {
		if (this.interpolationSteps == 0) {
			this.entity.snapTo(vec3, f, g);
			this.cancel();
		} else if (!this.hasActiveInterpolation() || !Objects.equals(this.yRot(), f) || !Objects.equals(this.xRot(), g) || !Objects.equals(this.position(), vec3)) {
			this.interpolationData.steps = this.interpolationSteps;
			this.interpolationData.position = vec3;
			this.interpolationData.yRot = f;
			this.interpolationData.xRot = g;
			this.previousTickPosition = this.entity.position();
			this.previousTickRot = new Vec2(this.entity.getXRot(), this.entity.getYRot());
			if (this.onInterpolationStart != null) {
				this.onInterpolationStart.accept(this);
			}
		}
	}

	public boolean hasActiveInterpolation() {
		return this.interpolationData.steps > 0;
	}

	public void setInterpolationLength(int i) {
		this.interpolationSteps = i;
	}

	public void interpolate() {
		if (!this.hasActiveInterpolation()) {
			this.cancel();
		} else {
			double d = 1.0 / this.interpolationData.steps;
			if (this.previousTickPosition != null) {
				Vec3 vec3 = this.entity.position().subtract(this.previousTickPosition);
				if (this.entity.level().noCollision(this.entity, this.entity.makeBoundingBox(this.interpolationData.position.add(vec3)))) {
					this.interpolationData.addDelta(vec3);
				}
			}

			if (this.previousTickRot != null) {
				float f = this.entity.getYRot() - this.previousTickRot.y;
				float g = this.entity.getXRot() - this.previousTickRot.x;
				this.interpolationData.addRotation(f, g);
			}

			double e = Mth.lerp(d, this.entity.getX(), this.interpolationData.position.x);
			double h = Mth.lerp(d, this.entity.getY(), this.interpolationData.position.y);
			double i = Mth.lerp(d, this.entity.getZ(), this.interpolationData.position.z);
			Vec3 vec32 = new Vec3(e, h, i);
			float j = (float)Mth.rotLerp(d, this.entity.getYRot(), this.interpolationData.yRot);
			float k = (float)Mth.lerp(d, this.entity.getXRot(), this.interpolationData.xRot);
			this.entity.setPos(vec32);
			this.entity.setRot(j, k);
			this.interpolationData.decrease();
			this.previousTickPosition = vec32;
			this.previousTickRot = new Vec2(this.entity.getXRot(), this.entity.getYRot());
		}
	}

	public void cancel() {
		this.interpolationData.steps = 0;
		this.previousTickPosition = null;
		this.previousTickRot = null;
	}

	static class InterpolationData {
		protected int steps;
		Vec3 position;
		float yRot;
		float xRot;

		InterpolationData(int i, Vec3 vec3, float f, float g) {
			this.steps = i;
			this.position = vec3;
			this.yRot = f;
			this.xRot = g;
		}

		public void decrease() {
			this.steps--;
		}

		public void addDelta(Vec3 vec3) {
			this.position = this.position.add(vec3);
		}

		public void addRotation(float f, float g) {
			this.yRot += f;
			this.xRot += g;
		}
	}
}
