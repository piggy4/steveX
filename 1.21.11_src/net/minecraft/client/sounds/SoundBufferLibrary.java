package net.minecraft.client.sounds;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.audio.SoundBuffer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.Util;

@Environment(EnvType.CLIENT)
public class SoundBufferLibrary {
	private final ResourceProvider resourceManager;
	private final Map<Identifier, CompletableFuture<SoundBuffer>> cache = Maps.newHashMap();

	public SoundBufferLibrary(ResourceProvider resourceProvider) {
		this.resourceManager = resourceProvider;
	}

	public CompletableFuture<SoundBuffer> getCompleteBuffer(Identifier identifier) {
		return this.cache.computeIfAbsent(identifier, identifierx -> CompletableFuture.supplyAsync(() -> {
			try (
				InputStream inputStream = this.resourceManager.open(identifierx);
				FiniteAudioStream finiteAudioStream = new JOrbisAudioStream(inputStream);
			) {
				ByteBuffer byteBuffer = finiteAudioStream.readAll();
				return new SoundBuffer(byteBuffer, finiteAudioStream.getFormat());
			} catch (IOException iOException) {
				throw new CompletionException(iOException);
			}
		}, Util.nonCriticalIoPool()));
	}

	public CompletableFuture<AudioStream> getStream(Identifier identifier, boolean bl) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				InputStream inputStream = this.resourceManager.open(identifier);
				return bl ? new LoopingAudioStream(JOrbisAudioStream::new, inputStream) : new JOrbisAudioStream(inputStream);
			} catch (IOException iOException) {
				throw new CompletionException(iOException);
			}
		}, Util.nonCriticalIoPool());
	}

	public void clear() {
		this.cache.values().forEach(completableFuture -> completableFuture.thenAccept(SoundBuffer::discardAlBuffer));
		this.cache.clear();
	}

	public CompletableFuture<?> preload(Collection<Sound> collection) {
		return CompletableFuture.allOf(collection.stream().map(sound -> this.getCompleteBuffer(sound.getPath())).toArray(CompletableFuture[]::new));
	}
}
