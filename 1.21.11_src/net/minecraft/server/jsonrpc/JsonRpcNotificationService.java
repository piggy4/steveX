package net.minecraft.server.jsonrpc;

import net.minecraft.core.Holder;
import net.minecraft.server.jsonrpc.api.PlayerDto;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.jsonrpc.methods.BanlistService;
import net.minecraft.server.jsonrpc.methods.GameRulesService;
import net.minecraft.server.jsonrpc.methods.IpBanlistService;
import net.minecraft.server.jsonrpc.methods.OperatorService;
import net.minecraft.server.jsonrpc.methods.ServerStateService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.notifications.NotificationService;
import net.minecraft.server.players.IpBanListEntry;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.level.gamerules.GameRule;

public class JsonRpcNotificationService implements NotificationService {
	private final ManagementServer managementServer;
	private final MinecraftApi minecraftApi;

	public JsonRpcNotificationService(MinecraftApi minecraftApi, ManagementServer managementServer) {
		this.minecraftApi = minecraftApi;
		this.managementServer = managementServer;
	}

	@Override
	public void playerJoined(ServerPlayer serverPlayer) {
		this.broadcastNotification(OutgoingRpcMethods.PLAYER_JOINED, PlayerDto.from(serverPlayer));
	}

	@Override
	public void playerLeft(ServerPlayer serverPlayer) {
		this.broadcastNotification(OutgoingRpcMethods.PLAYER_LEFT, PlayerDto.from(serverPlayer));
	}

	@Override
	public void serverStarted() {
		this.broadcastNotification(OutgoingRpcMethods.SERVER_STARTED);
	}

	@Override
	public void serverShuttingDown() {
		this.broadcastNotification(OutgoingRpcMethods.SERVER_SHUTTING_DOWN);
	}

	@Override
	public void serverSaveStarted() {
		this.broadcastNotification(OutgoingRpcMethods.SERVER_SAVE_STARTED);
	}

	@Override
	public void serverSaveCompleted() {
		this.broadcastNotification(OutgoingRpcMethods.SERVER_SAVE_COMPLETED);
	}

	@Override
	public void serverActivityOccured() {
		this.broadcastNotification(OutgoingRpcMethods.SERVER_ACTIVITY_OCCURRED);
	}

	@Override
	public void playerOped(ServerOpListEntry serverOpListEntry) {
		this.broadcastNotification(OutgoingRpcMethods.PLAYER_OPED, OperatorService.OperatorDto.from(serverOpListEntry));
	}

	@Override
	public void playerDeoped(ServerOpListEntry serverOpListEntry) {
		this.broadcastNotification(OutgoingRpcMethods.PLAYER_DEOPED, OperatorService.OperatorDto.from(serverOpListEntry));
	}

	@Override
	public void playerAddedToAllowlist(NameAndId nameAndId) {
		this.broadcastNotification(OutgoingRpcMethods.PLAYER_ADDED_TO_ALLOWLIST, PlayerDto.from(nameAndId));
	}

	@Override
	public void playerRemovedFromAllowlist(NameAndId nameAndId) {
		this.broadcastNotification(OutgoingRpcMethods.PLAYER_REMOVED_FROM_ALLOWLIST, PlayerDto.from(nameAndId));
	}

	@Override
	public void ipBanned(IpBanListEntry ipBanListEntry) {
		this.broadcastNotification(OutgoingRpcMethods.IP_BANNED, IpBanlistService.IpBanDto.from(ipBanListEntry));
	}

	@Override
	public void ipUnbanned(String string) {
		this.broadcastNotification(OutgoingRpcMethods.IP_UNBANNED, string);
	}

	@Override
	public void playerBanned(UserBanListEntry userBanListEntry) {
		this.broadcastNotification(OutgoingRpcMethods.PLAYER_BANNED, BanlistService.UserBanDto.from(userBanListEntry));
	}

	@Override
	public void playerUnbanned(NameAndId nameAndId) {
		this.broadcastNotification(OutgoingRpcMethods.PLAYER_UNBANNED, PlayerDto.from(nameAndId));
	}

	@Override
	public <T> void onGameRuleChanged(GameRule<T> gameRule, T object) {
		this.broadcastNotification(OutgoingRpcMethods.GAMERULE_CHANGED, GameRulesService.getTypedRule(this.minecraftApi, gameRule, object));
	}

	@Override
	public void statusHeartbeat() {
		this.broadcastNotification(OutgoingRpcMethods.STATUS_HEARTBEAT, ServerStateService.status(this.minecraftApi));
	}

	private void broadcastNotification(Holder.Reference<? extends OutgoingRpcMethod<Void, ?>> reference) {
		this.managementServer.forEachConnection(connection -> connection.sendNotification(reference));
	}

	private <Params> void broadcastNotification(Holder.Reference<? extends OutgoingRpcMethod<Params, ?>> reference, Params object) {
		this.managementServer.forEachConnection(connection -> connection.sendNotification(reference, object));
	}
}
