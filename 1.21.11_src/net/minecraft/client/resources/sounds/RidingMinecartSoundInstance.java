package net.minecraft.client.resources.sounds;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;

@Environment(EnvType.CLIENT)
public class RidingMinecartSoundInstance extends RidingEntitySoundInstance {
	private final Player player;
	private final AbstractMinecart minecart;
	private final boolean underwaterSound;

	public RidingMinecartSoundInstance(Player player, AbstractMinecart abstractMinecart, boolean bl, SoundEvent soundEvent, float f, float g, float h) {
		super(player, abstractMinecart, bl, soundEvent, SoundSource.NEUTRAL, f, g, h);
		this.player = player;
		this.minecart = abstractMinecart;
		this.underwaterSound = bl;
	}

	@Override
	protected boolean shouldNotPlayUnderwaterSound() {
		return this.underwaterSound != this.player.isUnderWater();
	}

	@Override
	protected float getEntitySpeed() {
		return (float)this.minecart.getDeltaMovement().horizontalDistance();
	}

	@Override
	protected boolean shoudlPlaySound() {
		return this.minecart.isOnRails() || !(this.minecart.getBehavior() instanceof NewMinecartBehavior);
	}
}
