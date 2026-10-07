package net.minecraft.util.debug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

public class ServerDebugSubscribers {
	private final MinecraftServer server;
	private final Map<DebugSubscription<?>, List<ServerPlayer>> enabledSubscriptions = new HashMap<>();

	public ServerDebugSubscribers(MinecraftServer minecraftServer) {
		this.server = minecraftServer;
	}

	private List<ServerPlayer> getSubscribersFor(DebugSubscription<?> debugSubscription) {
		return this.enabledSubscriptions.getOrDefault(debugSubscription, List.of());
	}

	public void tick() {
		this.enabledSubscriptions.values().forEach(List::clear);

		for (ServerPlayer serverPlayer : this.server.getPlayerList().getPlayers()) {
			for (DebugSubscription<?> debugSubscription : serverPlayer.debugSubscriptions()) {
				this.enabledSubscriptions.computeIfAbsent(debugSubscription, debugSubscriptionx -> new ArrayList<>()).add(serverPlayer);
			}
		}

		this.enabledSubscriptions.values().removeIf(List::isEmpty);
	}

	public void broadcastToAll(DebugSubscription<?> debugSubscription, Packet<?> packet) {
		for (ServerPlayer serverPlayer : this.getSubscribersFor(debugSubscription)) {
			serverPlayer.connection.send(packet);
		}
	}

	public Set<DebugSubscription<?>> enabledSubscriptions() {
		return Set.copyOf(this.enabledSubscriptions.keySet());
	}

	public boolean hasAnySubscriberFor(DebugSubscription<?> debugSubscription) {
		return !this.getSubscribersFor(debugSubscription).isEmpty();
	}

	public boolean hasRequiredPermissions(ServerPlayer serverPlayer) {
		NameAndId nameAndId = serverPlayer.nameAndId();
		return SharedConstants.IS_RUNNING_IN_IDE && this.server.isSingleplayerOwner(nameAndId) ? true : this.server.getPlayerList().isOp(nameAndId);
	}
}
