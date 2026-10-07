package net.minecraft.server.jsonrpc.internalapi;

import java.util.Collection;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.jsonrpc.JsonRpcLogger;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class MinecraftServerStateServiceImpl implements MinecraftServerStateService {
	private final DedicatedServer server;
	private final JsonRpcLogger jsonrpcLogger;

	public MinecraftServerStateServiceImpl(DedicatedServer dedicatedServer, JsonRpcLogger jsonRpcLogger) {
		this.server = dedicatedServer;
		this.jsonrpcLogger = jsonRpcLogger;
	}

	@Override
	public boolean isReady() {
		return this.server.isReady();
	}

	@Override
	public boolean saveEverything(boolean bl, boolean bl2, boolean bl3, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Save everything. SuppressLogs: {}, flush: {}, force: {}", bl, bl2, bl3);
		return this.server.saveEverything(bl, bl2, bl3);
	}

	@Override
	public void halt(boolean bl, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Halt server. WaitForShutdown: {}", bl);
		this.server.halt(bl);
	}

	@Override
	public void sendSystemMessage(Component component, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Send system message: '{}'", component.getString());
		this.server.sendSystemMessage(component);
	}

	@Override
	public void sendSystemMessage(Component component, boolean bl, Collection<ServerPlayer> collection, ClientInfo clientInfo) {
		List<String> list = collection.stream().map(Player::getPlainTextName).toList();
		this.jsonrpcLogger.log(clientInfo, "Send system message to '{}' players (overlay: {}): '{}'", list.size(), bl, component.getString());

		for (ServerPlayer serverPlayer : collection) {
			if (bl) {
				serverPlayer.sendSystemMessage(component, true);
			} else {
				serverPlayer.sendSystemMessage(component);
			}
		}
	}

	@Override
	public void broadcastSystemMessage(Component component, boolean bl, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Broadcast system message (overlay: {}): '{}'", bl, component.getString());

		for (ServerPlayer serverPlayer : this.server.getPlayerList().getPlayers()) {
			if (bl) {
				serverPlayer.sendSystemMessage(component, true);
			} else {
				serverPlayer.sendSystemMessage(component);
			}
		}
	}
}
