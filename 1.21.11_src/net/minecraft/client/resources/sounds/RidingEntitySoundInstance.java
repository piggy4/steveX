package net.minecraft.client.resources.sounds;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

@Environment(EnvType.CLIENT)
public class RidingEntitySoundInstance extends AbstractTickableSoundInstance {
	private final Player player;
	private final Entity entity;
	private final boolean underwaterSound;
	private final float volumeMin;
	private final float volumeMax;
	private final float volumeAmplifier;

	public RidingEntitySoundInstance(Player player, Entity entity, boolean bl, SoundEvent soundEvent, SoundSource soundSource, float f, float g, float h) {
		super(soundEvent, soundSource, SoundInstance.createUnseededRandom());
		this.player = player;
		this.entity = entity;
		this.underwaterSound = bl;
		this.volumeMin = f;
		this.volumeMax = g;
		this.volumeAmplifier = h;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.looping = true;
		this.delay = 0;
		this.volume = f;
	}

	@Override
	public boolean canPlaySound() {
		return !this.entity.isSilent();
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	protected boolean shouldNotPlayUnderwaterSound() {
		return this.underwaterSound != this.entity.isUnderWater();
	}

	protected float getEntitySpeed() {
		return (float)this.entity.getDeltaMovement().length();
	}

	protected boolean shoudlPlaySound() {
		return true;
	}

	@Override
	public void tick() {
		if (this.entity.isRemoved() || !this.player.isPassenger() || this.player.getVehicle() != this.entity) {
			this.stop();
		} else if (this.shouldNotPlayUnderwaterSound()) {
			this.volume = this.volumeMin;
		} else {
			float f = this.getEntitySpeed();
			if (f >= 0.01F && this.shoudlPlaySound()) {
				this.volume = this.volumeAmplifier * Mth.clampedLerp(f, this.volumeMin, this.volumeMax);
			} else {
				this.volume = this.volumeMin;
			}
		}
	}
}
