package net.minecraft.server.notifications;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.IpBanListEntry;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.level.gamerules.GameRule;

public class NotificationManager implements NotificationService {
	private final List<NotificationService> notificationServices = Lists.newArrayList();

	public void registerService(NotificationService notificationService) {
		this.notificationServices.add(notificationService);
	}

	@Override
	public void playerJoined(ServerPlayer serverPlayer) {
		this.notificationServices.forEach(notificationService -> notificationService.playerJoined(serverPlayer));
	}

	@Override
	public void playerLeft(ServerPlayer serverPlayer) {
		this.notificationServices.forEach(notificationService -> notificationService.playerLeft(serverPlayer));
	}

	@Override
	public void serverStarted() {
		this.notificationServices.forEach(NotificationService::serverStarted);
	}

	@Override
	public void serverShuttingDown() {
		this.notificationServices.forEach(NotificationService::serverShuttingDown);
	}

	@Override
	public void serverSaveStarted() {
		this.notificationServices.forEach(NotificationService::serverSaveStarted);
	}

	@Override
	public void serverSaveCompleted() {
		this.notificationServices.forEach(NotificationService::serverSaveCompleted);
	}

	@Override
	public void serverActivityOccured() {
		this.notificationServices.forEach(NotificationService::serverActivityOccured);
	}

	@Override
	public void playerOped(ServerOpListEntry serverOpListEntry) {
		this.notificationServices.forEach(notificationService -> notificationService.playerOped(serverOpListEntry));
	}

	@Override
	public void playerDeoped(ServerOpListEntry serverOpListEntry) {
		this.notificationServices.forEach(notificationService -> notificationService.playerDeoped(serverOpListEntry));
	}

	@Override
	public void playerAddedToAllowlist(NameAndId nameAndId) {
		this.notificationServices.forEach(notificationService -> notificationService.playerAddedToAllowlist(nameAndId));
	}

	@Override
	public void playerRemovedFromAllowlist(NameAndId nameAndId) {
		this.notificationServices.forEach(notificationService -> notificationService.playerRemovedFromAllowlist(nameAndId));
	}

	@Override
	public void ipBanned(IpBanListEntry ipBanListEntry) {
		this.notificationServices.forEach(notificationService -> notificationService.ipBanned(ipBanListEntry));
	}

	@Override
	public void ipUnbanned(String string) {
		this.notificationServices.forEach(notificationService -> notificationService.ipUnbanned(string));
	}

	@Override
	public void playerBanned(UserBanListEntry userBanListEntry) {
		this.notificationServices.forEach(notificationService -> notificationService.playerBanned(userBanListEntry));
	}

	@Override
	public void playerUnbanned(NameAndId nameAndId) {
		this.notificationServices.forEach(notificationService -> notificationService.playerUnbanned(nameAndId));
	}

	@Override
	public <T> void onGameRuleChanged(GameRule<T> gameRule, T object) {
		this.notificationServices.forEach(notificationService -> notificationService.onGameRuleChanged(gameRule, object));
	}

	@Override
	public void statusHeartbeat() {
		this.notificationServices.forEach(NotificationService::statusHeartbeat);
	}
}
