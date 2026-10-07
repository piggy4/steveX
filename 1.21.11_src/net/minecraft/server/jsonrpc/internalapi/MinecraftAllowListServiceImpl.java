package net.minecraft.server.jsonrpc.internalapi;

import java.util.Collection;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.jsonrpc.JsonRpcLogger;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserWhiteListEntry;

public class MinecraftAllowListServiceImpl implements MinecraftAllowListService {
	private final DedicatedServer server;
	private final JsonRpcLogger jsonrpcLogger;

	public MinecraftAllowListServiceImpl(DedicatedServer dedicatedServer, JsonRpcLogger jsonRpcLogger) {
		this.server = dedicatedServer;
		this.jsonrpcLogger = jsonRpcLogger;
	}

	@Override
	public Collection<UserWhiteListEntry> getEntries() {
		return this.server.getPlayerList().getWhiteList().getEntries();
	}

	@Override
	public boolean add(UserWhiteListEntry userWhiteListEntry, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Add player '{}' to allowlist", userWhiteListEntry.getUser());
		return this.server.getPlayerList().getWhiteList().add(userWhiteListEntry);
	}

	@Override
	public void clear(ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Clear allowlist");
		this.server.getPlayerList().getWhiteList().clear();
	}

	@Override
	public void remove(NameAndId nameAndId, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Remove player '{}' from allowlist", nameAndId);
		this.server.getPlayerList().getWhiteList().remove(nameAndId);
	}

	@Override
	public void kickUnlistedPlayers(ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Kick unlisted players");
		this.server.kickUnlistedPlayers();
	}
}
