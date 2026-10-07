package net.minecraft.world.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Interaction extends Entity implements Attackable, Targeting {
	private static final EntityDataAccessor<Float> DATA_WIDTH_ID = SynchedEntityData.defineId(Interaction.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_HEIGHT_ID = SynchedEntityData.defineId(Interaction.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> DATA_RESPONSE_ID = SynchedEntityData.defineId(Interaction.class, EntityDataSerializers.BOOLEAN);
	private static final String TAG_WIDTH = "width";
	private static final String TAG_HEIGHT = "height";
	private static final String TAG_ATTACK = "attack";
	private static final String TAG_INTERACTION = "interaction";
	private static final String TAG_RESPONSE = "response";
	private static final float DEFAULT_WIDTH = 1.0F;
	private static final float DEFAULT_HEIGHT = 1.0F;
	private static final boolean DEFAULT_RESPONSE = false;
	private Interaction.@Nullable PlayerAction attack;
	private Interaction.@Nullable PlayerAction interaction;

	public Interaction(EntityType<?> entityType, Level level) {
		super(entityType, level);
		this.noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_WIDTH_ID, 1.0F);
		builder.define(DATA_HEIGHT_ID, 1.0F);
		builder.define(DATA_RESPONSE_ID, false);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		this.setWidth(valueInput.getFloatOr("width", 1.0F));
		this.setHeight(valueInput.getFloatOr("height", 1.0F));
		this.attack = valueInput.read("attack", Interaction.PlayerAction.CODEC).orElse(null);
		this.interaction = valueInput.read("interaction", Interaction.PlayerAction.CODEC).orElse(null);
		this.setResponse(valueInput.getBooleanOr("response", false));
		this.setBoundingBox(this.makeBoundingBox());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		valueOutput.putFloat("width", this.getWidth());
		valueOutput.putFloat("height", this.getHeight());
		valueOutput.storeNullable("attack", Interaction.PlayerAction.CODEC, this.attack);
		valueOutput.storeNullable("interaction", Interaction.PlayerAction.CODEC, this.interaction);
		valueOutput.putBoolean("response", this.getResponse());
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> entityDataAccessor) {
		super.onSyncedDataUpdated(entityDataAccessor);
		if (DATA_HEIGHT_ID.equals(entityDataAccessor) || DATA_WIDTH_ID.equals(entityDataAccessor)) {
			this.refreshDimensions();
		}
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public PushReaction getPistonPushReaction() {
		return PushReaction.IGNORE;
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	public boolean skipAttackInteraction(Entity entity) {
		if (entity instanceof Player player) {
			this.attack = new Interaction.PlayerAction(player.getUUID(), this.level().getGameTime());
			if (player instanceof ServerPlayer serverPlayer) {
				CriteriaTriggers.PLAYER_HURT_ENTITY.trigger(serverPlayer, this, player.damageSources().generic(), 1.0F, 1.0F, false);
			}

			return !this.getResponse();
		} else {
			return false;
		}
	}

	@Override
	public final boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float f) {
		return false;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand interactionHand) {
		if (this.level().isClientSide()) {
			return this.getResponse() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
		}

		this.interaction = new Interaction.PlayerAction(player.getUUID(), this.level().getGameTime());
		return InteractionResult.CONSUME;
	}

	@Override
	public void tick() {
	}

	@Override
	public @Nullable LivingEntity getLastAttacker() {
		return this.attack != null ? this.level().getPlayerByUUID(this.attack.player()) : null;
	}

	@Override
	public @Nullable LivingEntity getTarget() {
		return this.interaction != null ? this.level().getPlayerByUUID(this.interaction.player()) : null;
	}

	public final void setWidth(float f) {
		this.entityData.set(DATA_WIDTH_ID, f);
	}

	public final float getWidth() {
		return this.entityData.get(DATA_WIDTH_ID);
	}

	public final void setHeight(float f) {
		this.entityData.set(DATA_HEIGHT_ID, f);
	}

	public final float getHeight() {
		return this.entityData.get(DATA_HEIGHT_ID);
	}

	public final void setResponse(boolean bl) {
		this.entityData.set(DATA_RESPONSE_ID, bl);
	}

	public final boolean getResponse() {
		return this.entityData.get(DATA_RESPONSE_ID);
	}

	private EntityDimensions getDimensions() {
		return EntityDimensions.scalable(this.getWidth(), this.getHeight());
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		return this.getDimensions();
	}

	@Override
	protected AABB makeBoundingBox(Vec3 vec3) {
		return this.getDimensions().makeBoundingBox(vec3);
	}

	record PlayerAction(UUID player, long timestamp) {
		public static final Codec<Interaction.PlayerAction> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					UUIDUtil.CODEC.fieldOf("player").forGetter(Interaction.PlayerAction::player),
					Codec.LONG.fieldOf("timestamp").forGetter(Interaction.PlayerAction::timestamp)
				)
				.apply(instance, Interaction.PlayerAction::new)
		);
	}
}
