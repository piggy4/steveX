package net.minecraft.world.entity.vehicle.minecart;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class OldMinecartBehavior extends MinecartBehavior {
	private static final double MINECART_RIDABLE_THRESHOLD = 0.01;
	private static final double MAX_SPEED_IN_WATER = 0.2;
	private static final double MAX_SPEED_ON_LAND = 0.4;
	private static final double ABSOLUTE_MAX_SPEED = 0.4;
	private final InterpolationHandler interpolation;
	private Vec3 targetDeltaMovement = Vec3.ZERO;

	public OldMinecartBehavior(AbstractMinecart abstractMinecart) {
		super(abstractMinecart);
		this.interpolation = new InterpolationHandler(abstractMinecart, this::onInterpolation);
	}

	@Override
	public InterpolationHandler getInterpolation() {
		return this.interpolation;
	}

	public void onInterpolation(InterpolationHandler interpolationHandler) {
		this.setDeltaMovement(this.targetDeltaMovement);
	}

	@Override
	public void lerpMotion(Vec3 vec3) {
		this.targetDeltaMovement = vec3;
		this.setDeltaMovement(this.targetDeltaMovement);
	}

	@Override
	public void tick() {
		if (this.level() instanceof ServerLevel serverLevel) {
			this.minecart.applyGravity();
			BlockPos var11 = this.minecart.getCurrentBlockPosOrRailBelow();
			BlockState blockState = this.level().getBlockState(var11);
			boolean bl = BaseRailBlock.isRail(blockState);
			this.minecart.setOnRails(bl);
			if (bl) {
				this.moveAlongTrack(serverLevel);
				if (blockState.is(Blocks.ACTIVATOR_RAIL)) {
					this.minecart.activateMinecart(serverLevel, var11.getX(), var11.getY(), var11.getZ(), blockState.getValue(PoweredRailBlock.POWERED));
				}
			} else {
				this.minecart.comeOffTrack(serverLevel);
			}

			this.minecart.applyEffectsFromBlocks();
			this.setXRot(0.0F);
			double d = this.minecart.xo - this.getX();
			double e = this.minecart.zo - this.getZ();
			if (d * d + e * e > 0.001) {
				this.setYRot((float)(Mth.atan2(e, d) * 180.0 / Math.PI));
				if (this.minecart.isFlipped()) {
					this.setYRot(this.getYRot() + 180.0F);
				}
			}

			double f = Mth.wrapDegrees(this.getYRot() - this.minecart.yRotO);
			if (f < -170.0 || f >= 170.0) {
				this.setYRot(this.getYRot() + 180.0F);
				this.minecart.setFlipped(!this.minecart.isFlipped());
			}

			this.setXRot(this.getXRot() % 360.0F);
			this.setYRot(this.getYRot() % 360.0F);
			this.pushAndPickupEntities();
		} else {
			if (this.interpolation.hasActiveInterpolation()) {
				this.interpolation.interpolate();
			} else {
				this.minecart.reapplyPosition();
				this.setXRot(this.getXRot() % 360.0F);
				this.setYRot(this.getYRot() % 360.0F);
			}
		}
	}

	@Override
	public void moveAlongTrack(ServerLevel serverLevel) {
		BlockPos blockPos = this.minecart.getCurrentBlockPosOrRailBelow();
		BlockState blockState = this.level().getBlockState(blockPos);
		this.minecart.resetFallDistance();
		double d = this.minecart.getX();
		double e = this.minecart.getY();
		double f = this.minecart.getZ();
		Vec3 vec3 = this.getPos(d, e, f);
		e = blockPos.getY();
		boolean bl = false;
		boolean bl2 = false;
		if (blockState.is(Blocks.POWERED_RAIL)) {
			bl = blockState.getValue(PoweredRailBlock.POWERED);
			bl2 = !bl;
		}

		double g = 0.0078125;
		if (this.minecart.isInWater()) {
			g *= 0.2;
		}

		Vec3 vec32 = this.getDeltaMovement();
		RailShape railShape = blockState.getValue(((BaseRailBlock)blockState.getBlock()).getShapeProperty());
		switch (railShape) {
			case ASCENDING_EAST:
				this.setDeltaMovement(vec32.add(-g, 0.0, 0.0));
				e++;
				break;
			case ASCENDING_WEST:
				this.setDeltaMovement(vec32.add(g, 0.0, 0.0));
				e++;
				break;
			case ASCENDING_NORTH:
				this.setDeltaMovement(vec32.add(0.0, 0.0, g));
				e++;
				break;
			case ASCENDING_SOUTH:
				this.setDeltaMovement(vec32.add(0.0, 0.0, -g));
				e++;
		}

		vec32 = this.getDeltaMovement();
		Pair<Vec3i, Vec3i> pair = AbstractMinecart.exits(railShape);
		Vec3i vec3i = pair.getFirst();
		Vec3i vec3i2 = pair.getSecond();
		double h = vec3i2.getX() - vec3i.getX();
		double i = vec3i2.getZ() - vec3i.getZ();
		double j = Math.sqrt(h * h + i * i);
		double k = vec32.x * h + vec32.z * i;
		if (k < 0.0) {
			h = -h;
			i = -i;
		}

		double l = Math.min(2.0, vec32.horizontalDistance());
		vec32 = new Vec3(l * h / j, vec32.y, l * i / j);
		this.setDeltaMovement(vec32);
		Entity entity = this.minecart.getFirstPassenger();
		Vec3 vec33;
		if (this.minecart.getFirstPassenger() instanceof ServerPlayer serverPlayer) {
			vec33 = serverPlayer.getLastClientMoveIntent();
		} else {
			vec33 = Vec3.ZERO;
		}

		if (entity instanceof Player && vec33.lengthSqr() > 0.0) {
			Vec3 vec34 = vec33.normalize();
			double m = this.getDeltaMovement().horizontalDistanceSqr();
			if (vec34.lengthSqr() > 0.0 && m < 0.01) {
				this.setDeltaMovement(this.getDeltaMovement().add(vec33.x * 0.001, 0.0, vec33.z * 0.001));
				bl2 = false;
			}
		}

		if (bl2) {
			double n = this.getDeltaMovement().horizontalDistance();
			if (n < 0.03) {
				this.setDeltaMovement(Vec3.ZERO);
			} else {
				this.setDeltaMovement(this.getDeltaMovement().multiply(0.5, 0.0, 0.5));
			}
		}

		double n = blockPos.getX() + 0.5 + vec3i.getX() * 0.5;
		double o = blockPos.getZ() + 0.5 + vec3i.getZ() * 0.5;
		double p = blockPos.getX() + 0.5 + vec3i2.getX() * 0.5;
		double q = blockPos.getZ() + 0.5 + vec3i2.getZ() * 0.5;
		h = p - n;
		i = q - o;
		double r;
		if (h == 0.0) {
			r = f - blockPos.getZ();
		} else if (i == 0.0) {
			r = d - blockPos.getX();
		} else {
			double s = d - n;
			double t = f - o;
			r = (s * h + t * i) * 2.0;
		}

		d = n + h * r;
		f = o + i * r;
		this.setPos(d, e, f);
		double s = this.minecart.isVehicle() ? 0.75 : 1.0;
		double t = this.minecart.getMaxSpeed(serverLevel);
		vec32 = this.getDeltaMovement();
		this.minecart.move(MoverType.SELF, new Vec3(Mth.clamp(s * vec32.x, -t, t), 0.0, Mth.clamp(s * vec32.z, -t, t)));
		if (vec3i.getY() != 0
			&& Mth.floor(this.minecart.getX()) - blockPos.getX() == vec3i.getX()
			&& Mth.floor(this.minecart.getZ()) - blockPos.getZ() == vec3i.getZ()) {
			this.setPos(this.minecart.getX(), this.minecart.getY() + vec3i.getY(), this.minecart.getZ());
		} else if (vec3i2.getY() != 0
			&& Mth.floor(this.minecart.getX()) - blockPos.getX() == vec3i2.getX()
			&& Mth.floor(this.minecart.getZ()) - blockPos.getZ() == vec3i2.getZ()) {
			this.setPos(this.minecart.getX(), this.minecart.getY() + vec3i2.getY(), this.minecart.getZ());
		}

		this.setDeltaMovement(this.minecart.applyNaturalSlowdown(this.getDeltaMovement()));
		Vec3 vec35 = this.getPos(this.minecart.getX(), this.minecart.getY(), this.minecart.getZ());
		if (vec35 != null && vec3 != null) {
			double u = (vec3.y - vec35.y) * 0.05;
			Vec3 vec36 = this.getDeltaMovement();
			double v = vec36.horizontalDistance();
			if (v > 0.0) {
				this.setDeltaMovement(vec36.multiply((v + u) / v, 1.0, (v + u) / v));
			}

			this.setPos(this.minecart.getX(), vec35.y, this.minecart.getZ());
		}

		int w = Mth.floor(this.minecart.getX());
		int x = Mth.floor(this.minecart.getZ());
		if (w != blockPos.getX() || x != blockPos.getZ()) {
			Vec3 vec36 = this.getDeltaMovement();
			double v = vec36.horizontalDistance();
			this.setDeltaMovement(v * (w - blockPos.getX()), vec36.y, v * (x - blockPos.getZ()));
		}

		if (bl) {
			Vec3 vec36 = this.getDeltaMovement();
			double v = vec36.horizontalDistance();
			if (v > 0.01) {
				double y = 0.06;
				this.setDeltaMovement(vec36.add(vec36.x / v * 0.06, 0.0, vec36.z / v * 0.06));
			} else {
				Vec3 vec37 = this.getDeltaMovement();
				double z = vec37.x;
				double aa = vec37.z;
				if (railShape == RailShape.EAST_WEST) {
					if (this.minecart.isRedstoneConductor(blockPos.west())) {
						z = 0.02;
					} else if (this.minecart.isRedstoneConductor(blockPos.east())) {
						z = -0.02;
					}
				} else {
					if (railShape != RailShape.NORTH_SOUTH) {
						return;
					}

					if (this.minecart.isRedstoneConductor(blockPos.north())) {
						aa = 0.02;
					} else if (this.minecart.isRedstoneConductor(blockPos.south())) {
						aa = -0.02;
					}
				}

				this.setDeltaMovement(z, vec37.y, aa);
			}
		}
	}

	public @Nullable Vec3 getPosOffs(double d, double e, double f, double g) {
		int i = Mth.floor(d);
		int j = Mth.floor(e);
		int k = Mth.floor(f);
		if (this.level().getBlockState(new BlockPos(i, j - 1, k)).is(BlockTags.RAILS)) {
			j--;
		}

		BlockState blockState = this.level().getBlockState(new BlockPos(i, j, k));
		if (BaseRailBlock.isRail(blockState)) {
			RailShape railShape = blockState.getValue(((BaseRailBlock)blockState.getBlock()).getShapeProperty());
			e = j;
			if (railShape.isSlope()) {
				e = j + 1;
			}

			Pair<Vec3i, Vec3i> pair = AbstractMinecart.exits(railShape);
			Vec3i vec3i = pair.getFirst();
			Vec3i vec3i2 = pair.getSecond();
			double h = vec3i2.getX() - vec3i.getX();
			double l = vec3i2.getZ() - vec3i.getZ();
			double m = Math.sqrt(h * h + l * l);
			h /= m;
			l /= m;
			d += h * g;
			f += l * g;
			if (vec3i.getY() != 0 && Mth.floor(d) - i == vec3i.getX() && Mth.floor(f) - k == vec3i.getZ()) {
				e += vec3i.getY();
			} else if (vec3i2.getY() != 0 && Mth.floor(d) - i == vec3i2.getX() && Mth.floor(f) - k == vec3i2.getZ()) {
				e += vec3i2.getY();
			}

			return this.getPos(d, e, f);
		} else {
			return null;
		}
	}

	public @Nullable Vec3 getPos(double d, double e, double f) {
		int i = Mth.floor(d);
		int j = Mth.floor(e);
		int k = Mth.floor(f);
		if (this.level().getBlockState(new BlockPos(i, j - 1, k)).is(BlockTags.RAILS)) {
			j--;
		}

		BlockState blockState = this.level().getBlockState(new BlockPos(i, j, k));
		if (BaseRailBlock.isRail(blockState)) {
			RailShape railShape = blockState.getValue(((BaseRailBlock)blockState.getBlock()).getShapeProperty());
			Pair<Vec3i, Vec3i> pair = AbstractMinecart.exits(railShape);
			Vec3i vec3i = pair.getFirst();
			Vec3i vec3i2 = pair.getSecond();
			double g = i + 0.5 + vec3i.getX() * 0.5;
			double h = j + 0.0625 + vec3i.getY() * 0.5;
			double l = k + 0.5 + vec3i.getZ() * 0.5;
			double m = i + 0.5 + vec3i2.getX() * 0.5;
			double n = j + 0.0625 + vec3i2.getY() * 0.5;
			double o = k + 0.5 + vec3i2.getZ() * 0.5;
			double p = m - g;
			double q = (n - h) * 2.0;
			double r = o - l;
			double s;
			if (p == 0.0) {
				s = f - k;
			} else if (r == 0.0) {
				s = d - i;
			} else {
				double t = d - g;
				double u = f - l;
				s = (t * p + u * r) * 2.0;
			}

			d = g + p * s;
			e = h + q * s;
			f = l + r * s;
			if (q < 0.0) {
				e++;
			} else if (q > 0.0) {
				e += 0.5;
			}

			return new Vec3(d, e, f);
		} else {
			return null;
		}
	}

	@Override
	public double stepAlongTrack(BlockPos blockPos, RailShape railShape, double d) {
		return 0.0;
	}

	@Override
	public boolean pushAndPickupEntities() {
		AABB aABB = this.minecart.getBoundingBox().inflate(0.2F, 0.0, 0.2F);
		if (this.minecart.isRideable() && this.getDeltaMovement().horizontalDistanceSqr() >= 0.01) {
			List<Entity> list = this.level().getEntities(this.minecart, aABB, EntitySelector.pushableBy(this.minecart));
			if (!list.isEmpty()) {
				for (Entity entity : list) {
					if (!(entity instanceof Player)
						&& !(entity instanceof IronGolem)
						&& !(entity instanceof AbstractMinecart)
						&& !this.minecart.isVehicle()
						&& !entity.isPassenger()) {
						entity.startRiding(this.minecart);
					} else {
						entity.push(this.minecart);
					}
				}
			}
		} else {
			for (Entity entity2 : this.level().getEntities(this.minecart, aABB)) {
				if (!this.minecart.hasPassenger(entity2) && entity2.isPushable() && entity2 instanceof AbstractMinecart) {
					entity2.push(this.minecart);
				}
			}
		}

		return false;
	}

	@Override
	public Direction getMotionDirection() {
		return this.minecart.isFlipped() ? this.minecart.getDirection().getOpposite().getClockWise() : this.minecart.getDirection().getClockWise();
	}

	@Override
	public Vec3 getKnownMovement(Vec3 vec3) {
		return !Double.isNaN(vec3.x) && !Double.isNaN(vec3.y) && !Double.isNaN(vec3.z)
			? new Vec3(Mth.clamp(vec3.x, -0.4, 0.4), vec3.y, Mth.clamp(vec3.z, -0.4, 0.4))
			: Vec3.ZERO;
	}

	@Override
	public double getMaxSpeed(ServerLevel serverLevel) {
		return this.minecart.isInWater() ? 0.2 : 0.4;
	}

	@Override
	public double getSlowdownFactor() {
		return this.minecart.isVehicle() ? 0.997 : 0.96;
	}
}
