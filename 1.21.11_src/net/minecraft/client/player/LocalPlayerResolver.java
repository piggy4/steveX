package net.minecraft.client.player;

import com.mojang.authlib.GameProfile;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.server.players.ProfileResolver;

@Environment(EnvType.CLIENT)
public class LocalPlayerResolver implements ProfileResolver {
	private final Minecraft minecraft;
	private final ProfileResolver parentResolver;

	public LocalPlayerResolver(Minecraft minecraft, ProfileResolver profileResolver) {
		this.minecraft = minecraft;
		this.parentResolver = profileResolver;
	}

	@Override
	public Optional<GameProfile> fetchByName(String string) {
		ClientPacketListener clientPacketListener = this.minecraft.getConnection();
		if (clientPacketListener != null) {
			PlayerInfo playerInfo = clientPacketListener.getPlayerInfoIgnoreCase(string);
			if (playerInfo != null) {
				return Optional.of(playerInfo.getProfile());
			}
		}

		return this.parentResolver.fetchByName(string);
	}

	@Override
	public Optional<GameProfile> fetchById(UUID uUID) {
		ClientPacketListener clientPacketListener = this.minecraft.getConnection();
		if (clientPacketListener != null) {
			PlayerInfo playerInfo = clientPacketListener.getPlayerInfo(uUID);
			if (playerInfo != null) {
				return Optional.of(playerInfo.getProfile());
			}
		}

		return this.parentResolver.fetchById(uUID);
	}
}
