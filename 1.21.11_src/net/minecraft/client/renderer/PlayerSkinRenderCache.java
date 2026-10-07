package net.minecraft.client.renderer;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.server.players.ProfileResolver;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class PlayerSkinRenderCache {
	public static final RenderType DEFAULT_PLAYER_SKIN_RENDER_TYPE = playerSkinRenderType(DefaultPlayerSkin.getDefaultSkin());
	public static final Duration CACHE_DURATION = Duration.ofMinutes(5L);
	private final LoadingCache<ResolvableProfile, CompletableFuture<Optional<PlayerSkinRenderCache.RenderInfo>>> renderInfoCache = CacheBuilder.newBuilder()
		.expireAfterAccess(CACHE_DURATION)
		.build(
			new CacheLoader<ResolvableProfile, CompletableFuture<Optional<PlayerSkinRenderCache.RenderInfo>>>() {
				public CompletableFuture<Optional<PlayerSkinRenderCache.RenderInfo>> load(ResolvableProfile resolvableProfile) {
					return resolvableProfile.resolveProfile(PlayerSkinRenderCache.this.profileResolver)
						.thenCompose(
							gameProfile -> PlayerSkinRenderCache.this.skinManager
								.get(gameProfile)
								.thenApply(optional -> optional.map(playerSkin -> PlayerSkinRenderCache.this.new RenderInfo(gameProfile, playerSkin, resolvableProfile.skinPatch())))
						);
				}
			}
		);
	private final LoadingCache<ResolvableProfile, PlayerSkinRenderCache.RenderInfo> defaultSkinCache = CacheBuilder.newBuilder()
		.expireAfterAccess(CACHE_DURATION)
		.build(new CacheLoader<ResolvableProfile, PlayerSkinRenderCache.RenderInfo>() {
			public PlayerSkinRenderCache.RenderInfo load(ResolvableProfile resolvableProfile) {
				GameProfile gameProfile = resolvableProfile.partialProfile();
				return PlayerSkinRenderCache.this.new RenderInfo(gameProfile, DefaultPlayerSkin.get(gameProfile), resolvableProfile.skinPatch());
			}
		});
	final TextureManager textureManager;
	final SkinManager skinManager;
	final ProfileResolver profileResolver;

	public PlayerSkinRenderCache(TextureManager textureManager, SkinManager skinManager, ProfileResolver profileResolver) {
		this.textureManager = textureManager;
		this.skinManager = skinManager;
		this.profileResolver = profileResolver;
	}

	public PlayerSkinRenderCache.RenderInfo getOrDefault(ResolvableProfile resolvableProfile) {
		PlayerSkinRenderCache.RenderInfo renderInfo = this.lookup(resolvableProfile).getNow(Optional.empty()).orElse(null);
		return renderInfo != null ? renderInfo : this.defaultSkinCache.getUnchecked(resolvableProfile);
	}

	public Supplier<PlayerSkinRenderCache.RenderInfo> createLookup(ResolvableProfile resolvableProfile) {
		PlayerSkinRenderCache.RenderInfo renderInfo = this.defaultSkinCache.getUnchecked(resolvableProfile);
		CompletableFuture<Optional<PlayerSkinRenderCache.RenderInfo>> completableFuture = this.renderInfoCache.getUnchecked(resolvableProfile);
		Optional<PlayerSkinRenderCache.RenderInfo> optional = completableFuture.getNow(null);
		if (optional != null) {
			PlayerSkinRenderCache.RenderInfo renderInfo2 = optional.orElse(renderInfo);
			return () -> renderInfo2;
		} else {
			return () -> completableFuture.getNow(Optional.empty()).orElse(renderInfo);
		}
	}

	public CompletableFuture<Optional<PlayerSkinRenderCache.RenderInfo>> lookup(ResolvableProfile resolvableProfile) {
		return this.renderInfoCache.getUnchecked(resolvableProfile);
	}

	static RenderType playerSkinRenderType(PlayerSkin playerSkin) {
		return SkullBlockRenderer.getPlayerSkinRenderType(playerSkin.body().texturePath());
	}

	@Environment(EnvType.CLIENT)
	public final class RenderInfo {
		private final GameProfile gameProfile;
		private final PlayerSkin playerSkin;
		private @Nullable RenderType itemRenderType;
		private @Nullable GpuTextureView textureView;
		private @Nullable GlyphRenderTypes glyphRenderTypes;

		public RenderInfo(final GameProfile gameProfile, final PlayerSkin playerSkin, final PlayerSkin.Patch patch) {
			this.gameProfile = gameProfile;
			this.playerSkin = playerSkin.with(patch);
		}

		public GameProfile gameProfile() {
			return this.gameProfile;
		}

		public PlayerSkin playerSkin() {
			return this.playerSkin;
		}

		public RenderType renderType() {
			if (this.itemRenderType == null) {
				this.itemRenderType = PlayerSkinRenderCache.playerSkinRenderType(this.playerSkin);
			}

			return this.itemRenderType;
		}

		public GpuTextureView textureView() {
			if (this.textureView == null) {
				this.textureView = PlayerSkinRenderCache.this.textureManager.getTexture(this.playerSkin.body().texturePath()).getTextureView();
			}

			return this.textureView;
		}

		public GlyphRenderTypes glyphRenderTypes() {
			if (this.glyphRenderTypes == null) {
				this.glyphRenderTypes = GlyphRenderTypes.createForColorTexture(this.playerSkin.body().texturePath());
			}

			return this.glyphRenderTypes;
		}

		@Override
		public boolean equals(Object object) {
			return this == object
				|| object instanceof PlayerSkinRenderCache.RenderInfo renderInfo
					&& this.gameProfile.equals(renderInfo.gameProfile)
					&& this.playerSkin.equals(renderInfo.playerSkin);
		}

		@Override
		public int hashCode() {
			int i = 1;
			i = 31 * i + this.gameProfile.hashCode();
			return 31 * i + this.playerSkin.hashCode();
		}
	}
}
