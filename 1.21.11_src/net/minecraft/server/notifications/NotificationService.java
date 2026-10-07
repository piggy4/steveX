package net.minecraft.server.notifications;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.IpBanListEntry;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.level.gamerules.GameRule;

public interface NotificationService {
	void playerJoined(ServerPlayer serverPlayer);

	void playerLeft(ServerPlayer serverPlayer);

	void serverStarted();

	void serverShuttingDown();

	void serverSaveStarted();

	void serverSaveCompleted();

	void serverActivityOccured();

	void playerOped(ServerOpListEntry serverOpListEntry);

	void playerDeoped(ServerOpListEntry serverOpListEntry);

	void playerAddedToAllowlist(NameAndId nameAndId);

	void playerRemovedFromAllowlist(NameAndId nameAndId);

	void ipBanned(IpBanListEntry ipBanListEntry);

	void ipUnbanned(String string);

	void playerBanned(UserBanListEntry userBanListEntry);

	void playerUnbanned(NameAndId nameAndId);

	<T> void onGameRuleChanged(GameRule<T> gameRule, T object);

	void statusHeartbeat();
}
