package net.minecraft.server.players;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.datafixers.util.Either;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.util.StringUtil;

public interface ProfileResolver {
	Optional<GameProfile> fetchByName(String string);

	Optional<GameProfile> fetchById(UUID uUID);

	default Optional<GameProfile> fetchByNameOrId(Either<String, UUID> either) {
		return either.map(this::fetchByName, this::fetchById);
	}

	class Cached implements ProfileResolver {
		private final LoadingCache<String, Optional<GameProfile>> profileCacheByName;
		final LoadingCache<UUID, Optional<GameProfile>> profileCacheById;

		public Cached(MinecraftSessionService minecraftSessionService, UserNameToIdResolver userNameToIdResolver) {
			this.profileCacheById = CacheBuilder.newBuilder()
				.expireAfterAccess(Duration.ofMinutes(10L))
				.maximumSize(256L)
				.build(new CacheLoader<UUID, Optional<GameProfile>>() {
					public Optional<GameProfile> load(UUID uUID) {
						ProfileResult profileResult = minecraftSessionService.fetchProfile(uUID, true);
						return Optional.ofNullable(profileResult).map(ProfileResult::profile);
					}
				});
			this.profileCacheByName = CacheBuilder.newBuilder()
				.expireAfterAccess(Duration.ofMinutes(10L))
				.maximumSize(256L)
				.build(new CacheLoader<String, Optional<GameProfile>>() {
					public Optional<GameProfile> load(String string) {
						return userNameToIdResolver.get(string).flatMap(nameAndId -> Cached.this.profileCacheById.getUnchecked(nameAndId.id()));
					}
				});
		}

		@Override
		public Optional<GameProfile> fetchByName(String string) {
			return StringUtil.isValidPlayerName(string) ? this.profileCacheByName.getUnchecked(string) : Optional.empty();
		}

		@Override
		public Optional<GameProfile> fetchById(UUID uUID) {
			return this.profileCacheById.getUnchecked(uUID);
		}
	}
}
