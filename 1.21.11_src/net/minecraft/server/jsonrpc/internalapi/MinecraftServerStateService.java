package net.minecraft.server.jsonrpc.internalapi;

import java.util.Collection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.level.ServerPlayer;

public interface MinecraftServerStateService {
	boolean isReady();

	boolean saveEverything(boolean bl, boolean bl2, boolean bl3, ClientInfo clientInfo);

	void halt(boolean bl, ClientInfo clientInfo);

	void sendSystemMessage(Component component, ClientInfo clientInfo);

	void sendSystemMessage(Component component, boolean bl, Collection<ServerPlayer> collection, ClientInfo clientInfo);

	void broadcastSystemMessage(Component component, boolean bl, ClientInfo clientInfo);
}
