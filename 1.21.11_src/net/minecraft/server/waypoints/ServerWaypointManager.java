package net.minecraft.server.waypoints;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import com.google.common.collect.Sets.SetView;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.waypoints.WaypointManager;
import net.minecraft.world.waypoints.WaypointTransmitter;

public class ServerWaypointManager implements WaypointManager<WaypointTransmitter> {
	private final Set<WaypointTransmitter> waypoints = new HashSet<>();
	private final Set<ServerPlayer> players = new HashSet<>();
	private final Table<ServerPlayer, WaypointTransmitter, WaypointTransmitter.Connection> connections = HashBasedTable.create();

	public void trackWaypoint(WaypointTransmitter waypointTransmitter) {
		this.waypoints.add(waypointTransmitter);

		for (ServerPlayer serverPlayer : this.players) {
			this.createConnection(serverPlayer, waypointTransmitter);
		}
	}

	public void updateWaypoint(WaypointTransmitter waypointTransmitter) {
		if (this.waypoints.contains(waypointTransmitter)) {
			Map<ServerPlayer, WaypointTransmitter.Connection> map = Tables.transpose(this.connections).row(waypointTransmitter);
			SetView<ServerPlayer> setView = Sets.difference(this.players, map.keySet());

			for (Entry<ServerPlayer, WaypointTransmitter.Connection> entry : ImmutableSet.copyOf(map.entrySet())) {
				this.updateConnection(entry.getKey(), waypointTransmitter, entry.getValue());
			}

			for (ServerPlayer serverPlayer : setView) {
				this.createConnection(serverPlayer, waypointTransmitter);
			}
		}
	}

	public void untrackWaypoint(WaypointTransmitter waypointTransmitter) {
		this.connections.column(waypointTransmitter).forEach((serverPlayer, connection) -> connection.disconnect());
		Tables.transpose(this.connections).row(waypointTransmitter).clear();
		this.waypoints.remove(waypointTransmitter);
	}

	public void addPlayer(ServerPlayer serverPlayer) {
		this.players.add(serverPlayer);

		for (WaypointTransmitter waypointTransmitter : this.waypoints) {
			this.createConnection(serverPlayer, waypointTransmitter);
		}

		if (serverPlayer.isTransmittingWaypoint()) {
			this.trackWaypoint(serverPlayer);
		}
	}

	public void updatePlayer(ServerPlayer serverPlayer) {
		Map<WaypointTransmitter, WaypointTransmitter.Connection> map = this.connections.row(serverPlayer);
		SetView<WaypointTransmitter> setView = Sets.difference(this.waypoints, map.keySet());

		for (Entry<WaypointTransmitter, WaypointTransmitter.Connection> entry : ImmutableSet.copyOf(map.entrySet())) {
			this.updateConnection(serverPlayer, entry.getKey(), entry.getValue());
		}

		for (WaypointTransmitter waypointTransmitter : setView) {
			this.createConnection(serverPlayer, waypointTransmitter);
		}
	}

	public void removePlayer(ServerPlayer serverPlayer) {
		this.connections.row(serverPlayer).values().removeIf(connection -> {
			connection.disconnect();
			return true;
		});
		this.untrackWaypoint(serverPlayer);
		this.players.remove(serverPlayer);
	}

	public void breakAllConnections() {
		this.connections.values().forEach(WaypointTransmitter.Connection::disconnect);
		this.connections.clear();
	}

	public void remakeConnections(WaypointTransmitter waypointTransmitter) {
		for (ServerPlayer serverPlayer : this.players) {
			this.createConnection(serverPlayer, waypointTransmitter);
		}
	}

	public Set<WaypointTransmitter> transmitters() {
		return this.waypoints;
	}

	private static boolean isLocatorBarEnabledFor(ServerPlayer serverPlayer) {
		return serverPlayer.level().getGameRules().get(GameRules.LOCATOR_BAR);
	}

	private void createConnection(ServerPlayer serverPlayer, WaypointTransmitter waypointTransmitter) {
		if (serverPlayer != waypointTransmitter) {
			if (isLocatorBarEnabledFor(serverPlayer)) {
				waypointTransmitter.makeWaypointConnectionWith(serverPlayer).ifPresentOrElse(connection -> {
					this.connections.put(serverPlayer, waypointTransmitter, connection);
					connection.connect();
				}, () -> {
					WaypointTransmitter.Connection connection = this.connections.remove(serverPlayer, waypointTransmitter);
					if (connection != null) {
						connection.disconnect();
					}
				});
			}
		}
	}

	private void updateConnection(ServerPlayer serverPlayer, WaypointTransmitter waypointTransmitter, WaypointTransmitter.Connection connection) {
		if (serverPlayer != waypointTransmitter) {
			if (isLocatorBarEnabledFor(serverPlayer)) {
				if (!connection.isBroken()) {
					connection.update();
				} else {
					waypointTransmitter.makeWaypointConnectionWith(serverPlayer).ifPresentOrElse(connectionx -> {
						connectionx.connect();
						this.connections.put(serverPlayer, waypointTransmitter, connectionx);
					}, () -> {
						connection.disconnect();
						this.connections.remove(serverPlayer, waypointTransmitter);
					});
				}
			}
		}
	}
}
