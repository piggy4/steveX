package net.minecraft.world.item.component;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.players.ProfileResolver;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;

public abstract sealed class ResolvableProfile implements TooltipProvider permits ResolvableProfile.Static, ResolvableProfile.Dynamic {
	private static final Codec<ResolvableProfile> FULL_CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				Codec.mapEither(ExtraCodecs.STORED_GAME_PROFILE, ResolvableProfile.Partial.MAP_CODEC).forGetter(ResolvableProfile::unpack),
				PlayerSkin.Patch.MAP_CODEC.forGetter(ResolvableProfile::skinPatch)
			)
			.apply(instance, ResolvableProfile::create)
	);
	public static final Codec<ResolvableProfile> CODEC = Codec.withAlternative(FULL_CODEC, ExtraCodecs.PLAYER_NAME, ResolvableProfile::createUnresolved);
	public static final StreamCodec<ByteBuf, ResolvableProfile> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.either(ByteBufCodecs.GAME_PROFILE, ResolvableProfile.Partial.STREAM_CODEC),
		ResolvableProfile::unpack,
		PlayerSkin.Patch.STREAM_CODEC,
		ResolvableProfile::skinPatch,
		ResolvableProfile::create
	);
	protected final GameProfile partialProfile;
	protected final PlayerSkin.Patch skinPatch;

	private static ResolvableProfile create(Either<GameProfile, ResolvableProfile.Partial> either, PlayerSkin.Patch patch) {
		return either.map(
			gameProfile -> new ResolvableProfile.Static(Either.left(gameProfile), patch),
			partial -> partial.properties.isEmpty() && partial.id.isPresent() != partial.name.isPresent()
				? partial.name
					.<ResolvableProfile>map(string -> new ResolvableProfile.Dynamic(Either.left(string), patch))
					.orElseGet(() -> new ResolvableProfile.Dynamic(Either.right(partial.id.get()), patch))
				: new ResolvableProfile.Static(Either.right(partial), patch)
		);
	}

	public static ResolvableProfile createResolved(GameProfile gameProfile) {
		return new ResolvableProfile.Static(Either.left(gameProfile), PlayerSkin.Patch.EMPTY);
	}

	public static ResolvableProfile createUnresolved(String string) {
		return new ResolvableProfile.Dynamic(Either.left(string), PlayerSkin.Patch.EMPTY);
	}

	public static ResolvableProfile createUnresolved(UUID uUID) {
		return new ResolvableProfile.Dynamic(Either.right(uUID), PlayerSkin.Patch.EMPTY);
	}

	protected abstract Either<GameProfile, ResolvableProfile.Partial> unpack();

	protected ResolvableProfile(GameProfile gameProfile, PlayerSkin.Patch patch) {
		this.partialProfile = gameProfile;
		this.skinPatch = patch;
	}

	public abstract CompletableFuture<GameProfile> resolveProfile(ProfileResolver profileResolver);

	public GameProfile partialProfile() {
		return this.partialProfile;
	}

	public PlayerSkin.Patch skinPatch() {
		return this.skinPatch;
	}

	static GameProfile createPartialProfile(Optional<String> optional, Optional<UUID> optional2, PropertyMap propertyMap) {
		String string = optional.orElse("");
		UUID uUID = optional2.orElseGet(() -> optional.map(UUIDUtil::createOfflinePlayerUUID).orElse(Util.NIL_UUID));
		return new GameProfile(uUID, string, propertyMap);
	}

	public abstract Optional<String> name();

	public static final class Dynamic extends ResolvableProfile {
		private static final Component DYNAMIC_TOOLTIP = Component.translatable("component.profile.dynamic").withStyle(ChatFormatting.GRAY);
		private final Either<String, UUID> nameOrId;

		Dynamic(Either<String, UUID> either, PlayerSkin.Patch patch) {
			super(ResolvableProfile.createPartialProfile(either.left(), either.right(), PropertyMap.EMPTY), patch);
			this.nameOrId = either;
		}

		@Override
		public Optional<String> name() {
			return this.nameOrId.left();
		}

		@Override
		public boolean equals(Object object) {
			return this == object
				|| object instanceof ResolvableProfile.Dynamic dynamic && this.nameOrId.equals(dynamic.nameOrId) && this.skinPatch.equals(dynamic.skinPatch);
		}

		@Override
		public int hashCode() {
			int i = 31 + this.nameOrId.hashCode();
			return 31 * i + this.skinPatch.hashCode();
		}

		@Override
		protected Either<GameProfile, ResolvableProfile.Partial> unpack() {
			return Either.right(new ResolvableProfile.Partial(this.nameOrId.left(), this.nameOrId.right(), PropertyMap.EMPTY));
		}

		@Override
		public CompletableFuture<GameProfile> resolveProfile(ProfileResolver profileResolver) {
			return CompletableFuture.supplyAsync(() -> profileResolver.fetchByNameOrId(this.nameOrId).orElse(this.partialProfile), Util.nonCriticalIoPool());
		}

		@Override
		public void addToTooltip(Item.TooltipContext tooltipContext, Consumer<Component> consumer, TooltipFlag tooltipFlag, DataComponentGetter dataComponentGetter) {
			consumer.accept(DYNAMIC_TOOLTIP);
		}
	}

	protected record Partial(Optional<String> name, Optional<UUID> id, PropertyMap properties) {
		public static final ResolvableProfile.Partial EMPTY = new ResolvableProfile.Partial(Optional.empty(), Optional.empty(), PropertyMap.EMPTY);
		static final MapCodec<ResolvableProfile.Partial> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ExtraCodecs.PLAYER_NAME.optionalFieldOf("name").forGetter(ResolvableProfile.Partial::name),
					UUIDUtil.CODEC.optionalFieldOf("id").forGetter(ResolvableProfile.Partial::id),
					ExtraCodecs.PROPERTY_MAP.optionalFieldOf("properties", PropertyMap.EMPTY).forGetter(ResolvableProfile.Partial::properties)
				)
				.apply(instance, ResolvableProfile.Partial::new)
		);
		public static final StreamCodec<ByteBuf, ResolvableProfile.Partial> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.PLAYER_NAME.apply(ByteBufCodecs::optional),
			ResolvableProfile.Partial::name,
			UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional),
			ResolvableProfile.Partial::id,
			ByteBufCodecs.GAME_PROFILE_PROPERTIES,
			ResolvableProfile.Partial::properties,
			ResolvableProfile.Partial::new
		);

		private GameProfile createProfile() {
			return ResolvableProfile.createPartialProfile(this.name, this.id, this.properties);
		}
	}

	public static final class Static extends ResolvableProfile {
		public static final ResolvableProfile.Static EMPTY = new ResolvableProfile.Static(Either.right(ResolvableProfile.Partial.EMPTY), PlayerSkin.Patch.EMPTY);
		private final Either<GameProfile, ResolvableProfile.Partial> contents;

		Static(Either<GameProfile, ResolvableProfile.Partial> either, PlayerSkin.Patch patch) {
			super(either.map(gameProfile -> (GameProfile)gameProfile, ResolvableProfile.Partial::createProfile), patch);
			this.contents = either;
		}

		@Override
		public CompletableFuture<GameProfile> resolveProfile(ProfileResolver profileResolver) {
			return CompletableFuture.completedFuture(this.partialProfile);
		}

		@Override
		protected Either<GameProfile, ResolvableProfile.Partial> unpack() {
			return this.contents;
		}

		@Override
		public Optional<String> name() {
			return this.contents.map(gameProfile -> Optional.of(gameProfile.name()), partial -> partial.name);
		}

		@Override
		public boolean equals(Object object) {
			return this == object
				|| object instanceof ResolvableProfile.Static static_ && this.contents.equals(static_.contents) && this.skinPatch.equals(static_.skinPatch);
		}

		@Override
		public int hashCode() {
			int i = 31 + this.contents.hashCode();
			return 31 * i + this.skinPatch.hashCode();
		}

		@Override
		public void addToTooltip(Item.TooltipContext tooltipContext, Consumer<Component> consumer, TooltipFlag tooltipFlag, DataComponentGetter dataComponentGetter) {
		}
	}
}
