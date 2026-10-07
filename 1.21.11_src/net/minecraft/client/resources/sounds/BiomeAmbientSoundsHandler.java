package net.minecraft.client.resources.sounds;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.AmbientAdditionsSettings;
import net.minecraft.world.attribute.AmbientSounds;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BiomeAmbientSoundsHandler implements AmbientSoundHandler {
	private static final int LOOP_SOUND_CROSS_FADE_TIME = 40;
	private static final float SKY_MOOD_RECOVERY_RATE = 0.001F;
	private final LocalPlayer player;
	private final SoundManager soundManager;
	private final RandomSource random;
	private final Object2ObjectArrayMap<Holder<SoundEvent>, BiomeAmbientSoundsHandler.LoopSoundInstance> loopSounds = new Object2ObjectArrayMap<>();
	private float moodiness;
	private @Nullable Holder<SoundEvent> previousLoopSound;

	public BiomeAmbientSoundsHandler(LocalPlayer localPlayer, SoundManager soundManager) {
		this.random = localPlayer.level().getRandom();
		this.player = localPlayer;
		this.soundManager = soundManager;
	}

	public float getMoodiness() {
		return this.moodiness;
	}

	@Override
	public void tick() {
		this.loopSounds.values().removeIf(AbstractTickableSoundInstance::isStopped);
		Level level = this.player.level();
		EnvironmentAttributeSystem environmentAttributeSystem = level.environmentAttributes();
		AmbientSounds ambientSounds = environmentAttributeSystem.getValue(EnvironmentAttributes.AMBIENT_SOUNDS, this.player.position());
		Holder<SoundEvent> holder = ambientSounds.loop().orElse(null);
		if (!Objects.equals(holder, this.previousLoopSound)) {
			this.previousLoopSound = holder;
			this.loopSounds.values().forEach(BiomeAmbientSoundsHandler.LoopSoundInstance::fadeOut);
			if (holder != null) {
				this.loopSounds.compute(holder, (holder2, loopSoundInstance) -> {
					if (loopSoundInstance == null) {
						loopSoundInstance = new BiomeAmbientSoundsHandler.LoopSoundInstance(holder.value());
						this.soundManager.play(loopSoundInstance);
					}

					loopSoundInstance.fadeIn();
					return (BiomeAmbientSoundsHandler.LoopSoundInstance)loopSoundInstance;
				});
			}
		}

		for (AmbientAdditionsSettings ambientAdditionsSettings : ambientSounds.additions()) {
			if (this.random.nextDouble() < ambientAdditionsSettings.tickChance()) {
				this.soundManager.play(SimpleSoundInstance.forAmbientAddition(ambientAdditionsSettings.soundEvent().value()));
			}
		}

		ambientSounds.mood()
			.ifPresent(
				ambientMoodSettings -> {
					int i = ambientMoodSettings.blockSearchExtent() * 2 + 1;
					BlockPos blockPos = BlockPos.containing(
						this.player.getX() + this.random.nextInt(i) - ambientMoodSettings.blockSearchExtent(),
						this.player.getEyeY() + this.random.nextInt(i) - ambientMoodSettings.blockSearchExtent(),
						this.player.getZ() + this.random.nextInt(i) - ambientMoodSettings.blockSearchExtent()
					);
					int j = level.getBrightness(LightLayer.SKY, blockPos);
					if (j > 0) {
						this.moodiness -= j / 15.0F * 0.001F;
					} else {
						this.moodiness = this.moodiness - (float)(level.getBrightness(LightLayer.BLOCK, blockPos) - 1) / ambientMoodSettings.tickDelay();
					}

					if (this.moodiness >= 1.0F) {
						double d = blockPos.getX() + 0.5;
						double e = blockPos.getY() + 0.5;
						double f = blockPos.getZ() + 0.5;
						double g = d - this.player.getX();
						double h = e - this.player.getEyeY();
						double k = f - this.player.getZ();
						double l = Math.sqrt(g * g + h * h + k * k);
						double m = l + ambientMoodSettings.soundPositionOffset();
						SimpleSoundInstance simpleSoundInstance = SimpleSoundInstance.forAmbientMood(
							ambientMoodSettings.soundEvent().value(), this.random, this.player.getX() + g / l * m, this.player.getEyeY() + h / l * m, this.player.getZ() + k / l * m
						);
						this.soundManager.play(simpleSoundInstance);
						this.moodiness = 0.0F;
					} else {
						this.moodiness = Math.max(this.moodiness, 0.0F);
					}
				}
			);
	}

	@Environment(EnvType.CLIENT)
	public static class LoopSoundInstance extends AbstractTickableSoundInstance {
		private int fadeDirection;
		private int fade;

		public LoopSoundInstance(SoundEvent soundEvent) {
			super(soundEvent, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
			this.looping = true;
			this.delay = 0;
			this.volume = 1.0F;
			this.relative = true;
		}

		@Override
		public void tick() {
			if (this.fade < 0) {
				this.stop();
			}

			this.fade = this.fade + this.fadeDirection;
			this.volume = Mth.clamp(this.fade / 40.0F, 0.0F, 1.0F);
		}

		public void fadeOut() {
			this.fade = Math.min(this.fade, 40);
			this.fadeDirection = -1;
		}

		public void fadeIn() {
			this.fade = Math.max(0, this.fade);
			this.fadeDirection = 1;
		}
	}
}
