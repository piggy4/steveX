package net.minecraft.client;

import java.util.Arrays;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.TrackedWaypoint;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public class Camera implements TrackedWaypoint.Camera {
	private static final float DEFAULT_CAMERA_DISTANCE = 4.0F;
	private static final Vector3f FORWARDS = new Vector3f(0.0F, 0.0F, -1.0F);
	private static final Vector3f UP = new Vector3f(0.0F, 1.0F, 0.0F);
	private static final Vector3f LEFT = new Vector3f(-1.0F, 0.0F, 0.0F);
	private boolean initialized;
	private Level level;
	private Entity entity;
	private Vec3 position = Vec3.ZERO;
	private final BlockPos.MutableBlockPos blockPosition = new BlockPos.MutableBlockPos();
	private final Vector3f forwards = new Vector3f(FORWARDS);
	private final Vector3f up = new Vector3f(UP);
	private final Vector3f left = new Vector3f(LEFT);
	private float xRot;
	private float yRot;
	private final Quaternionf rotation = new Quaternionf();
	private boolean detached;
	private float eyeHeight;
	private float eyeHeightOld;
	private float partialTickTime;
	private final EnvironmentAttributeProbe attributeProbe = new EnvironmentAttributeProbe();

	public void setup(Level level, Entity entity, boolean bl, boolean bl2, float f) {
		this.initialized = true;
		this.level = level;
		this.entity = entity;
		this.detached = bl;
		this.partialTickTime = f;
		if (entity.isPassenger()
			&& entity.getVehicle() instanceof Minecart minecart
			&& minecart.getBehavior() instanceof NewMinecartBehavior newMinecartBehavior
			&& newMinecartBehavior.cartHasPosRotLerp()) {
			Vec3 vec3 = minecart.getPassengerRidingPosition(entity)
				.subtract(minecart.position())
				.subtract(entity.getVehicleAttachmentPoint(minecart))
				.add(new Vec3(0.0, Mth.lerp(f, this.eyeHeightOld, this.eyeHeight), 0.0));
			this.setRotation(entity.getViewYRot(f), entity.getViewXRot(f));
			this.setPosition(newMinecartBehavior.getCartLerpPosition(f).add(vec3));
		} else {
			this.setRotation(entity.getViewYRot(f), entity.getViewXRot(f));
			this.setPosition(
				Mth.lerp(f, entity.xo, entity.getX()),
				Mth.lerp(f, entity.yo, entity.getY()) + Mth.lerp(f, this.eyeHeightOld, this.eyeHeight),
				Mth.lerp(f, entity.zo, entity.getZ())
			);
		}

		if (bl) {
			if (bl2) {
				this.setRotation(this.yRot + 180.0F, -this.xRot);
			}

			float g = 4.0F;
			float h = 1.0F;
			if (entity instanceof LivingEntity livingEntity) {
				h = livingEntity.getScale();
				g = (float)livingEntity.getAttributeValue(Attributes.CAMERA_DISTANCE);
			}

			float i = h;
			float j = g;
			if (entity.isPassenger() && entity.getVehicle() instanceof LivingEntity livingEntity2) {
				i = livingEntity2.getScale();
				j = (float)livingEntity2.getAttributeValue(Attributes.CAMERA_DISTANCE);
			}

			this.move(-this.getMaxZoom(Math.max(h * g, i * j)), 0.0F, 0.0F);
		} else if (entity instanceof LivingEntity && ((LivingEntity)entity).isSleeping()) {
			Direction direction = ((LivingEntity)entity).getBedOrientation();
			this.setRotation(direction != null ? direction.toYRot() - 180.0F : 0.0F, 0.0F);
			this.move(0.0F, 0.3F, 0.0F);
		}
	}

	public void tick() {
		if (this.entity != null) {
			this.eyeHeightOld = this.eyeHeight;
			this.eyeHeight = this.eyeHeight + (this.entity.getEyeHeight() - this.eyeHeight) * 0.5F;
			this.attributeProbe.tick(this.level, this.position);
		}
	}

	private float getMaxZoom(float f) {
		float g = 0.1F;

		for (int i = 0; i < 8; i++) {
			float h = (i & 1) * 2 - 1;
			float j = (i >> 1 & 1) * 2 - 1;
			float k = (i >> 2 & 1) * 2 - 1;
			Vec3 vec3 = this.position.add(h * 0.1F, j * 0.1F, k * 0.1F);
			Vec3 vec32 = vec3.add(new Vec3(this.forwards).scale(-f));
			HitResult hitResult = this.level.clip(new ClipContext(vec3, vec32, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this.entity));
			if (hitResult.getType() != HitResult.Type.MISS) {
				float l = (float)hitResult.getLocation().distanceToSqr(this.position);
				if (l < Mth.square(f)) {
					f = Mth.sqrt(l);
				}
			}
		}

		return f;
	}

	protected void move(float f, float g, float h) {
		Vector3f vector3f = new Vector3f(h, g, -f).rotate(this.rotation);
		this.setPosition(new Vec3(this.position.x + vector3f.x, this.position.y + vector3f.y, this.position.z + vector3f.z));
	}

	protected void setRotation(float f, float g) {
		this.xRot = g;
		this.yRot = f;
		this.rotation.rotationYXZ((float) Math.PI - f * (float) (Math.PI / 180.0), -g * (float) (Math.PI / 180.0), 0.0F);
		FORWARDS.rotate(this.rotation, this.forwards);
		UP.rotate(this.rotation, this.up);
		LEFT.rotate(this.rotation, this.left);
	}

	protected void setPosition(double d, double e, double f) {
		this.setPosition(new Vec3(d, e, f));
	}

	protected void setPosition(Vec3 vec3) {
		this.position = vec3;
		this.blockPosition.set(vec3.x, vec3.y, vec3.z);
	}

	@Override
	public Vec3 position() {
		return this.position;
	}

	public BlockPos blockPosition() {
		return this.blockPosition;
	}

	public float xRot() {
		return this.xRot;
	}

	public float yRot() {
		return this.yRot;
	}

	@Override
	public float yaw() {
		return Mth.wrapDegrees(this.yRot());
	}

	public Quaternionf rotation() {
		return this.rotation;
	}

	public Entity entity() {
		return this.entity;
	}

	public boolean isInitialized() {
		return this.initialized;
	}

	public boolean isDetached() {
		return this.detached;
	}

	public EnvironmentAttributeProbe attributeProbe() {
		return this.attributeProbe;
	}

	public Camera.NearPlane getNearPlane() {
		Minecraft minecraft = Minecraft.getInstance();
		double d = (double)minecraft.getWindow().getWidth() / minecraft.getWindow().getHeight();
		double e = Math.tan(minecraft.options.fov().get().intValue() * (float) (Math.PI / 180.0) / 2.0) * 0.05F;
		double f = e * d;
		Vec3 vec3 = new Vec3(this.forwards).scale(0.05F);
		Vec3 vec32 = new Vec3(this.left).scale(f);
		Vec3 vec33 = new Vec3(this.up).scale(e);
		return new Camera.NearPlane(vec3, vec32, vec33);
	}

	public FogType getFluidInCamera() {
		if (!this.initialized) {
			return FogType.NONE;
		}

		FluidState fluidState = this.level.getFluidState(this.blockPosition);
		if (fluidState.is(FluidTags.WATER) && this.position.y < this.blockPosition.getY() + fluidState.getHeight(this.level, this.blockPosition)) {
			return FogType.WATER;
		}

		Camera.NearPlane nearPlane = this.getNearPlane();

		for (Vec3 vec3 : Arrays.asList(nearPlane.forward, nearPlane.getTopLeft(), nearPlane.getTopRight(), nearPlane.getBottomLeft(), nearPlane.getBottomRight())) {
			Vec3 vec32 = this.position.add(vec3);
			BlockPos blockPos = BlockPos.containing(vec32);
			FluidState fluidState2 = this.level.getFluidState(blockPos);
			if (fluidState2.is(FluidTags.LAVA)) {
				if (vec32.y <= fluidState2.getHeight(this.level, blockPos) + blockPos.getY()) {
					return FogType.LAVA;
				}
			} else {
				BlockState blockState = this.level.getBlockState(blockPos);
				if (blockState.is(Blocks.POWDER_SNOW)) {
					return FogType.POWDER_SNOW;
				}
			}
		}

		return FogType.NONE;
	}

	public Vector3fc forwardVector() {
		return this.forwards;
	}

	public Vector3fc upVector() {
		return this.up;
	}

	public Vector3fc leftVector() {
		return this.left;
	}

	public void reset() {
		this.level = null;
		this.entity = null;
		this.attributeProbe.reset();
		this.initialized = false;
	}

	public float getPartialTickTime() {
		return this.partialTickTime;
	}

	@Environment(EnvType.CLIENT)
	public static class NearPlane {
		final Vec3 forward;
		private final Vec3 left;
		private final Vec3 up;

		NearPlane(Vec3 vec3, Vec3 vec32, Vec3 vec33) {
			this.forward = vec3;
			this.left = vec32;
			this.up = vec33;
		}

		public Vec3 getTopLeft() {
			return this.forward.add(this.up).add(this.left);
		}

		public Vec3 getTopRight() {
			return this.forward.add(this.up).subtract(this.left);
		}

		public Vec3 getBottomLeft() {
			return this.forward.subtract(this.up).add(this.left);
		}

		public Vec3 getBottomRight() {
			return this.forward.subtract(this.up).subtract(this.left);
		}

		public Vec3 getPointOnPlane(float f, float g) {
			return this.forward.add(this.up.scale(g)).subtract(this.left.scale(f));
		}
	}
}
