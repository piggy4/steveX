package net.minecraft.server.notifications;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.IpBanListEntry;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.level.gamerules.GameRule;

public class EmptyNotificationService implements NotificationService {
	@Override
	public void playerJoined(ServerPlayer serverPlayer) {
	}

	@Override
	public void playerLeft(ServerPlayer serverPlayer) {
	}

	@Override
	public void serverStarted() {
	}

	@Override
	public void serverShuttingDown() {
	}

	@Override
	public void serverSaveStarted() {
	}

	@Override
	public void serverSaveCompleted() {
	}

	@Override
	public void serverActivityOccured() {
	}

	@Override
	public void playerOped(ServerOpListEntry serverOpListEntry) {
	}

	@Override
	public void playerDeoped(ServerOpListEntry serverOpListEntry) {
	}

	@Override
	public void playerAddedToAllowlist(NameAndId nameAndId) {
	}

	@Override
	public void playerRemovedFromAllowlist(NameAndId nameAndId) {
	}

	@Override
	public void ipBanned(IpBanListEntry ipBanListEntry) {
	}

	@Override
	public void ipUnbanned(String string) {
	}

	@Override
	public void playerBanned(UserBanListEntry userBanListEntry) {
	}

	@Override
	public void playerUnbanned(NameAndId nameAndId) {
	}

	@Override
	public <T> void onGameRuleChanged(GameRule<T> gameRule, T object) {
	}

	@Override
	public void statusHeartbeat() {
	}
}
