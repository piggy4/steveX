package net.minecraft.server.players;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.notifications.NotificationService;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.util.FileUtil;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class PlayerList {
	public static final File USERBANLIST_FILE = new File("banned-players.json");
	public static final File IPBANLIST_FILE = new File("banned-ips.json");
	public static final File OPLIST_FILE = new File("ops.json");
	public static final File WHITELIST_FILE = new File("whitelist.json");
	public static final Component CHAT_FILTERED_FULL = Component.translatable("chat.filtered_full");
	public static final Component DUPLICATE_LOGIN_DISCONNECT_MESSAGE = Component.translatable("multiplayer.disconnect.duplicate_login");
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int SEND_PLAYER_INFO_INTERVAL = 600;
	private static final SimpleDateFormat BAN_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z", Locale.ROOT);
	private final MinecraftServer server;
	private final List<ServerPlayer> players = Lists.newArrayList();
	private final Map<UUID, ServerPlayer> playersByUUID = Maps.newHashMap();
	private final UserBanList bans;
	private final IpBanList ipBans;
	private final ServerOpList ops;
	private final UserWhiteList whitelist;
	private final Map<UUID, ServerStatsCounter> stats = Maps.newHashMap();
	private final Map<UUID, PlayerAdvancements> advancements = Maps.newHashMap();
	private final PlayerDataStorage playerIo;
	private final LayeredRegistryAccess<RegistryLayer> registries;
	private int viewDistance;
	private int simulationDistance;
	private boolean allowCommandsForAllPlayers;
	private int sendAllPlayerInfoIn;

	public PlayerList(
		MinecraftServer minecraftServer,
		LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess,
		PlayerDataStorage playerDataStorage,
		NotificationService notificationService
	) {
		this.server = minecraftServer;
		this.registries = layeredRegistryAccess;
		this.playerIo = playerDataStorage;
		this.whitelist = new UserWhiteList(WHITELIST_FILE, notificationService);
		this.ops = new ServerOpList(OPLIST_FILE, notificationService);
		this.bans = new UserBanList(USERBANLIST_FILE, notificationService);
		this.ipBans = new IpBanList(IPBANLIST_FILE, notificationService);
	}

	public void placeNewPlayer(Connection connection, ServerPlayer serverPlayer, CommonListenerCookie commonListenerCookie) {
		NameAndId nameAndId = serverPlayer.nameAndId();
		UserNameToIdResolver userNameToIdResolver = this.server.services().nameToIdCache();
		Optional<NameAndId> optional = userNameToIdResolver.get(nameAndId.id());
		String string = optional.map(NameAndId::name).orElse(nameAndId.name());
		userNameToIdResolver.add(nameAndId);
		ServerLevel serverLevel = serverPlayer.level();
		String string2 = connection.getLoggableAddress(this.server.logIPs());
		LOGGER.info(
			"{}[{}] logged in with entity id {} at ({}, {}, {})",
			serverPlayer.getPlainTextName(),
			string2,
			serverPlayer.getId(),
			serverPlayer.getX(),
			serverPlayer.getY(),
			serverPlayer.getZ()
		);
		LevelData levelData = serverLevel.getLevelData();
		ServerGamePacketListenerImpl serverGamePacketListenerImpl = new ServerGamePacketListenerImpl(this.server, connection, serverPlayer, commonListenerCookie);
		connection.setupInboundProtocol(
			GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(this.server.registryAccess()), serverGamePacketListenerImpl),
			serverGamePacketListenerImpl
		);
		serverGamePacketListenerImpl.suspendFlushing();
		GameRules gameRules = serverLevel.getGameRules();
		boolean bl = gameRules.get(GameRules.IMMEDIATE_RESPAWN);
		boolean bl2 = gameRules.get(GameRules.REDUCED_DEBUG_INFO);
		boolean bl3 = gameRules.get(GameRules.LIMITED_CRAFTING);
		serverGamePacketListenerImpl.send(
			new ClientboundLoginPacket(
				serverPlayer.getId(),
				levelData.isHardcore(),
				this.server.levelKeys(),
				this.getMaxPlayers(),
				this.getViewDistance(),
				this.getSimulationDistance(),
				bl2,
				!bl,
				bl3,
				serverPlayer.createCommonSpawnInfo(serverLevel),
				this.server.enforceSecureProfile()
			)
		);
		serverGamePacketListenerImpl.send(new ClientboundChangeDifficultyPacket(levelData.getDifficulty(), levelData.isDifficultyLocked()));
		serverGamePacketListenerImpl.send(new ClientboundPlayerAbilitiesPacket(serverPlayer.getAbilities()));
		serverGamePacketListenerImpl.send(new ClientboundSetHeldSlotPacket(serverPlayer.getInventory().getSelectedSlot()));
		RecipeManager recipeManager = this.server.getRecipeManager();
		serverGamePacketListenerImpl.send(
			new ClientboundUpdateRecipesPacket(recipeManager.getSynchronizedItemProperties(), recipeManager.getSynchronizedStonecutterRecipes())
		);
		this.sendPlayerPermissionLevel(serverPlayer);
		serverPlayer.getStats().markAllDirty();
		serverPlayer.getRecipeBook().sendInitialRecipeBook(serverPlayer);
		this.updateEntireScoreboard(serverLevel.getScoreboard(), serverPlayer);
		this.server.invalidateStatus();
		MutableComponent mutableComponent;
		if (serverPlayer.getGameProfile().name().equalsIgnoreCase(string)) {
			mutableComponent = Component.translatable("multiplayer.player.joined", serverPlayer.getDisplayName());
		} else {
			mutableComponent = Component.translatable("multiplayer.player.joined.renamed", serverPlayer.getDisplayName(), string);
		}

		this.broadcastSystemMessage(mutableComponent.withStyle(ChatFormatting.YELLOW), false);
		serverGamePacketListenerImpl.teleport(serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), serverPlayer.getYRot(), serverPlayer.getXRot());
		ServerStatus serverStatus = this.server.getStatus();
		if (serverStatus != null && !commonListenerCookie.transferred()) {
			serverPlayer.sendServerStatus(serverStatus);
		}

		serverPlayer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(this.players));
		this.players.add(serverPlayer);
		this.playersByUUID.put(serverPlayer.getUUID(), serverPlayer);
		this.broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(serverPlayer)));
		this.sendLevelInfo(serverPlayer, serverLevel);
		serverLevel.addNewPlayer(serverPlayer);
		this.server.getCustomBossEvents().onPlayerConnect(serverPlayer);
		this.sendActivePlayerEffects(serverPlayer);
		serverPlayer.initInventoryMenu();
		this.server.notificationManager().playerJoined(serverPlayer);
		serverGamePacketListenerImpl.resumeFlushing();
	}

	protected void updateEntireScoreboard(ServerScoreboard serverScoreboard, ServerPlayer serverPlayer) {
		Set<Objective> set = Sets.newHashSet();

		for (PlayerTeam playerTeam : serverScoreboard.getPlayerTeams()) {
			serverPlayer.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(playerTeam, true));
		}

		for (DisplaySlot displaySlot : DisplaySlot.values()) {
			Objective objective = serverScoreboard.getDisplayObjective(displaySlot);
			if (objective != null && !set.contains(objective)) {
				for (Packet<?> packet : serverScoreboard.getStartTrackingPackets(objective)) {
					serverPlayer.connection.send(packet);
				}

				set.add(objective);
			}
		}
	}

	public void addWorldborderListener(ServerLevel serverLevel) {
		serverLevel.getWorldBorder().addListener(new BorderChangeListener() {
			@Override
			public void onSetSize(WorldBorder worldBorder, double d) {
				PlayerList.this.broadcastAll(new ClientboundSetBorderSizePacket(worldBorder), serverLevel.dimension());
			}

			@Override
			public void onLerpSize(WorldBorder worldBorder, double d, double e, long l, long m) {
				PlayerList.this.broadcastAll(new ClientboundSetBorderLerpSizePacket(worldBorder), serverLevel.dimension());
			}

			@Override
			public void onSetCenter(WorldBorder worldBorder, double d, double e) {
				PlayerList.this.broadcastAll(new ClientboundSetBorderCenterPacket(worldBorder), serverLevel.dimension());
			}

			@Override
			public void onSetWarningTime(WorldBorder worldBorder, int i) {
				PlayerList.this.broadcastAll(new ClientboundSetBorderWarningDelayPacket(worldBorder), serverLevel.dimension());
			}

			@Override
			public void onSetWarningBlocks(WorldBorder worldBorder, int i) {
				PlayerList.this.broadcastAll(new ClientboundSetBorderWarningDistancePacket(worldBorder), serverLevel.dimension());
			}

			@Override
			public void onSetDamagePerBlock(WorldBorder worldBorder, double d) {
			}

			@Override
			public void onSetSafeZone(WorldBorder worldBorder, double d) {
			}
		});
	}

	public Optional<CompoundTag> loadPlayerData(NameAndId nameAndId) {
		CompoundTag compoundTag = this.server.getWorldData().getLoadedPlayerTag();
		if (this.server.isSingleplayerOwner(nameAndId) && compoundTag != null) {
			LOGGER.debug("loading single player");
			return Optional.of(compoundTag);
		} else {
			return this.playerIo.load(nameAndId);
		}
	}

	protected void save(ServerPlayer serverPlayer) {
		this.playerIo.save(serverPlayer);
		ServerStatsCounter serverStatsCounter = this.stats.get(serverPlayer.getUUID());
		if (serverStatsCounter != null) {
			serverStatsCounter.save();
		}

		PlayerAdvancements playerAdvancements = this.advancements.get(serverPlayer.getUUID());
		if (playerAdvancements != null) {
			playerAdvancements.save();
		}
	}

	public void remove(ServerPlayer serverPlayer) {
		ServerLevel serverLevel = serverPlayer.level();
		serverPlayer.awardStat(Stats.LEAVE_GAME);
		this.save(serverPlayer);
		if (serverPlayer.isPassenger()) {
			Entity entity = serverPlayer.getRootVehicle();
			if (entity.hasExactlyOnePlayerPassenger()) {
				LOGGER.debug("Removing player mount");
				serverPlayer.stopRiding();
				entity.getPassengersAndSelf().forEach(entityx -> entityx.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER));
			}
		}

		serverPlayer.unRide();

		for (ThrownEnderpearl thrownEnderpearl : serverPlayer.getEnderPearls()) {
			thrownEnderpearl.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER);
		}

		serverLevel.removePlayerImmediately(serverPlayer, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
		serverPlayer.getAdvancements().stopListening();
		this.players.remove(serverPlayer);
		this.server.getCustomBossEvents().onPlayerDisconnect(serverPlayer);
		UUID uUID = serverPlayer.getUUID();
		ServerPlayer serverPlayer2 = this.playersByUUID.get(uUID);
		if (serverPlayer2 == serverPlayer) {
			this.playersByUUID.remove(uUID);
			this.stats.remove(uUID);
			this.advancements.remove(uUID);
			this.server.notificationManager().playerLeft(serverPlayer);
		}

		this.broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(serverPlayer.getUUID())));
	}

	public @Nullable Component canPlayerLogin(SocketAddress socketAddress, NameAndId nameAndId) {
		if (this.bans.isBanned(nameAndId)) {
			UserBanListEntry userBanListEntry = this.bans.get(nameAndId);
			MutableComponent mutableComponent = Component.translatable("multiplayer.disconnect.banned.reason", userBanListEntry.getReasonMessage());
			if (userBanListEntry.getExpires() != null) {
				mutableComponent.append(Component.translatable("multiplayer.disconnect.banned.expiration", BAN_DATE_FORMAT.format(userBanListEntry.getExpires())));
			}

			return mutableComponent;
		} else {
			if (!this.isWhiteListed(nameAndId)) {
				return Component.translatable("multiplayer.disconnect.not_whitelisted");
			}

			if (this.ipBans.isBanned(socketAddress)) {
				IpBanListEntry ipBanListEntry = this.ipBans.get(socketAddress);
				MutableComponent mutableComponent = Component.translatable("multiplayer.disconnect.banned_ip.reason", ipBanListEntry.getReasonMessage());
				if (ipBanListEntry.getExpires() != null) {
					mutableComponent.append(Component.translatable("multiplayer.disconnect.banned_ip.expiration", BAN_DATE_FORMAT.format(ipBanListEntry.getExpires())));
				}

				return mutableComponent;
			} else {
				return this.players.size() >= this.getMaxPlayers() && !this.canBypassPlayerLimit(nameAndId)
					? Component.translatable("multiplayer.disconnect.server_full")
					: null;
			}
		}
	}

	public boolean disconnectAllPlayersWithProfile(UUID uUID) {
		Set<ServerPlayer> set = Sets.newIdentityHashSet();

		for (ServerPlayer serverPlayer : this.players) {
			if (serverPlayer.getUUID().equals(uUID)) {
				set.add(serverPlayer);
			}
		}

		ServerPlayer serverPlayer2 = this.playersByUUID.get(uUID);
		if (serverPlayer2 != null) {
			set.add(serverPlayer2);
		}

		for (ServerPlayer serverPlayer3 : set) {
			serverPlayer3.connection.disconnect(DUPLICATE_LOGIN_DISCONNECT_MESSAGE);
		}

		return !set.isEmpty();
	}

	public ServerPlayer respawn(ServerPlayer serverPlayer, boolean bl, Entity.RemovalReason removalReason) {
		TeleportTransition teleportTransition = serverPlayer.findRespawnPositionAndUseSpawnBlock(!bl, TeleportTransition.DO_NOTHING);
		this.players.remove(serverPlayer);
		serverPlayer.level().removePlayerImmediately(serverPlayer, removalReason);
		ServerLevel serverLevel = teleportTransition.newLevel();
		ServerPlayer serverPlayer2 = new ServerPlayer(this.server, serverLevel, serverPlayer.getGameProfile(), serverPlayer.clientInformation());
		serverPlayer2.connection = serverPlayer.connection;
		serverPlayer2.restoreFrom(serverPlayer, bl);
		serverPlayer2.setId(serverPlayer.getId());
		serverPlayer2.setMainArm(serverPlayer.getMainArm());
		if (!teleportTransition.missingRespawnBlock()) {
			serverPlayer2.copyRespawnPosition(serverPlayer);
		}

		for (String string : serverPlayer.getTags()) {
			serverPlayer2.addTag(string);
		}

		Vec3 vec3 = teleportTransition.position();
		serverPlayer2.snapTo(vec3.x, vec3.y, vec3.z, teleportTransition.yRot(), teleportTransition.xRot());
		if (teleportTransition.missingRespawnBlock()) {
			serverPlayer2.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE, 0.0F));
		}

		byte b = (byte)(bl ? 1 : 0);
		ServerLevel serverLevel2 = serverPlayer2.level();
		LevelData levelData = serverLevel2.getLevelData();
		serverPlayer2.connection.send(new ClientboundRespawnPacket(serverPlayer2.createCommonSpawnInfo(serverLevel2), b));
		serverPlayer2.connection.teleport(serverPlayer2.getX(), serverPlayer2.getY(), serverPlayer2.getZ(), serverPlayer2.getYRot(), serverPlayer2.getXRot());
		serverPlayer2.connection.send(new ClientboundSetDefaultSpawnPositionPacket(serverLevel.getRespawnData()));
		serverPlayer2.connection.send(new ClientboundChangeDifficultyPacket(levelData.getDifficulty(), levelData.isDifficultyLocked()));
		serverPlayer2.connection
			.send(new ClientboundSetExperiencePacket(serverPlayer2.experienceProgress, serverPlayer2.totalExperience, serverPlayer2.experienceLevel));
		this.sendActivePlayerEffects(serverPlayer2);
		this.sendLevelInfo(serverPlayer2, serverLevel);
		this.sendPlayerPermissionLevel(serverPlayer2);
		serverLevel.addRespawnedPlayer(serverPlayer2);
		this.players.add(serverPlayer2);
		this.playersByUUID.put(serverPlayer2.getUUID(), serverPlayer2);
		serverPlayer2.initInventoryMenu();
		serverPlayer2.setHealth(serverPlayer2.getHealth());
		ServerPlayer.RespawnConfig respawnConfig = serverPlayer2.getRespawnConfig();
		if (!bl && respawnConfig != null) {
			LevelData.RespawnData respawnData = respawnConfig.respawnData();
			ServerLevel serverLevel3 = this.server.getLevel(respawnData.dimension());
			if (serverLevel3 != null) {
				BlockPos blockPos = respawnData.pos();
				BlockState blockState = serverLevel3.getBlockState(blockPos);
				if (blockState.is(Blocks.RESPAWN_ANCHOR)) {
					serverPlayer2.connection
						.send(
							new ClientboundSoundPacket(
								SoundEvents.RESPAWN_ANCHOR_DEPLETE,
								SoundSource.BLOCKS,
								blockPos.getX(),
								blockPos.getY(),
								blockPos.getZ(),
								1.0F,
								1.0F,
								serverLevel.getRandom().nextLong()
							)
						);
				}
			}
		}

		return serverPlayer2;
	}

	public void sendActivePlayerEffects(ServerPlayer serverPlayer) {
		this.sendActiveEffects(serverPlayer, serverPlayer.connection);
	}

	public void sendActiveEffects(LivingEntity livingEntity, ServerGamePacketListenerImpl serverGamePacketListenerImpl) {
		for (MobEffectInstance mobEffectInstance : livingEntity.getActiveEffects()) {
			serverGamePacketListenerImpl.send(new ClientboundUpdateMobEffectPacket(livingEntity.getId(), mobEffectInstance, false));
		}
	}

	public void sendPlayerPermissionLevel(ServerPlayer serverPlayer) {
		LevelBasedPermissionSet levelBasedPermissionSet = this.server.getProfilePermissions(serverPlayer.nameAndId());
		this.sendPlayerPermissionLevel(serverPlayer, levelBasedPermissionSet);
	}

	public void tick() {
		if (++this.sendAllPlayerInfoIn > 600) {
			this.broadcastAll(new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY), this.players));
			this.sendAllPlayerInfoIn = 0;
		}
	}

	public void broadcastAll(Packet<?> packet) {
		for (ServerPlayer serverPlayer : this.players) {
			serverPlayer.connection.send(packet);
		}
	}

	public void broadcastAll(Packet<?> packet, ResourceKey<Level> resourceKey) {
		for (ServerPlayer serverPlayer : this.players) {
			if (serverPlayer.level().dimension() == resourceKey) {
				serverPlayer.connection.send(packet);
			}
		}
	}

	public void broadcastSystemToTeam(Player player, Component component) {
		Team team = player.getTeam();
		if (team != null) {
			for (String string : team.getPlayers()) {
				ServerPlayer serverPlayer = this.getPlayerByName(string);
				if (serverPlayer != null && serverPlayer != player) {
					serverPlayer.sendSystemMessage(component);
				}
			}
		}
	}

	public void broadcastSystemToAllExceptTeam(Player player, Component component) {
		Team team = player.getTeam();
		if (team == null) {
			this.broadcastSystemMessage(component, false);
		} else {
			for (int i = 0; i < this.players.size(); i++) {
				ServerPlayer serverPlayer = this.players.get(i);
				if (serverPlayer.getTeam() != team) {
					serverPlayer.sendSystemMessage(component);
				}
			}
		}
	}

	public String[] getPlayerNamesArray() {
		String[] strings = new String[this.players.size()];

		for (int i = 0; i < this.players.size(); i++) {
			strings[i] = this.players.get(i).getGameProfile().name();
		}

		return strings;
	}

	public UserBanList getBans() {
		return this.bans;
	}

	public IpBanList getIpBans() {
		return this.ipBans;
	}

	public void op(NameAndId nameAndId) {
		this.op(nameAndId, Optional.empty(), Optional.empty());
	}

	public void op(NameAndId nameAndId, Optional<LevelBasedPermissionSet> optional, Optional<Boolean> optional2) {
		this.ops
			.add(new ServerOpListEntry(nameAndId, optional.orElse(this.server.operatorUserPermissions()), optional2.orElse(this.ops.canBypassPlayerLimit(nameAndId))));
		ServerPlayer serverPlayer = this.getPlayer(nameAndId.id());
		if (serverPlayer != null) {
			this.sendPlayerPermissionLevel(serverPlayer);
		}
	}

	public void deop(NameAndId nameAndId) {
		if (this.ops.remove(nameAndId)) {
			ServerPlayer serverPlayer = this.getPlayer(nameAndId.id());
			if (serverPlayer != null) {
				this.sendPlayerPermissionLevel(serverPlayer);
			}
		}
	}

	private void sendPlayerPermissionLevel(ServerPlayer serverPlayer, LevelBasedPermissionSet levelBasedPermissionSet) {
		if (serverPlayer.connection != null) {
			byte b = switch (levelBasedPermissionSet.level()) {
				case ALL -> 24;
				case MODERATORS -> 25;
				case GAMEMASTERS -> 26;
				case ADMINS -> 27;
				case OWNERS -> 28;
			};
			serverPlayer.connection.send(new ClientboundEntityEventPacket(serverPlayer, b));
		}

		this.server.getCommands().sendCommands(serverPlayer);
	}

	public boolean isWhiteListed(NameAndId nameAndId) {
		return !this.isUsingWhitelist() || this.ops.contains(nameAndId) || this.whitelist.contains(nameAndId);
	}

	public boolean isOp(NameAndId nameAndId) {
		return this.ops.contains(nameAndId)
			|| this.server.isSingleplayerOwner(nameAndId) && this.server.getWorldData().isAllowCommands()
			|| this.allowCommandsForAllPlayers;
	}

	public @Nullable ServerPlayer getPlayerByName(String string) {
		int i = this.players.size();

		for (int j = 0; j < i; j++) {
			ServerPlayer serverPlayer = this.players.get(j);
			if (serverPlayer.getGameProfile().name().equalsIgnoreCase(string)) {
				return serverPlayer;
			}
		}

		return null;
	}

	public void broadcast(@Nullable Player player, double d, double e, double f, double g, ResourceKey<Level> resourceKey, Packet<?> packet) {
		for (int i = 0; i < this.players.size(); i++) {
			ServerPlayer serverPlayer = this.players.get(i);
			if (serverPlayer != player && serverPlayer.level().dimension() == resourceKey) {
				double h = d - serverPlayer.getX();
				double j = e - serverPlayer.getY();
				double k = f - serverPlayer.getZ();
				if (h * h + j * j + k * k < g * g) {
					serverPlayer.connection.send(packet);
				}
			}
		}
	}

	public void saveAll() {
		for (int i = 0; i < this.players.size(); i++) {
			this.save(this.players.get(i));
		}
	}

	public UserWhiteList getWhiteList() {
		return this.whitelist;
	}

	public String[] getWhiteListNames() {
		return this.whitelist.getUserList();
	}

	public ServerOpList getOps() {
		return this.ops;
	}

	public String[] getOpNames() {
		return this.ops.getUserList();
	}

	public void reloadWhiteList() {
	}

	public void sendLevelInfo(ServerPlayer serverPlayer, ServerLevel serverLevel) {
		WorldBorder worldBorder = serverLevel.getWorldBorder();
		serverPlayer.connection.send(new ClientboundInitializeBorderPacket(worldBorder));
		serverPlayer.connection
			.send(new ClientboundSetTimePacket(serverLevel.getGameTime(), serverLevel.getDayTime(), serverLevel.getGameRules().get(GameRules.ADVANCE_TIME)));
		serverPlayer.connection.send(new ClientboundSetDefaultSpawnPositionPacket(serverLevel.getRespawnData()));
		if (serverLevel.isRaining()) {
			serverPlayer.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0F));
			serverPlayer.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, serverLevel.getRainLevel(1.0F)));
			serverPlayer.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, serverLevel.getThunderLevel(1.0F)));
		}

		serverPlayer.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0.0F));
		this.server.tickRateManager().updateJoiningPlayer(serverPlayer);
	}

	public void sendAllPlayerInfo(ServerPlayer serverPlayer) {
		serverPlayer.inventoryMenu.sendAllDataToRemote();
		serverPlayer.resetSentInfo();
		serverPlayer.connection.send(new ClientboundSetHeldSlotPacket(serverPlayer.getInventory().getSelectedSlot()));
	}

	public int getPlayerCount() {
		return this.players.size();
	}

	public int getMaxPlayers() {
		return this.server.getMaxPlayers();
	}

	public boolean isUsingWhitelist() {
		return this.server.isUsingWhitelist();
	}

	public List<ServerPlayer> getPlayersWithAddress(String string) {
		List<ServerPlayer> list = Lists.newArrayList();

		for (ServerPlayer serverPlayer : this.players) {
			if (serverPlayer.getIpAddress().equals(string)) {
				list.add(serverPlayer);
			}
		}

		return list;
	}

	public int getViewDistance() {
		return this.viewDistance;
	}

	public int getSimulationDistance() {
		return this.simulationDistance;
	}

	public MinecraftServer getServer() {
		return this.server;
	}

	public @Nullable CompoundTag getSingleplayerData() {
		return null;
	}

	public void setAllowCommandsForAllPlayers(boolean bl) {
		this.allowCommandsForAllPlayers = bl;
	}

	public void removeAll() {
		for (int i = 0; i < this.players.size(); i++) {
			this.players.get(i).connection.disconnect(Component.translatable("multiplayer.disconnect.server_shutdown"));
		}
	}

	public void broadcastSystemMessage(Component component, boolean bl) {
		this.broadcastSystemMessage(component, serverPlayer -> component, bl);
	}

	public void broadcastSystemMessage(Component component, Function<ServerPlayer, Component> function, boolean bl) {
		this.server.sendSystemMessage(component);

		for (ServerPlayer serverPlayer : this.players) {
			Component component2 = function.apply(serverPlayer);
			if (component2 != null) {
				serverPlayer.sendSystemMessage(component2, bl);
			}
		}
	}

	public void broadcastChatMessage(PlayerChatMessage playerChatMessage, CommandSourceStack commandSourceStack, ChatType.Bound bound) {
		this.broadcastChatMessage(playerChatMessage, commandSourceStack::shouldFilterMessageTo, commandSourceStack.getPlayer(), bound);
	}

	public void broadcastChatMessage(PlayerChatMessage playerChatMessage, ServerPlayer serverPlayer, ChatType.Bound bound) {
		this.broadcastChatMessage(playerChatMessage, serverPlayer::shouldFilterMessageTo, serverPlayer, bound);
	}

	private void broadcastChatMessage(
		PlayerChatMessage playerChatMessage, Predicate<ServerPlayer> predicate, @Nullable ServerPlayer serverPlayer, ChatType.Bound bound
	) {
		boolean bl = this.verifyChatTrusted(playerChatMessage);
		this.server.logChatMessage(playerChatMessage.decoratedContent(), bound, bl ? null : "Not Secure");
		OutgoingChatMessage outgoingChatMessage = OutgoingChatMessage.create(playerChatMessage);
		boolean bl2 = false;

		for (ServerPlayer serverPlayer2 : this.players) {
			boolean bl3 = predicate.test(serverPlayer2);
			serverPlayer2.sendChatMessage(outgoingChatMessage, bl3, bound);
			bl2 |= bl3 && playerChatMessage.isFullyFiltered();
		}

		if (bl2 && serverPlayer != null) {
			serverPlayer.sendSystemMessage(CHAT_FILTERED_FULL);
		}
	}

	private boolean verifyChatTrusted(PlayerChatMessage playerChatMessage) {
		return playerChatMessage.hasSignature() && !playerChatMessage.hasExpiredServer(Instant.now());
	}

	public ServerStatsCounter getPlayerStats(Player player) {
		GameProfile gameProfile = player.getGameProfile();
		return this.stats.computeIfAbsent(gameProfile.id(), uUID -> {
			Path path = this.locateStatsFile(gameProfile);
			return new ServerStatsCounter(this.server, path);
		});
	}

	private Path locateStatsFile(GameProfile gameProfile) {
		Path path = this.server.getWorldPath(LevelResource.PLAYER_STATS_DIR);
		Path path2 = path.resolve(gameProfile.id() + ".json");
		if (Files.exists(path2)) {
			return path2;
		}

		String string = gameProfile.name() + ".json";
		if (FileUtil.isValidPathSegment(string)) {
			Path path3 = path.resolve(string);
			if (Files.isRegularFile(path3)) {
				try {
					return Files.move(path3, path2);
				} catch (IOException iOException) {
					LOGGER.warn("Failed to copy file {} to {}", string, path2);
					return path3;
				}
			}
		}

		return path2;
	}

	public PlayerAdvancements getPlayerAdvancements(ServerPlayer serverPlayer) {
		UUID uUID = serverPlayer.getUUID();
		PlayerAdvancements playerAdvancements = this.advancements.get(uUID);
		if (playerAdvancements == null) {
			Path path = this.server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(uUID + ".json");
			playerAdvancements = new PlayerAdvancements(this.server.getFixerUpper(), this, this.server.getAdvancements(), path, serverPlayer);
			this.advancements.put(uUID, playerAdvancements);
		}

		playerAdvancements.setPlayer(serverPlayer);
		return playerAdvancements;
	}

	public void setViewDistance(int i) {
		this.viewDistance = i;
		this.broadcastAll(new ClientboundSetChunkCacheRadiusPacket(i));

		for (ServerLevel serverLevel : this.server.getAllLevels()) {
			serverLevel.getChunkSource().setViewDistance(i);
		}
	}

	public void setSimulationDistance(int i) {
		this.simulationDistance = i;
		this.broadcastAll(new ClientboundSetSimulationDistancePacket(i));

		for (ServerLevel serverLevel : this.server.getAllLevels()) {
			serverLevel.getChunkSource().setSimulationDistance(i);
		}
	}

	public List<ServerPlayer> getPlayers() {
		return this.players;
	}

	public @Nullable ServerPlayer getPlayer(UUID uUID) {
		return this.playersByUUID.get(uUID);
	}

	public @Nullable ServerPlayer getPlayer(String string) {
		for (ServerPlayer serverPlayer : this.players) {
			if (serverPlayer.getGameProfile().name().equalsIgnoreCase(string)) {
				return serverPlayer;
			}
		}

		return null;
	}

	public boolean canBypassPlayerLimit(NameAndId nameAndId) {
		return false;
	}

	public void reloadResources() {
		for (PlayerAdvancements playerAdvancements : this.advancements.values()) {
			playerAdvancements.reload(this.server.getAdvancements());
		}

		this.broadcastAll(new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(this.registries)));
		RecipeManager recipeManager = this.server.getRecipeManager();
		ClientboundUpdateRecipesPacket clientboundUpdateRecipesPacket = new ClientboundUpdateRecipesPacket(
			recipeManager.getSynchronizedItemProperties(), recipeManager.getSynchronizedStonecutterRecipes()
		);

		for (ServerPlayer serverPlayer : this.players) {
			serverPlayer.connection.send(clientboundUpdateRecipesPacket);
			serverPlayer.getRecipeBook().sendInitialRecipeBook(serverPlayer);
		}
	}

	public boolean isAllowCommandsForAllPlayers() {
		return this.allowCommandsForAllPlayers;
	}
}
