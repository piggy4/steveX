package net.minecraft.client.resources.sounds;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance} injected by mod fabric-sound-api-v1</p>
 */
@Environment(EnvType.CLIENT)
public interface SoundInstance extends FabricSoundInstance {
	Identifier getIdentifier();

	@Nullable WeighedSoundEvents resolve(SoundManager soundManager);

	@Nullable Sound getSound();

	SoundSource getSource();

	boolean isLooping();

	boolean isRelative();

	int getDelay();

	float getVolume();

	float getPitch();

	double getX();

	double getY();

	double getZ();

	SoundInstance.Attenuation getAttenuation();

	default boolean canStartSilent() {
		return false;
	}

	default boolean canPlaySound() {
		return true;
	}

	static RandomSource createUnseededRandom() {
		return RandomSource.create();
	}

	@Environment(EnvType.CLIENT)
	enum Attenuation {
		NONE,
		LINEAR;
	}
}
