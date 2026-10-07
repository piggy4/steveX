package net.minecraft.client.resources;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.hash.Hashing;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.SignatureState;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import com.mojang.authlib.properties.Property;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Services;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class SkinManager {
	static final Logger LOGGER = LogUtils.getLogger();
	private final Services services;
	final SkinTextureDownloader skinTextureDownloader;
	private final LoadingCache<SkinManager.CacheKey, CompletableFuture<Optional<PlayerSkin>>> skinCache;
	private final SkinManager.TextureCache skinTextures;
	private final SkinManager.TextureCache capeTextures;
	private final SkinManager.TextureCache elytraTextures;

	public SkinManager(Path path, Services services, SkinTextureDownloader skinTextureDownloader, Executor executor) {
		this.services = services;
		this.skinTextureDownloader = skinTextureDownloader;
		this.skinTextures = new SkinManager.TextureCache(path, Type.SKIN);
		this.capeTextures = new SkinManager.TextureCache(path, Type.CAPE);
		this.elytraTextures = new SkinManager.TextureCache(path, Type.ELYTRA);
		this.skinCache = CacheBuilder.newBuilder()
			.expireAfterAccess(Duration.ofSeconds(15L))
			.build(
				new CacheLoader<SkinManager.CacheKey, CompletableFuture<Optional<PlayerSkin>>>() {
					public CompletableFuture<Optional<PlayerSkin>> load(SkinManager.CacheKey cacheKey) {
						return CompletableFuture.<MinecraftProfileTextures>supplyAsync(() -> {
								Property property = cacheKey.packedTextures();
								if (property == null) {
									return MinecraftProfileTextures.EMPTY;
								}

								MinecraftProfileTextures minecraftProfileTextures = services.sessionService().unpackTextures(property);
								if (minecraftProfileTextures.signatureState() == SignatureState.INVALID) {
									SkinManager.LOGGER.warn("Profile contained invalid signature for textures property (profile id: {})", cacheKey.profileId());
								}

								return minecraftProfileTextures;
							}, Util.backgroundExecutor().forName("unpackSkinTextures"))
							.thenComposeAsync(minecraftProfileTextures -> SkinManager.this.registerTextures(cacheKey.profileId(), minecraftProfileTextures), executor)
							.handle((playerSkin, throwable) -> {
								if (throwable != null) {
									SkinManager.LOGGER.warn("Failed to load texture for profile {}", cacheKey.profileId, throwable);
								}

								return Optional.ofNullable(playerSkin);
							});
					}
				}
			);
	}

	public Supplier<PlayerSkin> createLookup(GameProfile gameProfile, boolean bl) {
		CompletableFuture<Optional<PlayerSkin>> completableFuture = this.get(gameProfile);
		PlayerSkin playerSkin = DefaultPlayerSkin.get(gameProfile);
		if (SharedConstants.DEBUG_DEFAULT_SKIN_OVERRIDE) {
			return () -> playerSkin;
		} else {
			Optional<PlayerSkin> optional = completableFuture.getNow(null);
			if (optional != null) {
				PlayerSkin playerSkin2 = optional.filter(playerSkinx -> !bl || playerSkinx.secure()).orElse(playerSkin);
				return () -> playerSkin2;
			} else {
				return () -> completableFuture.getNow(Optional.empty()).filter(playerSkinxx -> !bl || playerSkinxx.secure()).orElse(playerSkin);
			}
		}
	}

	public CompletableFuture<Optional<PlayerSkin>> get(GameProfile gameProfile) {
		if (SharedConstants.DEBUG_DEFAULT_SKIN_OVERRIDE) {
			PlayerSkin playerSkin = DefaultPlayerSkin.get(gameProfile);
			return CompletableFuture.completedFuture(Optional.of(playerSkin));
		} else {
			Property property = this.services.sessionService().getPackedTextures(gameProfile);
			return this.skinCache.getUnchecked(new SkinManager.CacheKey(gameProfile.id(), property));
		}
	}

	CompletableFuture<PlayerSkin> registerTextures(UUID uUID, MinecraftProfileTextures minecraftProfileTextures) {
		MinecraftProfileTexture minecraftProfileTexture = minecraftProfileTextures.skin();
		CompletableFuture<ClientAsset.Texture> completableFuture;
		PlayerModelType playerModelType;
		if (minecraftProfileTexture != null) {
			completableFuture = this.skinTextures.getOrLoad(minecraftProfileTexture);
			playerModelType = PlayerModelType.byLegacyServicesName(minecraftProfileTexture.getMetadata("model"));
		} else {
			PlayerSkin playerSkin = DefaultPlayerSkin.get(uUID);
			completableFuture = CompletableFuture.completedFuture(playerSkin.body());
			playerModelType = playerSkin.model();
		}

		MinecraftProfileTexture minecraftProfileTexture2 = minecraftProfileTextures.cape();
		CompletableFuture<ClientAsset.Texture> completableFuture2 = minecraftProfileTexture2 != null
			? this.capeTextures.getOrLoad(minecraftProfileTexture2)
			: CompletableFuture.completedFuture(null);
		MinecraftProfileTexture minecraftProfileTexture3 = minecraftProfileTextures.elytra();
		CompletableFuture<ClientAsset.Texture> completableFuture3 = minecraftProfileTexture3 != null
			? this.elytraTextures.getOrLoad(minecraftProfileTexture3)
			: CompletableFuture.completedFuture(null);
		return CompletableFuture.allOf(completableFuture, completableFuture2, completableFuture3)
			.thenApply(
				void_ -> new PlayerSkin(
					completableFuture.join(),
					completableFuture2.join(),
					completableFuture3.join(),
					playerModelType,
					minecraftProfileTextures.signatureState() == SignatureState.SIGNED
				)
			);
	}

	@Environment(EnvType.CLIENT)
	record CacheKey(UUID profileId, @Nullable Property packedTextures) {
	}

	@Environment(EnvType.CLIENT)
	class TextureCache {
		private final Path root;
		private final Type type;
		private final Map<String, CompletableFuture<ClientAsset.Texture>> textures = new Object2ObjectOpenHashMap<>();

		TextureCache(final Path path, final Type type) {
			this.root = path;
			this.type = type;
		}

		public CompletableFuture<ClientAsset.Texture> getOrLoad(MinecraftProfileTexture minecraftProfileTexture) {
			String string = minecraftProfileTexture.getHash();
			CompletableFuture<ClientAsset.Texture> completableFuture = this.textures.get(string);
			if (completableFuture == null) {
				completableFuture = this.registerTexture(minecraftProfileTexture);
				this.textures.put(string, completableFuture);
			}

			return completableFuture;
		}

		private CompletableFuture<ClientAsset.Texture> registerTexture(MinecraftProfileTexture minecraftProfileTexture) {
			String string = Hashing.sha1().hashUnencodedChars(minecraftProfileTexture.getHash()).toString();
			Identifier identifier = this.getTextureLocation(string);
			Path path = this.root.resolve(string.length() > 2 ? string.substring(0, 2) : "xx").resolve(string);
			return SkinManager.this.skinTextureDownloader.downloadAndRegisterSkin(identifier, path, minecraftProfileTexture.getUrl(), this.type == Type.SKIN);
		}

		private Identifier getTextureLocation(String string) {
			String string2 = switch (this.type) {
				case SKIN -> "skins";
				case CAPE -> "capes";
				case ELYTRA -> "elytra";
			};
			return Identifier.withDefaultNamespace(string2 + "/" + string);
		}
	}
}
