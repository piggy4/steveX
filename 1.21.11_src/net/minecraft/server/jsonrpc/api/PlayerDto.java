package net.minecraft.server.jsonrpc.api;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

public record PlayerDto(Optional<UUID> id, Optional<String> name) {
	public static final MapCodec<PlayerDto> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				UUIDUtil.STRING_CODEC.optionalFieldOf("id").forGetter(PlayerDto::id), Codec.STRING.optionalFieldOf("name").forGetter(PlayerDto::name)
			)
			.apply(instance, PlayerDto::new)
	);

	public static PlayerDto from(GameProfile gameProfile) {
		return new PlayerDto(Optional.of(gameProfile.id()), Optional.of(gameProfile.name()));
	}

	public static PlayerDto from(NameAndId nameAndId) {
		return new PlayerDto(Optional.of(nameAndId.id()), Optional.of(nameAndId.name()));
	}

	public static PlayerDto from(ServerPlayer serverPlayer) {
		GameProfile gameProfile = serverPlayer.getGameProfile();
		return from(gameProfile);
	}
}
