package net.minecraft.world.entity.projectile.throwableitemprojectile;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ThrownEnderpearl extends ThrowableItemProjectile {
	private long ticketTimer = 0L;

	public ThrownEnderpearl(EntityType<? extends ThrownEnderpearl> entityType, Level level) {
		super(entityType, level);
	}

	public ThrownEnderpearl(Level level, LivingEntity livingEntity, ItemStack itemStack) {
		super(EntityType.ENDER_PEARL, livingEntity, level, itemStack);
	}

	@Override
	protected Item getDefaultItem() {
		return Items.ENDER_PEARL;
	}

	@Override
	protected void setOwner(@Nullable EntityReference<Entity> entityReference) {
		this.deregisterFromCurrentOwner();
		super.setOwner(entityReference);
		this.registerToCurrentOwner();
	}

	private void deregisterFromCurrentOwner() {
		if (this.getOwner() instanceof ServerPlayer serverPlayer) {
			serverPlayer.deregisterEnderPearl(this);
		}
	}

	private void registerToCurrentOwner() {
		if (this.getOwner() instanceof ServerPlayer serverPlayer) {
			serverPlayer.registerEnderPearl(this);
		}
	}

	@Override
	public @Nullable Entity getOwner() {
		return this.owner != null && this.level() instanceof ServerLevel serverLevel ? this.owner.getEntity(serverLevel, Entity.class) : super.getOwner();
	}

	private static @Nullable Entity findOwnerIncludingDeadPlayer(ServerLevel serverLevel, UUID uUID) {
		Entity entity = serverLevel.getEntityInAnyDimension(uUID);
		return entity != null ? entity : serverLevel.getServer().getPlayerList().getPlayer(uUID);
	}

	@Override
	protected void onHitEntity(EntityHitResult entityHitResult) {
		super.onHitEntity(entityHitResult);
		entityHitResult.getEntity().hurt(this.damageSources().thrown(this, this.getOwner()), 0.0F);
	}

	@Override
	protected void onHit(HitResult hitResult) {
		super.onHit(hitResult);

		for (int i = 0; i < 32; i++) {
			this.level()
				.addParticle(
					ParticleTypes.PORTAL, this.getX(), this.getY() + this.random.nextDouble() * 2.0, this.getZ(), this.random.nextGaussian(), 0.0, this.random.nextGaussian()
				);
		}

		if (this.level() instanceof ServerLevel serverLevel && !this.isRemoved()) {
			Entity entity = this.getOwner();
			if (entity != null && isAllowedToTeleportOwner(entity, serverLevel)) {
				Vec3 vec3 = this.oldPosition();
				if (entity instanceof ServerPlayer serverPlayer) {
					if (serverPlayer.connection.isAcceptingMessages()) {
						if (this.random.nextFloat() < 0.05F && serverLevel.isSpawningMonsters()) {
							Endermite endermite = EntityType.ENDERMITE.create(serverLevel, EntitySpawnReason.TRIGGERED);
							if (endermite != null) {
								endermite.snapTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
								serverLevel.addFreshEntity(endermite);
							}
						}

						if (this.isOnPortalCooldown()) {
							entity.setPortalCooldown();
						}

						ServerPlayer serverPlayer2 = serverPlayer.teleport(
							new TeleportTransition(serverLevel, vec3, Vec3.ZERO, 0.0F, 0.0F, Relative.union(Relative.ROTATION, Relative.DELTA), TeleportTransition.DO_NOTHING)
						);
						if (serverPlayer2 != null) {
							serverPlayer2.resetFallDistance();
							serverPlayer2.resetCurrentImpulseContext();
							serverPlayer2.hurtServer(serverPlayer.level(), this.damageSources().enderPearl(), 5.0F);
						}

						this.playSound(serverLevel, vec3);
					}
				} else {
					Entity entity2 = entity.teleport(
						new TeleportTransition(serverLevel, vec3, entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(), TeleportTransition.DO_NOTHING)
					);
					if (entity2 != null) {
						entity2.resetFallDistance();
					}

					this.playSound(serverLevel, vec3);
				}

				this.discard();
			} else {
				this.discard();
			}
		}
	}

	private static boolean isAllowedToTeleportOwner(Entity entity, Level level) {
		if (entity.level().dimension() == level.dimension()) {
			return !(entity instanceof LivingEntity livingEntity) ? entity.isAlive() : livingEntity.isAlive() && !livingEntity.isSleeping();
		} else {
			return entity.canUsePortal(true);
		}
	}

	@Override
	public void tick() {
		if (this.level() instanceof ServerLevel serverLevel) {
			int var7 = SectionPos.blockToSectionCoord(this.position().x());
			int j = SectionPos.blockToSectionCoord(this.position().z());
			Entity entity = this.owner != null ? findOwnerIncludingDeadPlayer(serverLevel, this.owner.getUUID()) : null;
			if (entity instanceof ServerPlayer serverPlayer
				&& !entity.isAlive()
				&& !serverPlayer.wonGame
				&& serverPlayer.level().getGameRules().get(GameRules.ENDER_PEARLS_VANISH_ON_DEATH)) {
				this.discard();
			} else {
				super.tick();
			}

			if (this.isAlive()) {
				BlockPos blockPos = BlockPos.containing(this.position());
				if ((--this.ticketTimer <= 0L || var7 != SectionPos.blockToSectionCoord(blockPos.getX()) || j != SectionPos.blockToSectionCoord(blockPos.getZ()))
					&& entity instanceof ServerPlayer serverPlayer2) {
					this.ticketTimer = serverPlayer2.registerAndUpdateEnderPearlTicket(this);
				}
			}
		} else {
			super.tick();
		}
	}

	private void playSound(Level level, Vec3 vec3) {
		level.playSound(null, vec3.x, vec3.y, vec3.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS);
	}

	@Override
	public @Nullable Entity teleport(TeleportTransition teleportTransition) {
		Entity entity = super.teleport(teleportTransition);
		if (entity != null) {
			entity.placePortalTicket(BlockPos.containing(entity.position()));
		}

		return entity;
	}

	@Override
	public boolean canTeleport(Level level, Level level2) {
		return level.dimension() == Level.END && level2.dimension() == Level.OVERWORLD && this.getOwner() instanceof ServerPlayer serverPlayer
			? super.canTeleport(level, level2) && serverPlayer.seenCredits
			: super.canTeleport(level, level2);
	}

	@Override
	protected void onInsideBlock(BlockState blockState) {
		super.onInsideBlock(blockState);
		if (blockState.is(Blocks.END_GATEWAY) && this.getOwner() instanceof ServerPlayer serverPlayer) {
			serverPlayer.onInsideBlock(blockState);
		}
	}

	@Override
	public void onRemoval(Entity.RemovalReason removalReason) {
		if (removalReason != Entity.RemovalReason.UNLOADED_WITH_PLAYER) {
			this.deregisterFromCurrentOwner();
		}

		super.onRemoval(removalReason);
	}

	@Override
	public void onAboveBubbleColumn(boolean bl, BlockPos blockPos) {
		Entity.handleOnAboveBubbleColumn(this, bl, blockPos);
	}

	@Override
	public void onInsideBubbleColumn(boolean bl) {
		Entity.handleOnInsideBubbleColumn(this, bl);
	}
}
