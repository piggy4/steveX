package net.minecraft.client.resources.sounds;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class DirectionalSoundInstance extends AbstractTickableSoundInstance {
	private final Camera camera;
	private final float xAngle;
	private final float yAngle;

	public DirectionalSoundInstance(SoundEvent soundEvent, SoundSource soundSource, RandomSource randomSource, Camera camera, float f, float g) {
		super(soundEvent, soundSource, randomSource);
		this.camera = camera;
		this.xAngle = f;
		this.yAngle = g;
		this.setPosition();
	}

	private void setPosition() {
		Vec3 vec3 = Vec3.directionFromRotation(this.xAngle, this.yAngle).scale(10.0);
		this.x = this.camera.position().x + vec3.x;
		this.y = this.camera.position().y + vec3.y;
		this.z = this.camera.position().z + vec3.z;
		this.attenuation = SoundInstance.Attenuation.NONE;
	}

	@Override
	public void tick() {
		this.setPosition();
	}
}
