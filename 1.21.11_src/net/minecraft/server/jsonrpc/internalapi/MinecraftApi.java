package net.minecraft.server.jsonrpc.internalapi;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.jsonrpc.JsonRpcLogger;
import net.minecraft.server.notifications.NotificationManager;

public class MinecraftApi {
	private final NotificationManager notificationManager;
	private final MinecraftAllowListService allowListService;
	private final MinecraftBanListService banListService;
	private final MinecraftPlayerListService minecraftPlayerListService;
	private final MinecraftGameRuleService gameRuleService;
	private final MinecraftOperatorListService minecraftOperatorListService;
	private final MinecraftServerSettingsService minecraftServerSettingsService;
	private final MinecraftServerStateService minecraftServerStateService;
	private final MinecraftExecutorService executorService;

	public MinecraftApi(
		NotificationManager notificationManager,
		MinecraftAllowListService minecraftAllowListService,
		MinecraftBanListService minecraftBanListService,
		MinecraftPlayerListService minecraftPlayerListService,
		MinecraftGameRuleService minecraftGameRuleService,
		MinecraftOperatorListService minecraftOperatorListService,
		MinecraftServerSettingsService minecraftServerSettingsService,
		MinecraftServerStateService minecraftServerStateService,
		MinecraftExecutorService minecraftExecutorService
	) {
		this.notificationManager = notificationManager;
		this.allowListService = minecraftAllowListService;
		this.banListService = minecraftBanListService;
		this.minecraftPlayerListService = minecraftPlayerListService;
		this.gameRuleService = minecraftGameRuleService;
		this.minecraftOperatorListService = minecraftOperatorListService;
		this.minecraftServerSettingsService = minecraftServerSettingsService;
		this.minecraftServerStateService = minecraftServerStateService;
		this.executorService = minecraftExecutorService;
	}

	public <V> CompletableFuture<V> submit(Supplier<V> supplier) {
		return this.executorService.submit(supplier);
	}

	public CompletableFuture<Void> submit(Runnable runnable) {
		return this.executorService.submit(runnable);
	}

	public MinecraftAllowListService allowListService() {
		return this.allowListService;
	}

	public MinecraftBanListService banListService() {
		return this.banListService;
	}

	public MinecraftPlayerListService playerListService() {
		return this.minecraftPlayerListService;
	}

	public MinecraftGameRuleService gameRuleService() {
		return this.gameRuleService;
	}

	public MinecraftOperatorListService operatorListService() {
		return this.minecraftOperatorListService;
	}

	public MinecraftServerSettingsService serverSettingsService() {
		return this.minecraftServerSettingsService;
	}

	public MinecraftServerStateService serverStateService() {
		return this.minecraftServerStateService;
	}

	public NotificationManager notificationManager() {
		return this.notificationManager;
	}

	public static MinecraftApi of(DedicatedServer dedicatedServer) {
		JsonRpcLogger jsonRpcLogger = new JsonRpcLogger();
		MinecraftAllowListServiceImpl minecraftAllowListServiceImpl = new MinecraftAllowListServiceImpl(dedicatedServer, jsonRpcLogger);
		MinecraftBanListServiceImpl minecraftBanListServiceImpl = new MinecraftBanListServiceImpl(dedicatedServer, jsonRpcLogger);
		MinecraftPlayerListServiceImpl minecraftPlayerListServiceImpl = new MinecraftPlayerListServiceImpl(dedicatedServer, jsonRpcLogger);
		MinecraftGameRuleServiceImpl minecraftGameRuleServiceImpl = new MinecraftGameRuleServiceImpl(dedicatedServer, jsonRpcLogger);
		MinecraftOperatorListServiceImpl minecraftOperatorListServiceImpl = new MinecraftOperatorListServiceImpl(dedicatedServer, jsonRpcLogger);
		MinecraftServerSettingsServiceImpl minecraftServerSettingsServiceImpl = new MinecraftServerSettingsServiceImpl(dedicatedServer, jsonRpcLogger);
		MinecraftServerStateServiceImpl minecraftServerStateServiceImpl = new MinecraftServerStateServiceImpl(dedicatedServer, jsonRpcLogger);
		MinecraftExecutorService minecraftExecutorService = new MinecraftExecutorServiceImpl(dedicatedServer);
		return new MinecraftApi(
			dedicatedServer.notificationManager(),
			minecraftAllowListServiceImpl,
			minecraftBanListServiceImpl,
			minecraftPlayerListServiceImpl,
			minecraftGameRuleServiceImpl,
			minecraftOperatorListServiceImpl,
			minecraftServerSettingsServiceImpl,
			minecraftServerStateServiceImpl,
			minecraftExecutorService
		);
	}
}
