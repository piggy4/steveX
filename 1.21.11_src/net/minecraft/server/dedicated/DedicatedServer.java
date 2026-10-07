package net.minecraft.server.dedicated;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.net.HostAndPort;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import io.netty.handler.ssl.SslContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.DefaultUncaughtExceptionHandlerWithName;
import net.minecraft.SharedConstants;
import net.minecraft.SystemReport;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.ConsoleInput;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerInterface;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.gui.MinecraftServerGui;
import net.minecraft.server.jsonrpc.JsonRpcNotificationService;
import net.minecraft.server.jsonrpc.ManagementServer;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.jsonrpc.security.AuthenticationHandler;
import net.minecraft.server.jsonrpc.security.JsonRpcSslContextProvider;
import net.minecraft.server.jsonrpc.security.SecurityConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.LoggingLevelLoadListener;
import net.minecraft.server.network.ServerTextFilter;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.server.rcon.RconConsoleSource;
import net.minecraft.server.rcon.thread.QueryThreadGs4;
import net.minecraft.server.rcon.thread.RconThread;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.Util;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debugchart.RemoteDebugSampleType;
import net.minecraft.util.debugchart.RemoteSampleLogger;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.util.debugchart.TpsDebugDimensions;
import net.minecraft.util.monitoring.jmx.MinecraftServerStatistics;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class DedicatedServer extends MinecraftServer implements ServerInterface {
	static final Logger LOGGER = LogUtils.getLogger();
	private static final int CONVERSION_RETRY_DELAY_MS = 5000;
	private static final int CONVERSION_RETRIES = 2;
	private final List<ConsoleInput> consoleInput = Collections.synchronizedList(Lists.newArrayList());
	private @Nullable QueryThreadGs4 queryThreadGs4;
	private final RconConsoleSource rconConsoleSource;
	private @Nullable RconThread rconThread;
	private final DedicatedServerSettings settings;
	private @Nullable MinecraftServerGui gui;
	private final @Nullable ServerTextFilter serverTextFilter;
	private @Nullable RemoteSampleLogger tickTimeLogger;
	private boolean isTickTimeLoggingEnabled;
	private final ServerLinks serverLinks;
	private final Map<String, String> codeOfConductTexts;
	private @Nullable ManagementServer jsonRpcServer;
	private long lastHeartbeat;

	public DedicatedServer(
		Thread thread,
		LevelStorageSource.LevelStorageAccess levelStorageAccess,
		PackRepository packRepository,
		WorldStem worldStem,
		DedicatedServerSettings dedicatedServerSettings,
		DataFixer dataFixer,
		Services services
	) {
		super(thread, levelStorageAccess, packRepository, worldStem, Proxy.NO_PROXY, dataFixer, services, LoggingLevelLoadListener.forDedicatedServer());
		this.settings = dedicatedServerSettings;
		this.rconConsoleSource = new RconConsoleSource(this);
		this.serverTextFilter = ServerTextFilter.createFromConfig(dedicatedServerSettings.getProperties());
		this.serverLinks = createServerLinks(dedicatedServerSettings);
		if (dedicatedServerSettings.getProperties().codeOfConduct) {
			this.codeOfConductTexts = readCodeOfConducts();
		} else {
			this.codeOfConductTexts = Map.of();
		}
	}

	private static Map<String, String> readCodeOfConducts() {
		Path path = Path.of("codeofconduct");
		if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
			throw new IllegalArgumentException("Code of Conduct folder does not exist: " + path);
		}

		try {
			Builder<String, String> builder = ImmutableMap.builder();

			try (Stream<Path> stream = Files.list(path)) {
				for (Path path2 : stream.toList()) {
					String string = path2.getFileName().toString();
					if (string.endsWith(".txt")) {
						String string2 = string.substring(0, string.length() - 4).toLowerCase(Locale.ROOT);
						if (!path2.toRealPath().getParent().equals(path.toAbsolutePath())) {
							throw new IllegalArgumentException("Failed to read Code of Conduct file \"" + string + "\" because it links to a file outside the allowed directory");
						}

						try {
							String string3 = String.join("\n", Files.readAllLines(path2, StandardCharsets.UTF_8));
							builder.put(string2, StringUtil.stripColor(string3));
						} catch (IOException iOException) {
							throw new IllegalArgumentException("Failed to read Code of Conduct file " + string, iOException);
						}
					}
				}
			}

			return builder.build();
		} catch (IOException iOException2) {
			throw new IllegalArgumentException("Failed to read Code of Conduct folder", iOException2);
		}
	}

	private SslContext createSslContext() {
		try {
			return JsonRpcSslContextProvider.createFrom(this.getProperties().managementServerTlsKeystore, this.getProperties().managementServerTlsKeystorePassword);
		} catch (Exception exception) {
			JsonRpcSslContextProvider.printInstructions();
			throw new IllegalStateException("Failed to configure TLS for the server management protocol", exception);
		}
	}

	@Override
	public boolean initServer() throws IOException {
		int i = this.getProperties().managementServerPort;
		if (this.getProperties().managementServerEnabled) {
			String string = this.settings.getProperties().managementServerSecret;
			if (!SecurityConfig.isValid(string)) {
				throw new IllegalStateException("Invalid management server secret, must be 40 alphanumeric characters");
			}

			String string2 = this.getProperties().managementServerHost;
			HostAndPort hostAndPort = HostAndPort.fromParts(string2, i);
			SecurityConfig securityConfig = new SecurityConfig(string);
			String string3 = this.getProperties().managementServerAllowedOrigins;
			AuthenticationHandler authenticationHandler = new AuthenticationHandler(securityConfig, string3);
			LOGGER.info("Starting json RPC server on {}", hostAndPort);
			this.jsonRpcServer = new ManagementServer(hostAndPort, authenticationHandler);
			MinecraftApi minecraftApi = MinecraftApi.of(this);
			minecraftApi.notificationManager().registerService(new JsonRpcNotificationService(minecraftApi, this.jsonRpcServer));
			if (this.getProperties().managementServerTlsEnabled) {
				SslContext sslContext = this.createSslContext();
				this.jsonRpcServer.startWithTls(minecraftApi, sslContext);
			} else {
				this.jsonRpcServer.startWithoutTls(minecraftApi);
			}
		}

		Thread thread = new Thread("Server console handler") {
			@Override
			public void run() {
				BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

				String string;
				try {
					while (!DedicatedServer.this.isStopped() && DedicatedServer.this.isRunning() && (string = bufferedReader.readLine()) != null) {
						DedicatedServer.this.handleConsoleInput(string, DedicatedServer.this.createCommandSourceStack());
					}
				} catch (IOException iOException) {
					DedicatedServer.LOGGER.error("Exception handling console input", iOException);
				}
			}
		};
		thread.setDaemon(true);
		thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
		thread.start();
		LOGGER.info("Starting minecraft server version {}", SharedConstants.getCurrentVersion().name());
		if (Runtime.getRuntime().maxMemory() / 1024L / 1024L < 512L) {
			LOGGER.warn("To start the server with more ram, launch it as \"java -Xmx1024M -Xms1024M -jar minecraft_server.jar\"");
		}

		LOGGER.info("Loading properties");
		DedicatedServerProperties dedicatedServerProperties = this.settings.getProperties();
		if (this.isSingleplayer()) {
			this.setLocalIp("127.0.0.1");
		} else {
			this.setUsesAuthentication(dedicatedServerProperties.onlineMode);
			this.setPreventProxyConnections(dedicatedServerProperties.preventProxyConnections);
			this.setLocalIp(dedicatedServerProperties.serverIp);
		}

		this.worldData.setGameType(dedicatedServerProperties.gameMode.get());
		LOGGER.info("Default game type: {}", dedicatedServerProperties.gameMode.get());
		InetAddress inetAddress = null;
		if (!this.getLocalIp().isEmpty()) {
			inetAddress = InetAddress.getByName(this.getLocalIp());
		}

		if (this.getPort() < 0) {
			this.setPort(dedicatedServerProperties.serverPort);
		}

		this.initializeKeyPair();
		LOGGER.info("Starting Minecraft server on {}:{}", this.getLocalIp().isEmpty() ? "*" : this.getLocalIp(), this.getPort());

		try {
			this.getConnection().startTcpServerListener(inetAddress, this.getPort());
		} catch (IOException iOException) {
			LOGGER.warn("**** FAILED TO BIND TO PORT!");
			LOGGER.warn("The exception was: {}", iOException.toString());
			LOGGER.warn("Perhaps a server is already running on that port?");
			return false;
		}

		if (!this.usesAuthentication()) {
			LOGGER.warn("**** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!");
			LOGGER.warn("The server will make no attempt to authenticate usernames. Beware.");
			LOGGER.warn(
				"While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose."
			);
			LOGGER.warn("To change this, set \"online-mode\" to \"true\" in the server.properties file.");
		}

		if (this.convertOldUsers()) {
			this.services.nameToIdCache().save();
		}

		if (!OldUsersConverter.serverReadyAfterUserconversion(this)) {
			return false;
		}

		this.setPlayerList(new DedicatedPlayerList(this, this.registries(), this.playerDataStorage));
		this.tickTimeLogger = new RemoteSampleLogger(TpsDebugDimensions.values().length, this.debugSubscribers(), RemoteDebugSampleType.TICK_TIME);
		long l = Util.getNanos();
		this.services.nameToIdCache().resolveOfflineUsers(!this.usesAuthentication());
		LOGGER.info("Preparing level \"{}\"", this.getLevelIdName());
		this.loadLevel();
		long m = Util.getNanos() - l;
		String string4 = String.format(Locale.ROOT, "%.3fs", m / 1.0E9);
		LOGGER.info("Done ({})! For help, type \"help\"", string4);
		if (dedicatedServerProperties.announcePlayerAchievements != null) {
			this.worldData.getGameRules().set(GameRules.SHOW_ADVANCEMENT_MESSAGES, dedicatedServerProperties.announcePlayerAchievements, this);
		}

		if (dedicatedServerProperties.enableQuery) {
			LOGGER.info("Starting GS4 status listener");
			this.queryThreadGs4 = QueryThreadGs4.create(this);
		}

		if (dedicatedServerProperties.enableRcon) {
			LOGGER.info("Starting remote control listener");
			this.rconThread = RconThread.create(this);
		}

		if (this.getMaxTickLength() > 0L) {
			Thread thread2 = new Thread(new ServerWatchdog(this));
			thread2.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandlerWithName(LOGGER));
			thread2.setName("Server Watchdog");
			thread2.setDaemon(true);
			thread2.start();
		}

		if (dedicatedServerProperties.enableJmxMonitoring) {
			MinecraftServerStatistics.registerJmxMonitoring(this);
			LOGGER.info("JMX monitoring enabled");
		}

		this.notificationManager().serverStarted();
		return true;
	}

	@Override
	public boolean isEnforceWhitelist() {
		return this.settings.getProperties().enforceWhitelist.get();
	}

	@Override
	public void setEnforceWhitelist(boolean bl) {
		this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.enforceWhitelist.update(this.registryAccess(), bl));
	}

	@Override
	public boolean isUsingWhitelist() {
		return this.settings.getProperties().whiteList.get();
	}

	@Override
	public void setUsingWhitelist(boolean bl) {
		this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.whiteList.update(this.registryAccess(), bl));
	}

	@Override
	public void tickServer(BooleanSupplier booleanSupplier) {
		super.tickServer(booleanSupplier);
		if (this.jsonRpcServer != null) {
			this.jsonRpcServer.tick();
		}

		long l = Util.getMillis();
		int i = this.statusHeartbeatInterval();
		if (i > 0) {
			long m = i * TimeUtil.MILLISECONDS_PER_SECOND;
			if (l - this.lastHeartbeat >= m) {
				this.lastHeartbeat = l;
				this.notificationManager().statusHeartbeat();
			}
		}
	}

	@Override
	public boolean saveAllChunks(boolean bl, boolean bl2, boolean bl3) {
		this.notificationManager().serverSaveStarted();
		boolean bl4 = super.saveAllChunks(bl, bl2, bl3);
		this.notificationManager().serverSaveCompleted();
		return bl4;
	}

	@Override
	public boolean allowFlight() {
		return this.settings.getProperties().allowFlight.get();
	}

	public void setAllowFlight(boolean bl) {
		this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.allowFlight.update(this.registryAccess(), bl));
	}

	@Override
	public DedicatedServerProperties getProperties() {
		return this.settings.getProperties();
	}

	public void setDifficulty(Difficulty difficulty) {
		this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.difficulty.update(this.registryAccess(), difficulty));
		this.forceDifficulty();
	}

	@Override
	public void forceDifficulty() {
		this.setDifficulty(this.getProperties().difficulty.get(), true);
	}

	public int viewDistance() {
		return this.settings.getProperties().viewDistance.get();
	}

	public void setViewDistance(int i) {
		this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.viewDistance.update(this.registryAccess(), i));
		this.getPlayerList().setViewDistance(i);
	}

	public int simulationDistance() {
		return this.settings.getProperties().simulationDistance.get();
	}

	public void setSimulationDistance(int i) {
		this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.simulationDistance.update(this.registryAccess(), i));
		this.getPlayerList().setSimulationDistance(i);
	}

	@Override
	public SystemReport fillServerSystemReport(SystemReport systemReport) {
		systemReport.setDetail("Is Modded", () -> this.getModdedStatus().fullDescription());
		systemReport.setDetail("Type", () -> "Dedicated Server (map_server.txt)");
		return systemReport;
	}

	@Override
	public void dumpServerProperties(Path path) throws IOException {
		DedicatedServerProperties dedicatedServerProperties = this.getProperties();

		try (Writer writer = Files.newBufferedWriter(path)) {
			writer.write(String.format(Locale.ROOT, "sync-chunk-writes=%s%n", dedicatedServerProperties.syncChunkWrites));
			writer.write(String.format(Locale.ROOT, "gamemode=%s%n", dedicatedServerProperties.gameMode.get()));
			writer.write(String.format(Locale.ROOT, "entity-broadcast-range-percentage=%d%n", dedicatedServerProperties.entityBroadcastRangePercentage.get()));
			writer.write(String.format(Locale.ROOT, "max-world-size=%d%n", dedicatedServerProperties.maxWorldSize));
			writer.write(String.format(Locale.ROOT, "view-distance=%d%n", dedicatedServerProperties.viewDistance.get()));
			writer.write(String.format(Locale.ROOT, "simulation-distance=%d%n", dedicatedServerProperties.simulationDistance.get()));
			writer.write(String.format(Locale.ROOT, "generate-structures=%s%n", dedicatedServerProperties.worldOptions.generateStructures()));
			writer.write(String.format(Locale.ROOT, "use-native=%s%n", dedicatedServerProperties.useNativeTransport));
			writer.write(String.format(Locale.ROOT, "rate-limit=%d%n", dedicatedServerProperties.rateLimitPacketsPerSecond));
		}
	}

	@Override
	public void onServerExit() {
		if (this.serverTextFilter != null) {
			this.serverTextFilter.close();
		}

		if (this.gui != null) {
			this.gui.close();
		}

		if (this.rconThread != null) {
			this.rconThread.stop();
		}

		if (this.queryThreadGs4 != null) {
			this.queryThreadGs4.stop();
		}

		if (this.jsonRpcServer != null) {
			try {
				this.jsonRpcServer.stop(true);
			} catch (InterruptedException interruptedException) {
				LOGGER.error("Interrupted while stopping the management server", interruptedException);
			}
		}
	}

	@Override
	public void tickConnection() {
		super.tickConnection();
		this.handleConsoleInputs();
	}

	public void handleConsoleInput(String string, CommandSourceStack commandSourceStack) {
		this.consoleInput.add(new ConsoleInput(string, commandSourceStack));
	}

	public void handleConsoleInputs() {
		while (!this.consoleInput.isEmpty()) {
			ConsoleInput consoleInput = this.consoleInput.remove(0);
			this.getCommands().performPrefixedCommand(consoleInput.source, consoleInput.msg);
		}
	}

	@Override
	public boolean isDedicatedServer() {
		return true;
	}

	@Override
	public int getRateLimitPacketsPerSecond() {
		return this.getProperties().rateLimitPacketsPerSecond;
	}

	@Override
	public boolean useNativeTransport() {
		return this.getProperties().useNativeTransport;
	}

	public DedicatedPlayerList getPlayerList() {
		return (DedicatedPlayerList)super.getPlayerList();
	}

	@Override
	public int getMaxPlayers() {
		return this.settings.getProperties().maxPlayers.get();
	}

	public void setMaxPlayers(int i) {
		this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.maxPlayers.update(this.registryAccess(), i));
	}

	@Override
	public boolean isPublished() {
		return true;
	}

	@Override
	public String getServerIp() {
		return this.getLocalIp();
	}

	@Override
	public int getServerPort() {
		return this.getPort();
	}

	@Override
	public String getServerName() {
		return this.getMotd();
	}

	public void showGui() {
		if (this.gui == null) {
			this.gui = MinecraftServerGui.showFrameFor(this);
		}
	}

	public int spawnProtectionRadius() {
		return this.getProperties().spawnProtection.get();
	}

	public void setSpawnProtectionRadius(int i) {
		this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.spawnProtection.update(this.registryAccess(), i));
	}

	@Override
	public boolean isUnderSpawnProtection(ServerLevel serverLevel, BlockPos blockPos, Player player) {
		LevelData.RespawnData respawnData = serverLevel.getRespawnData();
		if (serverLevel.dimension() != respawnData.dimension()) {
			return false;
		}

		if (this.getPlayerList().getOps().isEmpty()) {
			return false;
		}

		if (this.getPlayerList().isOp(player.nameAndId())) {
			return false;
		}

		if (this.spawnProtectionRadius() <= 0) {
			return false;
		}

		BlockPos blockPos2 = respawnData.pos();
		int i = Mth.abs(blockPos.getX() - blockPos2.getX());
		int j = Mth.abs(blockPos.getZ() - blockPos2.getZ());
		int k = Math.max(i, j);
		return k <= this.spawnProtectionRadius();
	}

	@Override
	public boolean repliesToStatus() {
		return this.getProperties().enableStatus.get();
	}

	public void setRepliesToStatus(boolean bl) {
		this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.enableStatus.update(this.registryAccess(), bl));
	}

	@Override
	public boolean hidesOnlinePlayers() {
		return this.getProperties().hideOnlinePlayers.get();
	}

	public void setHidesOnlinePlayers(boolean bl) {
		this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.hideOnlinePlayers.update(this.registryAccess(), bl));
	}

	@Override
	public LevelBasedPermissionSet operatorUserPermissions() {
		return this.getProperties().opPermissions.get();
	}

	public void setOperatorUserPermissions(LevelBasedPermissionSet levelBasedPermissionSet) {
		this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.opPermissions.update(this.registryAccess(), levelBasedPermissionSet));
	}

	@Override
	public PermissionSet getFunctionCompilationPermissions() {
		return this.getProperties().functionPermissions;
	}

	@Override
	public int playerIdleTimeout() {
		return this.settings.getProperties().playerIdleTimeout.get();
	}

	@Override
	public void setPlayerIdleTimeout(int i) {
		this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.playerIdleTimeout.update(this.registryAccess(), i));
	}

	public int statusHeartbeatInterval() {
		return this.settings.getProperties().statusHeartbeatInterval.get();
	}

	public void setStatusHeartbeatInterval(int i) {
		this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.statusHeartbeatInterval.update(this.registryAccess(), i));
	}

	@Override
	public String getMotd() {
		return this.settings.getProperties().motd.get();
	}

	@Override
	public void setMotd(String string) {
		this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.motd.update(this.registryAccess(), string));
	}

	@Override
	public boolean shouldRconBroadcast() {
		return this.getProperties().broadcastRconToOps;
	}

	@Override
	public boolean shouldInformAdmins() {
		return this.getProperties().broadcastConsoleToOps;
	}

	@Override
	public int getAbsoluteMaxWorldSize() {
		return this.getProperties().maxWorldSize;
	}

	@Override
	public int getCompressionThreshold() {
		return this.getProperties().networkCompressionThreshold;
	}

	@Override
	public boolean enforceSecureProfile() {
		DedicatedServerProperties dedicatedServerProperties = this.getProperties();
		return dedicatedServerProperties.enforceSecureProfile && dedicatedServerProperties.onlineMode && this.services.canValidateProfileKeys();
	}

	@Override
	public boolean logIPs() {
		return this.getProperties().logIPs;
	}

	protected boolean convertOldUsers() {
		boolean bl = false;

		for (int i = 0; !bl && i <= 2; i++) {
			if (i > 0) {
				LOGGER.warn("Encountered a problem while converting the user banlist, retrying in a few seconds");
				this.waitForRetry();
			}

			bl = OldUsersConverter.convertUserBanlist(this);
		}

		boolean bl2 = false;

		for (int var7 = 0; !bl2 && var7 <= 2; var7++) {
			if (var7 > 0) {
				LOGGER.warn("Encountered a problem while converting the ip banlist, retrying in a few seconds");
				this.waitForRetry();
			}

			bl2 = OldUsersConverter.convertIpBanlist(this);
		}

		boolean bl3 = false;

		for (int var8 = 0; !bl3 && var8 <= 2; var8++) {
			if (var8 > 0) {
				LOGGER.warn("Encountered a problem while converting the op list, retrying in a few seconds");
				this.waitForRetry();
			}

			bl3 = OldUsersConverter.convertOpsList(this);
		}

		boolean bl4 = false;

		for (int var9 = 0; !bl4 && var9 <= 2; var9++) {
			if (var9 > 0) {
				LOGGER.warn("Encountered a problem while converting the whitelist, retrying in a few seconds");
				this.waitForRetry();
			}

			bl4 = OldUsersConverter.convertWhiteList(this);
		}

		boolean bl5 = false;

		for (int var10 = 0; !bl5 && var10 <= 2; var10++) {
			if (var10 > 0) {
				LOGGER.warn("Encountered a problem while converting the player save files, retrying in a few seconds");
				this.waitForRetry();
			}

			bl5 = OldUsersConverter.convertPlayers(this);
		}

		return bl || bl2 || bl3 || bl4 || bl5;
	}

	private void waitForRetry() {
		try {
			Thread.sleep(5000L);
		} catch (InterruptedException interruptedException) {
		}
	}

	public long getMaxTickLength() {
		return this.getProperties().maxTickTime;
	}

	@Override
	public int getMaxChainedNeighborUpdates() {
		return this.getProperties().maxChainedNeighborUpdates;
	}

	@Override
	public String getPluginNames() {
		return "";
	}

	@Override
	public String runCommand(String string) {
		this.rconConsoleSource.prepareForCommand();
		this.executeBlocking(() -> this.getCommands().performPrefixedCommand(this.rconConsoleSource.createCommandSourceStack(), string));
		return this.rconConsoleSource.getCommandResponse();
	}

	@Override
	public void stopServer() {
		this.notificationManager().serverShuttingDown();
		super.stopServer();
		Util.shutdownExecutors();
	}

	@Override
	public boolean isSingleplayerOwner(NameAndId nameAndId) {
		return false;
	}

	@Override
	public int getScaledTrackingDistance(int i) {
		return this.entityBroadcastRangePercentage() * i / 100;
	}

	public int entityBroadcastRangePercentage() {
		return this.getProperties().entityBroadcastRangePercentage.get();
	}

	public void setEntityBroadcastRangePercentage(int i) {
		this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.entityBroadcastRangePercentage.update(this.registryAccess(), i));
	}

	@Override
	public String getLevelIdName() {
		return this.storageSource.getLevelId();
	}

	@Override
	public boolean forceSynchronousWrites() {
		return this.settings.getProperties().syncChunkWrites;
	}

	@Override
	public TextFilter createTextFilterForPlayer(ServerPlayer serverPlayer) {
		return this.serverTextFilter != null ? this.serverTextFilter.createContext(serverPlayer.getGameProfile()) : TextFilter.DUMMY;
	}

	@Override
	public @Nullable GameType getForcedGameType() {
		return this.forceGameMode() ? this.worldData.getGameType() : null;
	}

	public boolean forceGameMode() {
		return this.settings.getProperties().forceGameMode.get();
	}

	public void setForceGameMode(boolean bl) {
		this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.forceGameMode.update(this.registryAccess(), bl));
		this.enforceGameTypeForPlayers(this.getForcedGameType());
	}

	public GameType gameMode() {
		return this.getProperties().gameMode.get();
	}

	public void setGameMode(GameType gameType) {
		this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.gameMode.update(this.registryAccess(), gameType));
		this.worldData.setGameType(this.gameMode());
		this.enforceGameTypeForPlayers(this.getForcedGameType());
	}

	@Override
	public Optional<MinecraftServer.ServerResourcePackInfo> getServerResourcePack() {
		return this.settings.getProperties().serverResourcePackInfo;
	}

	@Override
	public void endMetricsRecordingTick() {
		super.endMetricsRecordingTick();
		this.isTickTimeLoggingEnabled = this.debugSubscribers().hasAnySubscriberFor(DebugSubscriptions.DEDICATED_SERVER_TICK_TIME);
	}

	@Override
	public SampleLogger getTickTimeLogger() {
		return this.tickTimeLogger;
	}

	@Override
	public boolean isTickTimeLoggingEnabled() {
		return this.isTickTimeLoggingEnabled;
	}

	@Override
	public boolean acceptsTransfers() {
		return this.settings.getProperties().acceptsTransfers.get();
	}

	public void setAcceptsTransfers(boolean bl) {
		this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.acceptsTransfers.update(this.registryAccess(), bl));
	}

	@Override
	public ServerLinks serverLinks() {
		return this.serverLinks;
	}

	@Override
	public int pauseWhenEmptySeconds() {
		return this.settings.getProperties().pauseWhenEmptySeconds.get();
	}

	public void setPauseWhenEmptySeconds(int i) {
		this.settings.update(dedicatedServerProperties -> dedicatedServerProperties.pauseWhenEmptySeconds.update(this.registryAccess(), i));
	}

	private static ServerLinks createServerLinks(DedicatedServerSettings dedicatedServerSettings) {
		Optional<URI> optional = parseBugReportLink(dedicatedServerSettings.getProperties());
		return optional.<ServerLinks>map(uRI -> new ServerLinks(List.of(ServerLinks.KnownLinkType.BUG_REPORT.create(uRI)))).orElse(ServerLinks.EMPTY);
	}

	private static Optional<URI> parseBugReportLink(DedicatedServerProperties dedicatedServerProperties) {
		String string = dedicatedServerProperties.bugReportLink;
		if (string.isEmpty()) {
			return Optional.empty();
		}

		try {
			return Optional.of(Util.parseAndValidateUntrustedUri(string));
		} catch (Exception exception) {
			LOGGER.warn("Failed to parse bug link {}", string, exception);
			return Optional.empty();
		}
	}

	@Override
	public Map<String, String> getCodeOfConducts() {
		return this.codeOfConductTexts;
	}
}
