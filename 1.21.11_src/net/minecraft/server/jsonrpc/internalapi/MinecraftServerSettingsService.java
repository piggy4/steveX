package net.minecraft.server.jsonrpc.internalapi;

import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;

public interface MinecraftServerSettingsService {
	boolean isAutoSave();

	boolean setAutoSave(boolean bl, ClientInfo clientInfo);

	Difficulty getDifficulty();

	Difficulty setDifficulty(Difficulty difficulty, ClientInfo clientInfo);

	boolean isEnforceWhitelist();

	boolean setEnforceWhitelist(boolean bl, ClientInfo clientInfo);

	boolean isUsingWhitelist();

	boolean setUsingWhitelist(boolean bl, ClientInfo clientInfo);

	int getMaxPlayers();

	int setMaxPlayers(int i, ClientInfo clientInfo);

	int getPauseWhenEmptySeconds();

	int setPauseWhenEmptySeconds(int i, ClientInfo clientInfo);

	int getPlayerIdleTimeout();

	int setPlayerIdleTimeout(int i, ClientInfo clientInfo);

	boolean allowFlight();

	boolean setAllowFlight(boolean bl, ClientInfo clientInfo);

	int getSpawnProtectionRadius();

	int setSpawnProtectionRadius(int i, ClientInfo clientInfo);

	String getMotd();

	String setMotd(String string, ClientInfo clientInfo);

	boolean forceGameMode();

	boolean setForceGameMode(boolean bl, ClientInfo clientInfo);

	GameType getGameMode();

	GameType setGameMode(GameType gameType, ClientInfo clientInfo);

	int getViewDistance();

	int setViewDistance(int i, ClientInfo clientInfo);

	int getSimulationDistance();

	int setSimulationDistance(int i, ClientInfo clientInfo);

	boolean acceptsTransfers();

	boolean setAcceptsTransfers(boolean bl, ClientInfo clientInfo);

	int getStatusHeartbeatInterval();

	int setStatusHeartbeatInterval(int i, ClientInfo clientInfo);

	LevelBasedPermissionSet getOperatorUserPermissions();

	LevelBasedPermissionSet setOperatorUserPermissions(LevelBasedPermissionSet levelBasedPermissionSet, ClientInfo clientInfo);

	boolean hidesOnlinePlayers();

	boolean setHidesOnlinePlayers(boolean bl, ClientInfo clientInfo);

	boolean repliesToStatus();

	boolean setRepliesToStatus(boolean bl, ClientInfo clientInfo);

	int getEntityBroadcastRangePercentage();

	int setEntityBroadcastRangePercentage(int i, ClientInfo clientInfo);
}
