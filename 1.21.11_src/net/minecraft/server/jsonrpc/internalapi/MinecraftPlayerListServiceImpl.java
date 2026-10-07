package net.minecraft.server.jsonrpc.internalapi;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.jsonrpc.JsonRpcLogger;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import org.jspecify.annotations.Nullable;

public class MinecraftPlayerListServiceImpl implements MinecraftPlayerListService {
	private final JsonRpcLogger jsonRpcLogger;
	private final DedicatedServer server;

	public MinecraftPlayerListServiceImpl(DedicatedServer dedicatedServer, JsonRpcLogger jsonRpcLogger) {
		this.jsonRpcLogger = jsonRpcLogger;
		this.server = dedicatedServer;
	}

	@Override
	public List<ServerPlayer> getPlayers() {
		return this.server.getPlayerList().getPlayers();
	}

	@Override
	public @Nullable ServerPlayer getPlayer(UUID uUID) {
		return this.server.getPlayerList().getPlayer(uUID);
	}

	@Override
	public Optional<NameAndId> fetchUserByName(String string) {
		return this.server.services().nameToIdCache().get(string);
	}

	@Override
	public Optional<NameAndId> fetchUserById(UUID uUID) {
		return Optional.ofNullable(this.server.services().sessionService().fetchProfile(uUID, true)).map(profileResult -> new NameAndId(profileResult.profile()));
	}

	@Override
	public Optional<NameAndId> getCachedUserById(UUID uUID) {
		return this.server.services().nameToIdCache().get(uUID);
	}

	@Override
	public Optional<ServerPlayer> getPlayer(Optional<UUID> optional, Optional<String> optional2) {
		if (optional.isPresent()) {
			return Optional.ofNullable(this.server.getPlayerList().getPlayer(optional.get()));
		} else {
			return optional2.isPresent() ? Optional.ofNullable(this.server.getPlayerList().getPlayerByName(optional2.get())) : Optional.empty();
		}
	}

	@Override
	public List<ServerPlayer> getPlayersWithAddress(String string) {
		return this.server.getPlayerList().getPlayersWithAddress(string);
	}

	@Override
	public void remove(ServerPlayer serverPlayer, ClientInfo clientInfo) {
		this.server.getPlayerList().remove(serverPlayer);
		this.jsonRpcLogger.log(clientInfo, "Remove player '{}'", serverPlayer.getPlainTextName());
	}

	@Override
	public @Nullable ServerPlayer getPlayerByName(String string) {
		return this.server.getPlayerList().getPlayerByName(string);
	}
}
