package net.minecraft.world.entity.ai.goal;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class WaterAvoidingRandomStrollGoal extends RandomStrollGoal {
	public static final float PROBABILITY = 0.001F;
	protected final float probability;

	public WaterAvoidingRandomStrollGoal(PathfinderMob pathfinderMob, double d) {
		this(pathfinderMob, d, 0.001F);
	}

	public WaterAvoidingRandomStrollGoal(PathfinderMob pathfinderMob, double d, float f) {
		super(pathfinderMob, d);
		this.probability = f;
	}

	@Override
	protected @Nullable Vec3 getPosition() {
		if (this.mob.isInWater()) {
			Vec3 vec3 = LandRandomPos.getPos(this.mob, 15, 7);
			return vec3 == null ? super.getPosition() : vec3;
		} else {
			return this.mob.getRandom().nextFloat() >= this.probability ? LandRandomPos.getPos(this.mob, 10, 7) : super.getPosition();
		}
	}
}
