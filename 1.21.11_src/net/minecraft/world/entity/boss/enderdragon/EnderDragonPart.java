package net.minecraft.world.entity.boss.enderdragon;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class EnderDragonPart extends Entity {
	public final EnderDragon parentMob;
	public final String name;
	private final EntityDimensions size;

	public EnderDragonPart(EnderDragon enderDragon, String string, float f, float g) {
		super(enderDragon.getType(), enderDragon.level());
		this.size = EntityDimensions.scalable(f, g);
		this.refreshDimensions();
		this.parentMob = enderDragon;
		this.name = string;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public @Nullable ItemStack getPickResult() {
		return this.parentMob.getPickResult();
	}

	@Override
	public final boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float f) {
		return this.isInvulnerableToBase(damageSource) ? false : this.parentMob.hurt(serverLevel, this, damageSource, f);
	}

	@Override
	public boolean is(Entity entity) {
		return this == entity || this.parentMob == entity;
	}

	@Override
	public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
		throw new UnsupportedOperationException();
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		return this.size;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}
}
