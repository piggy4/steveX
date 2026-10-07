package net.minecraft.server.jsonrpc.internalapi;

import java.util.Collection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.jsonrpc.JsonRpcLogger;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.players.IpBanListEntry;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanListEntry;

public class MinecraftBanListServiceImpl implements MinecraftBanListService {
	private final MinecraftServer server;
	private final JsonRpcLogger jsonrpcLogger;

	public MinecraftBanListServiceImpl(MinecraftServer minecraftServer, JsonRpcLogger jsonRpcLogger) {
		this.server = minecraftServer;
		this.jsonrpcLogger = jsonRpcLogger;
	}

	@Override
	public void addUserBan(UserBanListEntry userBanListEntry, ClientInfo clientInfo) {
		this.jsonrpcLogger
			.log(clientInfo, "Add player '{}' to banlist. Reason: '{}'", userBanListEntry.getDisplayName(), userBanListEntry.getReasonMessage().getString());
		this.server.getPlayerList().getBans().add(userBanListEntry);
	}

	@Override
	public void removeUserBan(NameAndId nameAndId, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Remove player '{}' from banlist", nameAndId);
		this.server.getPlayerList().getBans().remove(nameAndId);
	}

	@Override
	public void clearUserBans(ClientInfo clientInfo) {
		this.server.getPlayerList().getBans().clear();
	}

	@Override
	public Collection<UserBanListEntry> getUserBanEntries() {
		return this.server.getPlayerList().getBans().getEntries();
	}

	@Override
	public Collection<IpBanListEntry> getIpBanEntries() {
		return this.server.getPlayerList().getIpBans().getEntries();
	}

	@Override
	public void addIpBan(IpBanListEntry ipBanListEntry, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Add ip '{}' to ban list", ipBanListEntry.getUser());
		this.server.getPlayerList().getIpBans().add(ipBanListEntry);
	}

	@Override
	public void clearIpBans(ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Clear ip ban list");
		this.server.getPlayerList().getIpBans().clear();
	}

	@Override
	public void removeIpBan(String string, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Remove ip '{}' from ban list", string);
		this.server.getPlayerList().getIpBans().remove(string);
	}
}
