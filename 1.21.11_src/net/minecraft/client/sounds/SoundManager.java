package net.minecraft.client.sounds;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.audio.ListenerTransform;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.Options;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundEventRegistration;
import net.minecraft.client.resources.sounds.SoundEventRegistrationSerializer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.Zone;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.MultipliedFloats;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class SoundManager extends SimplePreparableReloadListener<SoundManager.Preparations> {
	public static final Identifier EMPTY_SOUND_LOCATION = Identifier.withDefaultNamespace("empty");
	public static final Sound EMPTY_SOUND = new Sound(EMPTY_SOUND_LOCATION, ConstantFloat.of(1.0F), ConstantFloat.of(1.0F), 1, Sound.Type.FILE, false, false, 16);
	public static final Identifier INTENTIONALLY_EMPTY_SOUND_LOCATION = Identifier.withDefaultNamespace("intentionally_empty");
	public static final WeighedSoundEvents INTENTIONALLY_EMPTY_SOUND_EVENT = new WeighedSoundEvents(INTENTIONALLY_EMPTY_SOUND_LOCATION, null);
	public static final Sound INTENTIONALLY_EMPTY_SOUND = new Sound(
		INTENTIONALLY_EMPTY_SOUND_LOCATION, ConstantFloat.of(1.0F), ConstantFloat.of(1.0F), 1, Sound.Type.FILE, false, false, 16
	);
	static final Logger LOGGER = LogUtils.getLogger();
	private static final String SOUNDS_PATH = "sounds.json";
	private static final Gson GSON = new GsonBuilder().registerTypeAdapter(SoundEventRegistration.class, new SoundEventRegistrationSerializer()).create();
	private static final TypeToken<Map<String, SoundEventRegistration>> SOUND_EVENT_REGISTRATION_TYPE = new TypeToken<Map<String, SoundEventRegistration>>() {};
	private final Map<Identifier, WeighedSoundEvents> registry = Maps.newHashMap();
	private final SoundEngine soundEngine;
	private final Map<Identifier, Resource> soundCache = new HashMap<>();

	public SoundManager(Options options) {
		this.soundEngine = new SoundEngine(this, options, ResourceProvider.fromMap(this.soundCache));
	}

	protected SoundManager.Preparations prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
		SoundManager.Preparations preparations = new SoundManager.Preparations();

		try (Zone zone = profilerFiller.zone("list")) {
			preparations.listResources(resourceManager);
		}

		for (String string : resourceManager.getNamespaces()) {
			try (Zone zone2 = profilerFiller.zone(string)) {
				for (Resource resource : resourceManager.getResourceStack(Identifier.fromNamespaceAndPath(string, "sounds.json"))) {
					profilerFiller.push(resource.sourcePackId());

					try (Reader reader = resource.openAsReader()) {
						profilerFiller.push("parse");
						Map<String, SoundEventRegistration> map = GsonHelper.fromJson(GSON, reader, SOUND_EVENT_REGISTRATION_TYPE);
						profilerFiller.popPush("register");

						for (Entry<String, SoundEventRegistration> entry : map.entrySet()) {
							preparations.handleRegistration(Identifier.fromNamespaceAndPath(string, entry.getKey()), entry.getValue());
						}

						profilerFiller.pop();
					} catch (RuntimeException runtimeException) {
						LOGGER.warn("Invalid {} in resourcepack: '{}'", "sounds.json", resource.sourcePackId(), runtimeException);
					}

					profilerFiller.pop();
				}
			} catch (IOException var21) {
			}
		}

		return preparations;
	}

	protected void apply(SoundManager.Preparations preparations, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
		preparations.apply(this.registry, this.soundCache, this.soundEngine);
		if (SharedConstants.IS_RUNNING_IN_IDE) {
			for (Identifier identifier : this.registry.keySet()) {
				WeighedSoundEvents weighedSoundEvents = this.registry.get(identifier);
				if (!ComponentUtils.isTranslationResolvable(weighedSoundEvents.getSubtitle()) && BuiltInRegistries.SOUND_EVENT.containsKey(identifier)) {
					LOGGER.error("Missing subtitle {} for sound event: {}", weighedSoundEvents.getSubtitle(), identifier);
				}
			}
		}

		if (LOGGER.isDebugEnabled()) {
			for (Identifier identifier : this.registry.keySet()) {
				if (!BuiltInRegistries.SOUND_EVENT.containsKey(identifier)) {
					LOGGER.debug("Not having sound event for: {}", identifier);
				}
			}
		}

		this.soundEngine.reload();
	}

	public List<String> getAvailableSoundDevices() {
		return this.soundEngine.getAvailableSoundDevices();
	}

	public ListenerTransform getListenerTransform() {
		return this.soundEngine.getListenerTransform();
	}

	static boolean validateSoundResource(Sound sound, Identifier identifier, ResourceProvider resourceProvider) {
		Identifier identifier2 = sound.getPath();
		if (resourceProvider.getResource(identifier2).isEmpty()) {
			LOGGER.warn("File {} does not exist, cannot add it to event {}", identifier2, identifier);
			return false;
		} else {
			return true;
		}
	}

	public @Nullable WeighedSoundEvents getSoundEvent(Identifier identifier) {
		return this.registry.get(identifier);
	}

	public Collection<Identifier> getAvailableSounds() {
		return this.registry.keySet();
	}

	public void queueTickingSound(TickableSoundInstance tickableSoundInstance) {
		this.soundEngine.queueTickingSound(tickableSoundInstance);
	}

	public SoundEngine.PlayResult play(SoundInstance soundInstance) {
		return this.soundEngine.play(soundInstance);
	}

	public void playDelayed(SoundInstance soundInstance, int i) {
		this.soundEngine.playDelayed(soundInstance, i);
	}

	public void updateSource(Camera camera) {
		this.soundEngine.updateSource(camera);
	}

	public void pauseAllExcept(SoundSource... soundSources) {
		this.soundEngine.pauseAllExcept(soundSources);
	}

	public void stop() {
		this.soundEngine.stopAll();
	}

	public void destroy() {
		this.soundEngine.destroy();
	}

	public void emergencyShutdown() {
		this.soundEngine.emergencyShutdown();
	}

	public void tick(boolean bl) {
		this.soundEngine.tick(bl);
	}

	public void resume() {
		this.soundEngine.resume();
	}

	public void refreshCategoryVolume(SoundSource soundSource) {
		this.soundEngine.refreshCategoryVolume(soundSource);
	}

	public void stop(SoundInstance soundInstance) {
		this.soundEngine.stop(soundInstance);
	}

	public void updateCategoryVolume(SoundSource soundSource, float f) {
		this.soundEngine.updateCategoryVolume(soundSource, f);
	}

	public boolean isActive(SoundInstance soundInstance) {
		return this.soundEngine.isActive(soundInstance);
	}

	public void addListener(SoundEventListener soundEventListener) {
		this.soundEngine.addEventListener(soundEventListener);
	}

	public void removeListener(SoundEventListener soundEventListener) {
		this.soundEngine.removeEventListener(soundEventListener);
	}

	public void stop(@Nullable Identifier identifier, @Nullable SoundSource soundSource) {
		this.soundEngine.stop(identifier, soundSource);
	}

	public String getDebugString() {
		return this.soundEngine.getDebugString();
	}

	public void reload() {
		this.soundEngine.reload();
	}

	@Environment(EnvType.CLIENT)
	protected static class Preparations {
		final Map<Identifier, WeighedSoundEvents> registry = Maps.newHashMap();
		private Map<Identifier, Resource> soundCache = Map.of();

		void listResources(ResourceManager resourceManager) {
			this.soundCache = Sound.SOUND_LISTER.listMatchingResources(resourceManager);
		}

		void handleRegistration(Identifier identifier, SoundEventRegistration soundEventRegistration) {
			WeighedSoundEvents weighedSoundEvents = this.registry.get(identifier);
			boolean bl = weighedSoundEvents == null;
			if (bl || soundEventRegistration.isReplace()) {
				if (!bl) {
					SoundManager.LOGGER.debug("Replaced sound event location {}", identifier);
				}

				weighedSoundEvents = new WeighedSoundEvents(identifier, soundEventRegistration.getSubtitle());
				this.registry.put(identifier, weighedSoundEvents);
			}

			ResourceProvider resourceProvider = ResourceProvider.fromMap(this.soundCache);

			for (final Sound sound : soundEventRegistration.getSounds()) {
				final Identifier identifier2 = sound.getLocation();
				Weighted<Sound> weighted;
				switch (sound.getType()) {
					case FILE:
						if (!SoundManager.validateSoundResource(sound, identifier, resourceProvider)) {
							continue;
						}

						weighted = sound;
						break;
					case SOUND_EVENT:
						weighted = new Weighted<Sound>() {
							@Override
							public int getWeight() {
								WeighedSoundEvents weighedSoundEventsx = Preparations.this.registry.get(identifier2);
								return weighedSoundEventsx == null ? 0 : weighedSoundEventsx.getWeight();
							}

							public Sound getSound(RandomSource randomSource) {
								WeighedSoundEvents weighedSoundEventsx = Preparations.this.registry.get(identifier2);
								if (weighedSoundEventsx == null) {
									return SoundManager.EMPTY_SOUND;
								}

								Sound soundx = weighedSoundEventsx.getSound(randomSource);
								return new Sound(
									soundx.getLocation(),
									new MultipliedFloats(soundx.getVolume(), sound.getVolume()),
									new MultipliedFloats(soundx.getPitch(), sound.getPitch()),
									sound.getWeight(),
									Sound.Type.FILE,
									soundx.shouldStream() || sound.shouldStream(),
									soundx.shouldPreload(),
									soundx.getAttenuationDistance()
								);
							}

							@Override
							public void preloadIfRequired(SoundEngine soundEngine) {
								WeighedSoundEvents weighedSoundEventsx = Preparations.this.registry.get(identifier2);
								if (weighedSoundEventsx != null) {
									weighedSoundEventsx.preloadIfRequired(soundEngine);
								}
							}
						};
						break;
					default:
						throw new IllegalStateException("Unknown SoundEventRegistration type: " + sound.getType());
				}

				weighedSoundEvents.addSound(weighted);
			}
		}

		public void apply(Map<Identifier, WeighedSoundEvents> map, Map<Identifier, Resource> map2, SoundEngine soundEngine) {
			map.clear();
			map2.clear();
			map2.putAll(this.soundCache);

			for (Entry<Identifier, WeighedSoundEvents> entry : this.registry.entrySet()) {
				map.put(entry.getKey(), entry.getValue());
				entry.getValue().preloadIfRequired(soundEngine);
			}
		}
	}
}
