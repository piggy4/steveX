package net.minecraft.server.jsonrpc.internalapi;

import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.jsonrpc.JsonRpcLogger;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;

public class MinecraftServerSettingsServiceImpl implements MinecraftServerSettingsService {
	private final DedicatedServer server;
	private final JsonRpcLogger jsonrpcLogger;

	public MinecraftServerSettingsServiceImpl(DedicatedServer dedicatedServer, JsonRpcLogger jsonRpcLogger) {
		this.server = dedicatedServer;
		this.jsonrpcLogger = jsonRpcLogger;
	}

	@Override
	public boolean isAutoSave() {
		return this.server.isAutoSave();
	}

	@Override
	public boolean setAutoSave(boolean bl, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Update autosave from {} to {}", this.isAutoSave(), bl);
		this.server.setAutoSave(bl);
		return this.isAutoSave();
	}

	@Override
	public Difficulty getDifficulty() {
		return this.server.getWorldData().getDifficulty();
	}

	@Override
	public Difficulty setDifficulty(Difficulty difficulty, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Update difficulty from '{}' to '{}'", this.getDifficulty(), difficulty);
		this.server.setDifficulty(difficulty);
		return this.getDifficulty();
	}

	@Override
	public boolean isEnforceWhitelist() {
		return this.server.isEnforceWhitelist();
	}

	@Override
	public boolean setEnforceWhitelist(boolean bl, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Update enforce allowlist from {} to {}", this.isEnforceWhitelist(), bl);
		this.server.setEnforceWhitelist(bl);
		this.server.kickUnlistedPlayers();
		return this.isEnforceWhitelist();
	}

	@Override
	public boolean isUsingWhitelist() {
		return this.server.isUsingWhitelist();
	}

	@Override
	public boolean setUsingWhitelist(boolean bl, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Update using allowlist from {} to {}", this.isUsingWhitelist(), bl);
		this.server.setUsingWhitelist(bl);
		this.server.kickUnlistedPlayers();
		return this.isUsingWhitelist();
	}

	@Override
	public int getMaxPlayers() {
		return this.server.getMaxPlayers();
	}

	@Override
	public int setMaxPlayers(int i, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Update max players from {} to {}", this.getMaxPlayers(), i);
		this.server.setMaxPlayers(i);
		return this.getMaxPlayers();
	}

	@Override
	public int getPauseWhenEmptySeconds() {
		return this.server.pauseWhenEmptySeconds();
	}

	@Override
	public int setPauseWhenEmptySeconds(int i, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Update pause when empty from {} seconds to {} seconds", this.getPauseWhenEmptySeconds(), i);
		this.server.setPauseWhenEmptySeconds(i);
		return this.getPauseWhenEmptySeconds();
	}

	@Override
	public int getPlayerIdleTimeout() {
		return this.server.playerIdleTimeout();
	}

	@Override
	public int setPlayerIdleTimeout(int i, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Update player idle timeout from {} minutes to {} minutes", this.getPlayerIdleTimeout(), i);
		this.server.setPlayerIdleTimeout(i);
		return this.getPlayerIdleTimeout();
	}

	@Override
	public boolean allowFlight() {
		return this.server.allowFlight();
	}

	@Override
	public boolean setAllowFlight(boolean bl, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Update allow flight from {} to {}", this.allowFlight(), bl);
		this.server.setAllowFlight(bl);
		return this.allowFlight();
	}

	@Override
	public int getSpawnProtectionRadius() {
		return this.server.spawnProtectionRadius();
	}

	@Override
	public int setSpawnProtectionRadius(int i, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Update spawn protection radius from {} to {}", this.getSpawnProtectionRadius(), i);
		this.server.setSpawnProtectionRadius(i);
		return this.getSpawnProtectionRadius();
	}

	@Override
	public String getMotd() {
		return this.server.getMotd();
	}

	@Override
	public String setMotd(String string, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Update MOTD from '{}' to '{}'", this.getMotd(), string);
		this.server.setMotd(string);
		return this.getMotd();
	}

	@Override
	public boolean forceGameMode() {
		return this.server.forceGameMode();
	}

	@Override
	public boolean setForceGameMode(boolean bl, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Update force game mode from {} to {}", this.forceGameMode(), bl);
		this.server.setForceGameMode(bl);
		return this.forceGameMode();
	}

	@Override
	public GameType getGameMode() {
		return this.server.gameMode();
	}

	@Override
	public GameType setGameMode(GameType gameType, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Update game mode from '{}' to '{}'", this.getGameMode(), gameType);
		this.server.setGameMode(gameType);
		return this.getGameMode();
	}

	@Override
	public int getViewDistance() {
		return this.server.viewDistance();
	}

	@Override
	public int setViewDistance(int i, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Update view distance from {} to {}", this.getViewDistance(), i);
		this.server.setViewDistance(i);
		return this.getViewDistance();
	}

	@Override
	public int getSimulationDistance() {
		return this.server.simulationDistance();
	}

	@Override
	public int setSimulationDistance(int i, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Update simulation distance from {} to {}", this.getSimulationDistance(), i);
		this.server.setSimulationDistance(i);
		return this.getSimulationDistance();
	}

	@Override
	public boolean acceptsTransfers() {
		return this.server.acceptsTransfers();
	}

	@Override
	public boolean setAcceptsTransfers(boolean bl, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Update accepts transfers from {} to {}", this.acceptsTransfers(), bl);
		this.server.setAcceptsTransfers(bl);
		return this.acceptsTransfers();
	}

	@Override
	public int getStatusHeartbeatInterval() {
		return this.server.statusHeartbeatInterval();
	}

	@Override
	public int setStatusHeartbeatInterval(int i, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Update status heartbeat interval from {} to {}", this.getStatusHeartbeatInterval(), i);
		this.server.setStatusHeartbeatInterval(i);
		return this.getStatusHeartbeatInterval();
	}

	@Override
	public LevelBasedPermissionSet getOperatorUserPermissions() {
		return this.server.operatorUserPermissions();
	}

	@Override
	public LevelBasedPermissionSet setOperatorUserPermissions(LevelBasedPermissionSet levelBasedPermissionSet, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Update operator user permission level from {} to {}", this.getOperatorUserPermissions(), levelBasedPermissionSet.level());
		this.server.setOperatorUserPermissions(levelBasedPermissionSet);
		return this.getOperatorUserPermissions();
	}

	@Override
	public boolean hidesOnlinePlayers() {
		return this.server.hidesOnlinePlayers();
	}

	@Override
	public boolean setHidesOnlinePlayers(boolean bl, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Update hides online players from {} to {}", this.hidesOnlinePlayers(), bl);
		this.server.setHidesOnlinePlayers(bl);
		return this.hidesOnlinePlayers();
	}

	@Override
	public boolean repliesToStatus() {
		return this.server.repliesToStatus();
	}

	@Override
	public boolean setRepliesToStatus(boolean bl, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Update replies to status from {} to {}", this.repliesToStatus(), bl);
		this.server.setRepliesToStatus(bl);
		return this.repliesToStatus();
	}

	@Override
	public int getEntityBroadcastRangePercentage() {
		return this.server.entityBroadcastRangePercentage();
	}

	@Override
	public int setEntityBroadcastRangePercentage(int i, ClientInfo clientInfo) {
		this.jsonrpcLogger.log(clientInfo, "Update entity broadcast range percentage from {}% to {}%", this.getEntityBroadcastRangePercentage(), i);
		this.server.setEntityBroadcastRangePercentage(i);
		return this.getEntityBroadcastRangePercentage();
	}
}
