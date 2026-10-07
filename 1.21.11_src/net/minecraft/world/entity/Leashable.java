package net.minecraft.world.entity;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public interface Leashable {
	String LEASH_TAG = "leash";
	double LEASH_TOO_FAR_DIST = 12.0;
	double LEASH_ELASTIC_DIST = 6.0;
	double MAXIMUM_ALLOWED_LEASHED_DIST = 16.0;
	Vec3 AXIS_SPECIFIC_ELASTICITY = new Vec3(0.8, 0.2, 0.8);
	float SPRING_DAMPENING = 0.7F;
	double TORSIONAL_ELASTICITY = 10.0;
	double STIFFNESS = 0.11;
	List<Vec3> ENTITY_ATTACHMENT_POINT = ImmutableList.of(new Vec3(0.0, 0.5, 0.5));
	List<Vec3> LEASHER_ATTACHMENT_POINT = ImmutableList.of(new Vec3(0.0, 0.5, 0.0));
	List<Vec3> SHARED_QUAD_ATTACHMENT_POINTS = ImmutableList.of(
		new Vec3(-0.5, 0.5, 0.5), new Vec3(-0.5, 0.5, -0.5), new Vec3(0.5, 0.5, -0.5), new Vec3(0.5, 0.5, 0.5)
	);

	Leashable.@Nullable LeashData getLeashData();

	void setLeashData(Leashable.@Nullable LeashData leashData);

	default boolean isLeashed() {
		return this.getLeashData() != null && this.getLeashData().leashHolder != null;
	}

	default boolean mayBeLeashed() {
		return this.getLeashData() != null;
	}

	default boolean canHaveALeashAttachedTo(Entity entity) {
		if (this == entity) {
			return false;
		} else {
			return this.leashDistanceTo(entity) > this.leashSnapDistance() ? false : this.canBeLeashed();
		}
	}

	default double leashDistanceTo(Entity entity) {
		return entity.getBoundingBox().getCenter().distanceTo(((Entity)this).getBoundingBox().getCenter());
	}

	default boolean canBeLeashed() {
		return true;
	}

	default void setDelayedLeashHolderId(int i) {
		this.setLeashData(new Leashable.LeashData(i));
		dropLeash((Entity & Leashable)this, false, false);
	}

	default void readLeashData(ValueInput valueInput) {
		Leashable.LeashData leashData = valueInput.read("leash", Leashable.LeashData.CODEC).orElse(null);
		if (this.getLeashData() != null && leashData == null) {
			this.removeLeash();
		}

		this.setLeashData(leashData);
	}

	default void writeLeashData(ValueOutput valueOutput, Leashable.@Nullable LeashData leashData) {
		valueOutput.storeNullable("leash", Leashable.LeashData.CODEC, leashData);
	}

	private static <E extends Entity & Leashable> void restoreLeashFromSave(E entity, Leashable.LeashData leashData) {
		if (leashData.delayedLeashInfo != null && entity.level() instanceof ServerLevel serverLevel) {
			Optional<UUID> optional = leashData.delayedLeashInfo.left();
			Optional<BlockPos> optional2 = leashData.delayedLeashInfo.right();
			if (optional.isPresent()) {
				Entity entity2 = serverLevel.getEntity(optional.get());
				if (entity2 != null) {
					setLeashedTo(entity, entity2, true);
					return;
				}
			} else if (optional2.isPresent()) {
				setLeashedTo(entity, LeashFenceKnotEntity.getOrCreateKnot(serverLevel, optional2.get()), true);
				return;
			}

			if (entity.tickCount > 100) {
				entity.spawnAtLocation(serverLevel, Items.LEAD);
				entity.setLeashData(null);
			}
		}
	}

	default void dropLeash() {
		dropLeash((Entity & Leashable)this, true, true);
	}

	default void removeLeash() {
		dropLeash((Entity & Leashable)this, true, false);
	}

	default void onLeashRemoved() {
	}

	private static <E extends Entity & Leashable> void dropLeash(E entity, boolean bl, boolean bl2) {
		Leashable.LeashData leashData = entity.getLeashData();
		if (leashData != null && leashData.leashHolder != null) {
			entity.setLeashData(null);
			entity.onLeashRemoved();
			if (entity.level() instanceof ServerLevel serverLevel) {
				if (bl2) {
					entity.spawnAtLocation(serverLevel, Items.LEAD);
				}

				if (bl) {
					serverLevel.getChunkSource().sendToTrackingPlayers(entity, new ClientboundSetEntityLinkPacket(entity, null));
				}

				leashData.leashHolder.notifyLeasheeRemoved(entity);
			}
		}
	}

	static <E extends Entity & Leashable> void tickLeash(ServerLevel serverLevel, E entity) {
		Leashable.LeashData leashData = entity.getLeashData();
		if (leashData != null && leashData.delayedLeashInfo != null) {
			restoreLeashFromSave(entity, leashData);
		}

		if (leashData != null && leashData.leashHolder != null) {
			if (!entity.canInteractWithLevel() || !leashData.leashHolder.canInteractWithLevel()) {
				if (serverLevel.getGameRules().get(GameRules.ENTITY_DROPS)) {
					entity.dropLeash();
				} else {
					entity.removeLeash();
				}
			}

			Entity entity2 = entity.getLeashHolder();
			if (entity2 != null && entity2.level() == entity.level()) {
				double d = entity.leashDistanceTo(entity2);
				entity.whenLeashedTo(entity2);
				if (d > entity.leashSnapDistance()) {
					serverLevel.playSound(null, entity2.getX(), entity2.getY(), entity2.getZ(), SoundEvents.LEAD_BREAK, SoundSource.NEUTRAL, 1.0F, 1.0F);
					entity.leashTooFarBehaviour();
				} else if (d > entity.leashElasticDistance() - entity2.getBbWidth() - entity.getBbWidth() && entity.checkElasticInteractions(entity2, leashData)) {
					entity.onElasticLeashPull();
				} else {
					entity.closeRangeLeashBehaviour(entity2);
				}

				entity.setYRot((float)(entity.getYRot() - leashData.angularMomentum));
				leashData.angularMomentum = leashData.angularMomentum * angularFriction(entity);
			}
		}
	}

	default void onElasticLeashPull() {
		Entity entity = (Entity)this;
		entity.checkFallDistanceAccumulation();
	}

	default double leashSnapDistance() {
		return 12.0;
	}

	default double leashElasticDistance() {
		return 6.0;
	}

	static <E extends Entity & Leashable> float angularFriction(E entity) {
		if (entity.onGround()) {
			return entity.level().getBlockState(entity.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() * 0.91F;
		} else {
			return entity.isInLiquid() ? 0.8F : 0.91F;
		}
	}

	default void whenLeashedTo(Entity entity) {
		entity.notifyLeashHolder(this);
	}

	default void leashTooFarBehaviour() {
		this.dropLeash();
	}

	default void closeRangeLeashBehaviour(Entity entity) {
	}

	default boolean checkElasticInteractions(Entity entity, Leashable.LeashData leashData) {
		boolean bl = entity.supportQuadLeashAsHolder() && this.supportQuadLeash();
		List<Leashable.Wrench> list = computeElasticInteraction(
			(Entity & Leashable)this,
			entity,
			bl ? SHARED_QUAD_ATTACHMENT_POINTS : ENTITY_ATTACHMENT_POINT,
			bl ? SHARED_QUAD_ATTACHMENT_POINTS : LEASHER_ATTACHMENT_POINT
		);
		if (list.isEmpty()) {
			return false;
		}

		Leashable.Wrench wrench = Leashable.Wrench.accumulate(list).scale(bl ? 0.25 : 1.0);
		leashData.angularMomentum = leashData.angularMomentum + 10.0 * wrench.torque();
		Vec3 vec3 = getHolderMovement(entity).subtract(((Entity)this).getKnownMovement());
		((Entity)this).addDeltaMovement(wrench.force().multiply(AXIS_SPECIFIC_ELASTICITY).add(vec3.scale(0.11)));
		return true;
	}

	private static Vec3 getHolderMovement(Entity entity) {
		return entity instanceof Mob mob && mob.isNoAi() ? Vec3.ZERO : entity.getKnownMovement();
	}

	private static <E extends Entity & Leashable> List<Leashable.Wrench> computeElasticInteraction(E entity, Entity entity2, List<Vec3> list, List<Vec3> list2) {
		double d = entity.leashElasticDistance();
		Vec3 vec3 = getHolderMovement(entity);
		float f = entity.getYRot() * (float) (Math.PI / 180.0);
		Vec3 vec32 = new Vec3(entity.getBbWidth(), entity.getBbHeight(), entity.getBbWidth());
		float g = entity2.getYRot() * (float) (Math.PI / 180.0);
		Vec3 vec33 = new Vec3(entity2.getBbWidth(), entity2.getBbHeight(), entity2.getBbWidth());
		List<Leashable.Wrench> list3 = new ArrayList<>();

		for (int i = 0; i < list.size(); i++) {
			Vec3 vec34 = list.get(i).multiply(vec32).yRot(-f);
			Vec3 vec35 = entity.position().add(vec34);
			Vec3 vec36 = list2.get(i).multiply(vec33).yRot(-g);
			Vec3 vec37 = entity2.position().add(vec36);
			computeDampenedSpringInteraction(vec37, vec35, d, vec3, vec34).ifPresent(list3::add);
		}

		return list3;
	}

	private static Optional<Leashable.Wrench> computeDampenedSpringInteraction(Vec3 vec3, Vec3 vec32, double d, Vec3 vec33, Vec3 vec34) {
		double e = vec32.distanceTo(vec3);
		if (e < d) {
			return Optional.empty();
		}

		Vec3 vec35 = vec3.subtract(vec32).normalize().scale(e - d);
		double f = Leashable.Wrench.torqueFromForce(vec34, vec35);
		boolean bl = vec33.dot(vec35) >= 0.0;
		if (bl) {
			vec35 = vec35.scale(0.3F);
		}

		return Optional.of(new Leashable.Wrench(vec35, f));
	}

	default boolean supportQuadLeash() {
		return false;
	}

	default Vec3[] getQuadLeashOffsets() {
		return createQuadLeashOffsets((Entity)this, 0.0, 0.5, 0.5, 0.5);
	}

	static Vec3[] createQuadLeashOffsets(Entity entity, double d, double e, double f, double g) {
		float h = entity.getBbWidth();
		double i = d * h;
		double j = e * h;
		double k = f * h;
		double l = g * entity.getBbHeight();
		return new Vec3[]{new Vec3(-k, l, j + i), new Vec3(-k, l, -j + i), new Vec3(k, l, -j + i), new Vec3(k, l, j + i)};
	}

	default Vec3 getLeashOffset(float f) {
		return this.getLeashOffset();
	}

	default Vec3 getLeashOffset() {
		Entity entity = (Entity)this;
		return new Vec3(0.0, entity.getEyeHeight(), entity.getBbWidth() * 0.4F);
	}

	default void setLeashedTo(Entity entity, boolean bl) {
		if (this != entity) {
			setLeashedTo((Entity & Leashable)this, entity, bl);
		}
	}

	private static <E extends Entity & Leashable> void setLeashedTo(E entity, Entity entity2, boolean bl) {
		Leashable.LeashData leashData = entity.getLeashData();
		if (leashData == null) {
			leashData = new Leashable.LeashData(entity2);
			entity.setLeashData(leashData);
		} else {
			Entity entity3 = leashData.leashHolder;
			leashData.setLeashHolder(entity2);
			if (entity3 != null && entity3 != entity2) {
				entity3.notifyLeasheeRemoved(entity);
			}
		}

		if (bl && entity.level() instanceof ServerLevel serverLevel) {
			serverLevel.getChunkSource().sendToTrackingPlayers(entity, new ClientboundSetEntityLinkPacket(entity, entity2));
		}

		if (entity.isPassenger()) {
			entity.stopRiding();
		}
	}

	default @Nullable Entity getLeashHolder() {
		return getLeashHolder((Entity & Leashable)this);
	}

	private static <E extends Entity & Leashable> @Nullable Entity getLeashHolder(E entity) {
		Leashable.LeashData leashData = entity.getLeashData();
		if (leashData == null) {
			return null;
		}

		if (leashData.delayedLeashHolderId != 0
			&& entity.level().isClientSide()
			&& entity.level().getEntity(leashData.delayedLeashHolderId) instanceof Entity entity2) {
			leashData.setLeashHolder(entity2);
		}

		return leashData.leashHolder;
	}

	static List<Leashable> leashableLeashedTo(Entity entity) {
		return leashableInArea(entity, leashable -> leashable.getLeashHolder() == entity);
	}

	static List<Leashable> leashableInArea(Entity entity, Predicate<Leashable> predicate) {
		return leashableInArea(entity.level(), entity.getBoundingBox().getCenter(), predicate);
	}

	static List<Leashable> leashableInArea(Level level, Vec3 vec3, Predicate<Leashable> predicate) {
		double d = 32.0;
		AABB aABB = AABB.ofSize(vec3, 32.0, 32.0, 32.0);
		return level.getEntitiesOfClass(Entity.class, aABB, entity -> entity instanceof Leashable leashable && predicate.test(leashable))
			.stream()
			.map(Leashable.class::cast)
			.toList();
	}

	final class LeashData {
		public static final Codec<Leashable.LeashData> CODEC = Codec.xor(UUIDUtil.CODEC.fieldOf("UUID").codec(), BlockPos.CODEC)
			.xmap(
				Leashable.LeashData::new,
				leashData -> {
					if (leashData.leashHolder instanceof LeashFenceKnotEntity leashFenceKnotEntity) {
						return Either.right(leashFenceKnotEntity.getPos());
					} else {
						return leashData.leashHolder != null
							? Either.left(leashData.leashHolder.getUUID())
							: Objects.requireNonNull(leashData.delayedLeashInfo, "Invalid LeashData had no attachment");
					}
				}
			);
		int delayedLeashHolderId;
		public @Nullable Entity leashHolder;
		public @Nullable Either<UUID, BlockPos> delayedLeashInfo;
		public double angularMomentum;

		private LeashData(Either<UUID, BlockPos> either) {
			this.delayedLeashInfo = either;
		}

		LeashData(Entity entity) {
			this.leashHolder = entity;
		}

		LeashData(int i) {
			this.delayedLeashHolderId = i;
		}

		public void setLeashHolder(Entity entity) {
			this.leashHolder = entity;
			this.delayedLeashInfo = null;
			this.delayedLeashHolderId = 0;
		}
	}

	record Wrench(Vec3 force, double torque) {
		static Leashable.Wrench ZERO = new Leashable.Wrench(Vec3.ZERO, 0.0);

		static double torqueFromForce(Vec3 vec3, Vec3 vec32) {
			return vec3.z * vec32.x - vec3.x * vec32.z;
		}

		static Leashable.Wrench accumulate(List<Leashable.Wrench> list) {
			if (list.isEmpty()) {
				return ZERO;
			}

			double d = 0.0;
			double e = 0.0;
			double f = 0.0;
			double g = 0.0;

			for (Leashable.Wrench wrench : list) {
				Vec3 vec3 = wrench.force;
				d += vec3.x;
				e += vec3.y;
				f += vec3.z;
				g += wrench.torque;
			}

			return new Leashable.Wrench(new Vec3(d, e, f), g);
		}

		public Leashable.Wrench scale(double d) {
			return new Leashable.Wrench(this.force.scale(d), this.torque * d);
		}
	}
}
