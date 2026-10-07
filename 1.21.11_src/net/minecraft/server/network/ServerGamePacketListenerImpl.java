package net.minecraft.server.network;

import com.google.common.collect.Lists;
import com.google.common.primitives.Floats;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.CommandSigningContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ArgumentSignatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.HashedStack;
import net.minecraft.network.TickablePacketListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.LastSeenMessagesValidator;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MessageSignatureCache;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.chat.SignableCommand;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.chat.SignedMessageChain;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTagQueryPacket;
import net.minecraft.network.protocol.game.ClientboundTestInstanceBlockStatus;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundBlockEntityTagQueryPacket;
import net.minecraft.network.protocol.game.ServerboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundChangeGameModePacket;
import net.minecraft.network.protocol.game.ServerboundChatAckPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ServerboundConfigurationAcknowledgedPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundContainerSlotStateChangedPacket;
import net.minecraft.network.protocol.game.ServerboundDebugSubscriptionRequestPacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundEntityTagQueryPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundJigsawGeneratePacket;
import net.minecraft.network.protocol.game.ServerboundLockDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandMinecartPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSetJigsawBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetTestBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.network.protocol.game.ServerboundTestInstanceBlockActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.GameModeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.FutureChain;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.SignatureValidator;
import net.minecraft.util.StringUtil;
import net.minecraft.util.TickThrottler;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.entity.TestBlockEntity;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ServerGamePacketListenerImpl
	extends ServerCommonPacketListenerImpl
	implements GameProtocols.Context,
	ServerGamePacketListener,
	ServerPlayerConnection,
	TickablePacketListener {
	static final Logger LOGGER = LogUtils.getLogger();
	private static final int NO_BLOCK_UPDATES_TO_ACK = -1;
	private static final int TRACKED_MESSAGE_DISCONNECT_THRESHOLD = 4096;
	private static final int MAXIMUM_FLYING_TICKS = 80;
	private static final int ATTACK_INDICATOR_TOLERANCE_TICKS = 5;
	public static final int CLIENT_LOADED_TIMEOUT_TIME = 60;
	private static final Component CHAT_VALIDATION_FAILED = Component.translatable("multiplayer.disconnect.chat_validation_failed");
	private static final Component INVALID_COMMAND_SIGNATURE = Component.translatable("chat.disabled.invalid_command_signature").withStyle(ChatFormatting.RED);
	private static final int MAX_COMMAND_SUGGESTIONS = 1000;
	public ServerPlayer player;
	public final PlayerChunkSender chunkSender;
	private int tickCount;
	private int ackBlockChangesUpTo = -1;
	private final TickThrottler chatSpamThrottler = new TickThrottler(20, 200);
	private final TickThrottler dropSpamThrottler = new TickThrottler(20, 1480);
	private double firstGoodX;
	private double firstGoodY;
	private double firstGoodZ;
	private double lastGoodX;
	private double lastGoodY;
	private double lastGoodZ;
	private @Nullable Entity lastVehicle;
	private double vehicleFirstGoodX;
	private double vehicleFirstGoodY;
	private double vehicleFirstGoodZ;
	private double vehicleLastGoodX;
	private double vehicleLastGoodY;
	private double vehicleLastGoodZ;
	private @Nullable Vec3 awaitingPositionFromClient;
	private int awaitingTeleport;
	private int awaitingTeleportTime;
	private boolean clientIsFloating;
	private int aboveGroundTickCount;
	private boolean clientVehicleIsFloating;
	private int aboveGroundVehicleTickCount;
	private int receivedMovePacketCount;
	private int knownMovePacketCount;
	private boolean receivedMovementThisTick;
	private @Nullable RemoteChatSession chatSession;
	private SignedMessageChain.Decoder signedMessageDecoder;
	private final LastSeenMessagesValidator lastSeenMessages = new LastSeenMessagesValidator(20);
	private int nextChatIndex;
	private final MessageSignatureCache messageSignatureCache = MessageSignatureCache.createDefault();
	private final FutureChain chatMessageChain;
	private boolean waitingForSwitchToConfig;
	private boolean waitingForRespawn;
	private int clientLoadedTimeoutTimer;

	public ServerGamePacketListenerImpl(
		MinecraftServer minecraftServer, Connection connection, ServerPlayer serverPlayer, CommonListenerCookie commonListenerCookie
	) {
		super(minecraftServer, connection, commonListenerCookie);
		this.restartClientLoadTimerAfterRespawn();
		this.chunkSender = new PlayerChunkSender(connection.isMemoryConnection());
		this.player = serverPlayer;
		serverPlayer.connection = this;
		serverPlayer.getTextFilter().join();
		this.signedMessageDecoder = SignedMessageChain.Decoder.unsigned(serverPlayer.getUUID(), minecraftServer::enforceSecureProfile);
		this.chatMessageChain = new FutureChain(minecraftServer);
	}

	@Override
	public void tick() {
		if (this.ackBlockChangesUpTo > -1) {
			this.send(new ClientboundBlockChangedAckPacket(this.ackBlockChangesUpTo));
			this.ackBlockChangesUpTo = -1;
		}

		if (this.server.isPaused() || !this.tickPlayer()) {
			this.keepConnectionAlive();
			this.chatSpamThrottler.tick();
			this.dropSpamThrottler.tick();
			if (this.player.getLastActionTime() > 0L
				&& this.server.playerIdleTimeout() > 0
				&& Util.getMillis() - this.player.getLastActionTime() > TimeUnit.MINUTES.toMillis(this.server.playerIdleTimeout())
				&& !this.player.wonGame) {
				this.disconnect(Component.translatable("multiplayer.disconnect.idling"));
			}
		}
	}

	private boolean tickPlayer() {
		this.resetPosition();
		this.player.xo = this.player.getX();
		this.player.yo = this.player.getY();
		this.player.zo = this.player.getZ();
		this.player.doTick();
		this.player.absSnapTo(this.firstGoodX, this.firstGoodY, this.firstGoodZ, this.player.getYRot(), this.player.getXRot());
		this.tickCount++;
		this.knownMovePacketCount = this.receivedMovePacketCount;
		if (this.clientIsFloating && !this.player.isSleeping() && !this.player.isPassenger() && !this.player.isDeadOrDying()) {
			if (++this.aboveGroundTickCount > this.getMaximumFlyingTicks(this.player)) {
				LOGGER.warn("{} was kicked for floating too long!", this.player.getPlainTextName());
				this.disconnect(Component.translatable("multiplayer.disconnect.flying"));
				return true;
			}
		} else {
			this.clientIsFloating = false;
			this.aboveGroundTickCount = 0;
		}

		this.lastVehicle = this.player.getRootVehicle();
		if (this.lastVehicle != this.player && this.lastVehicle.getControllingPassenger() == this.player) {
			this.vehicleFirstGoodX = this.lastVehicle.getX();
			this.vehicleFirstGoodY = this.lastVehicle.getY();
			this.vehicleFirstGoodZ = this.lastVehicle.getZ();
			this.vehicleLastGoodX = this.lastVehicle.getX();
			this.vehicleLastGoodY = this.lastVehicle.getY();
			this.vehicleLastGoodZ = this.lastVehicle.getZ();
			if (this.clientVehicleIsFloating && this.lastVehicle.getControllingPassenger() == this.player) {
				if (++this.aboveGroundVehicleTickCount > this.getMaximumFlyingTicks(this.lastVehicle)) {
					LOGGER.warn("{} was kicked for floating a vehicle too long!", this.player.getPlainTextName());
					this.disconnect(Component.translatable("multiplayer.disconnect.flying"));
					return true;
				}
			} else {
				this.clientVehicleIsFloating = false;
				this.aboveGroundVehicleTickCount = 0;
			}
		} else {
			this.lastVehicle = null;
			this.clientVehicleIsFloating = false;
			this.aboveGroundVehicleTickCount = 0;
		}

		return false;
	}

	private int getMaximumFlyingTicks(Entity entity) {
		double d = entity.getGravity();
		if (d < 1.0E-5F) {
			return Integer.MAX_VALUE;
		}

		double e = 0.08 / d;
		return Mth.ceil(80.0 * Math.max(e, 1.0));
	}

	public void resetFlyingTicks() {
		this.aboveGroundTickCount = 0;
		this.aboveGroundVehicleTickCount = 0;
	}

	public void resetPosition() {
		this.firstGoodX = this.player.getX();
		this.firstGoodY = this.player.getY();
		this.firstGoodZ = this.player.getZ();
		this.lastGoodX = this.player.getX();
		this.lastGoodY = this.player.getY();
		this.lastGoodZ = this.player.getZ();
	}

	@Override
	public boolean isAcceptingMessages() {
		return this.connection.isConnected() && !this.waitingForSwitchToConfig;
	}

	@Override
	public boolean shouldHandleMessage(Packet<?> packet) {
		return super.shouldHandleMessage(packet)
			? true
			: this.waitingForSwitchToConfig && this.connection.isConnected() && packet instanceof ServerboundConfigurationAcknowledgedPacket;
	}

	@Override
	protected GameProfile playerProfile() {
		return this.player.getGameProfile();
	}

	private <T, R> CompletableFuture<R> filterTextPacket(T object, BiFunction<TextFilter, T, CompletableFuture<R>> biFunction) {
		return biFunction.apply(this.player.getTextFilter(), object).thenApply(objectx -> {
			if (!this.isAcceptingMessages()) {
				LOGGER.debug("Ignoring packet due to disconnection");
				throw new CancellationException("disconnected");
			} else {
				return (R)objectx;
			}
		});
	}

	private CompletableFuture<FilteredText> filterTextPacket(String string) {
		return this.filterTextPacket(string, TextFilter::processStreamMessage);
	}

	private CompletableFuture<List<FilteredText>> filterTextPacket(List<String> list) {
		return this.filterTextPacket(list, TextFilter::processMessageBundle);
	}

	@Override
	public void handlePlayerInput(ServerboundPlayerInputPacket serverboundPlayerInputPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundPlayerInputPacket, this, this.player.level());
		this.player.setLastClientInput(serverboundPlayerInputPacket.input());
		if (this.hasClientLoaded()) {
			this.player.resetLastActionTime();
			this.player.setShiftKeyDown(serverboundPlayerInputPacket.input().shift());
		}
	}

	private static boolean containsInvalidValues(double d, double e, double f, float g, float h) {
		return Double.isNaN(d) || Double.isNaN(e) || Double.isNaN(f) || !Floats.isFinite(h) || !Floats.isFinite(g);
	}

	private static double clampHorizontal(double d) {
		return Mth.clamp(d, -3.0E7, 3.0E7);
	}

	private static double clampVertical(double d) {
		return Mth.clamp(d, -2.0E7, 2.0E7);
	}

	@Override
	public void handleMoveVehicle(ServerboundMoveVehiclePacket serverboundMoveVehiclePacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundMoveVehiclePacket, this, this.player.level());
		if (containsInvalidValues(
			serverboundMoveVehiclePacket.position().x(),
			serverboundMoveVehiclePacket.position().y(),
			serverboundMoveVehiclePacket.position().z(),
			serverboundMoveVehiclePacket.yRot(),
			serverboundMoveVehiclePacket.xRot()
		)) {
			this.disconnect(Component.translatable("multiplayer.disconnect.invalid_vehicle_movement"));
		} else if (!this.updateAwaitingTeleport() && this.hasClientLoaded()) {
			Entity entity = this.player.getRootVehicle();
			if (entity != this.player && entity.getControllingPassenger() == this.player && entity == this.lastVehicle) {
				ServerLevel serverLevel = this.player.level();
				double d = entity.getX();
				double e = entity.getY();
				double f = entity.getZ();
				double g = clampHorizontal(serverboundMoveVehiclePacket.position().x());
				double h = clampVertical(serverboundMoveVehiclePacket.position().y());
				double i = clampHorizontal(serverboundMoveVehiclePacket.position().z());
				float j = Mth.wrapDegrees(serverboundMoveVehiclePacket.yRot());
				float k = Mth.wrapDegrees(serverboundMoveVehiclePacket.xRot());
				double l = g - this.vehicleFirstGoodX;
				double m = h - this.vehicleFirstGoodY;
				double n = i - this.vehicleFirstGoodZ;
				double o = entity.getDeltaMovement().lengthSqr();
				double p = l * l + m * m + n * n;
				if (p - o > 100.0 && !this.isSingleplayerOwner()) {
					LOGGER.warn("{} (vehicle of {}) moved too quickly! {},{},{}", entity.getPlainTextName(), this.player.getPlainTextName(), l, m, n);
					this.send(ClientboundMoveVehiclePacket.fromEntity(entity));
					return;
				}

				AABB aABB = entity.getBoundingBox();
				l = g - this.vehicleLastGoodX;
				m = h - this.vehicleLastGoodY;
				n = i - this.vehicleLastGoodZ;
				boolean bl = entity.verticalCollisionBelow;
				if (entity instanceof LivingEntity livingEntity && livingEntity.onClimbable()) {
					livingEntity.resetFallDistance();
				}

				entity.move(MoverType.PLAYER, new Vec3(l, m, n));
				double q = m;
				l = g - entity.getX();
				m = h - entity.getY();
				if (m > -0.5 || m < 0.5) {
					m = 0.0;
				}

				n = i - entity.getZ();
				p = l * l + m * m + n * n;
				boolean bl2 = false;
				if (p > 0.0625) {
					bl2 = true;
					LOGGER.warn("{} (vehicle of {}) moved wrongly! {}", entity.getPlainTextName(), this.player.getPlainTextName(), Math.sqrt(p));
				}

				if (bl2 && serverLevel.noCollision(entity, aABB) || this.isEntityCollidingWithAnythingNew(serverLevel, entity, aABB, g, h, i)) {
					entity.absSnapTo(d, e, f, j, k);
					this.send(ClientboundMoveVehiclePacket.fromEntity(entity));
					entity.removeLatestMovementRecording();
					return;
				}

				entity.absSnapTo(g, h, i, j, k);
				this.player.level().getChunkSource().move(this.player);
				Vec3 vec3 = new Vec3(entity.getX() - d, entity.getY() - e, entity.getZ() - f);
				this.handlePlayerKnownMovement(vec3);
				entity.setOnGroundWithMovement(serverboundMoveVehiclePacket.onGround(), vec3);
				entity.doCheckFallDamage(vec3.x, vec3.y, vec3.z, serverboundMoveVehiclePacket.onGround());
				this.player.checkMovementStatistics(vec3.x, vec3.y, vec3.z);
				this.clientVehicleIsFloating = q >= -0.03125
					&& !bl
					&& !this.server.allowFlight()
					&& !entity.isFlyingVehicle()
					&& !entity.isNoGravity()
					&& this.noBlocksAround(entity);
				this.vehicleLastGoodX = entity.getX();
				this.vehicleLastGoodY = entity.getY();
				this.vehicleLastGoodZ = entity.getZ();
			}
		}
	}

	private boolean noBlocksAround(Entity entity) {
		return entity.level().getBlockStates(entity.getBoundingBox().inflate(0.0625).expandTowards(0.0, -0.55, 0.0)).allMatch(BlockBehaviour.BlockStateBase::isAir);
	}

	@Override
	public void handleAcceptTeleportPacket(ServerboundAcceptTeleportationPacket serverboundAcceptTeleportationPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundAcceptTeleportationPacket, this, this.player.level());
		if (serverboundAcceptTeleportationPacket.getId() == this.awaitingTeleport) {
			if (this.awaitingPositionFromClient == null) {
				this.disconnect(Component.translatable("multiplayer.disconnect.invalid_player_movement"));
				return;
			}

			this.player
				.absSnapTo(
					this.awaitingPositionFromClient.x, this.awaitingPositionFromClient.y, this.awaitingPositionFromClient.z, this.player.getYRot(), this.player.getXRot()
				);
			this.lastGoodX = this.awaitingPositionFromClient.x;
			this.lastGoodY = this.awaitingPositionFromClient.y;
			this.lastGoodZ = this.awaitingPositionFromClient.z;
			this.player.hasChangedDimension();
			this.awaitingPositionFromClient = null;
		}
	}

	@Override
	public void handleAcceptPlayerLoad(ServerboundPlayerLoadedPacket serverboundPlayerLoadedPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundPlayerLoadedPacket, this, this.player.level());
		this.markClientLoaded();
	}

	@Override
	public void handleRecipeBookSeenRecipePacket(ServerboundRecipeBookSeenRecipePacket serverboundRecipeBookSeenRecipePacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundRecipeBookSeenRecipePacket, this, this.player.level());
		RecipeManager.ServerDisplayInfo serverDisplayInfo = this.server.getRecipeManager().getRecipeFromDisplay(serverboundRecipeBookSeenRecipePacket.recipe());
		if (serverDisplayInfo != null) {
			this.player.getRecipeBook().removeHighlight(serverDisplayInfo.parent().id());
		}
	}

	@Override
	public void handleBundleItemSelectedPacket(ServerboundSelectBundleItemPacket serverboundSelectBundleItemPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundSelectBundleItemPacket, this, this.player.level());
		this.player.containerMenu.setSelectedBundleItemIndex(serverboundSelectBundleItemPacket.slotId(), serverboundSelectBundleItemPacket.selectedItemIndex());
	}

	@Override
	public void handleRecipeBookChangeSettingsPacket(ServerboundRecipeBookChangeSettingsPacket serverboundRecipeBookChangeSettingsPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundRecipeBookChangeSettingsPacket, this, this.player.level());
		this.player
			.getRecipeBook()
			.setBookSetting(
				serverboundRecipeBookChangeSettingsPacket.getBookType(),
				serverboundRecipeBookChangeSettingsPacket.isOpen(),
				serverboundRecipeBookChangeSettingsPacket.isFiltering()
			);
	}

	@Override
	public void handleSeenAdvancements(ServerboundSeenAdvancementsPacket serverboundSeenAdvancementsPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundSeenAdvancementsPacket, this, this.player.level());
		if (serverboundSeenAdvancementsPacket.getAction() == ServerboundSeenAdvancementsPacket.Action.OPENED_TAB) {
			Identifier identifier = Objects.requireNonNull(serverboundSeenAdvancementsPacket.getTab());
			AdvancementHolder advancementHolder = this.server.getAdvancements().get(identifier);
			if (advancementHolder != null) {
				this.player.getAdvancements().setSelectedTab(advancementHolder);
			}
		}
	}

	@Override
	public void handleCustomCommandSuggestions(ServerboundCommandSuggestionPacket serverboundCommandSuggestionPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundCommandSuggestionPacket, this, this.player.level());
		StringReader stringReader = new StringReader(serverboundCommandSuggestionPacket.getCommand());
		if (stringReader.canRead() && stringReader.peek() == '/') {
			stringReader.skip();
		}

		ParseResults<CommandSourceStack> parseResults = this.server.getCommands().getDispatcher().parse(stringReader, this.player.createCommandSourceStack());
		this.server
			.getCommands()
			.getDispatcher()
			.getCompletionSuggestions(parseResults)
			.thenAccept(
				suggestions -> {
					Suggestions suggestions2 = suggestions.getList().size() <= 1000
						? suggestions
						: new Suggestions(suggestions.getRange(), suggestions.getList().subList(0, 1000));
					this.send(new ClientboundCommandSuggestionsPacket(serverboundCommandSuggestionPacket.getId(), suggestions2));
				}
			);
	}

	@Override
	public void handleSetCommandBlock(ServerboundSetCommandBlockPacket serverboundSetCommandBlockPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundSetCommandBlockPacket, this, this.player.level());
		if (!this.player.canUseGameMasterBlocks()) {
			this.player.sendSystemMessage(Component.translatable("advMode.notAllowed"));
		} else {
			BaseCommandBlock baseCommandBlock = null;
			CommandBlockEntity commandBlockEntity = null;
			BlockPos blockPos = serverboundSetCommandBlockPacket.getPos();
			BlockEntity blockEntity = this.player.level().getBlockEntity(blockPos);
			if (blockEntity instanceof CommandBlockEntity commandBlockEntity2) {
				commandBlockEntity = commandBlockEntity2;
				baseCommandBlock = commandBlockEntity.getCommandBlock();
			}

			String string = serverboundSetCommandBlockPacket.getCommand();
			boolean bl = serverboundSetCommandBlockPacket.isTrackOutput();
			if (baseCommandBlock != null) {
				CommandBlockEntity.Mode mode = commandBlockEntity.getMode();
				BlockState blockState = this.player.level().getBlockState(blockPos);
				Direction direction = blockState.getValue(CommandBlock.FACING);

				BlockState blockState2 = switch (serverboundSetCommandBlockPacket.getMode()) {
					case SEQUENCE -> Blocks.CHAIN_COMMAND_BLOCK.defaultBlockState();
					case AUTO -> Blocks.REPEATING_COMMAND_BLOCK.defaultBlockState();
					default -> Blocks.COMMAND_BLOCK.defaultBlockState();
				};
				BlockState blockState3 = blockState2.setValue(CommandBlock.FACING, direction)
					.setValue(CommandBlock.CONDITIONAL, serverboundSetCommandBlockPacket.isConditional());
				if (blockState3 != blockState) {
					this.player.level().setBlock(blockPos, blockState3, 2);
					blockEntity.setBlockState(blockState3);
					this.player.level().getChunkAt(blockPos).setBlockEntity(blockEntity);
				}

				baseCommandBlock.setCommand(string);
				baseCommandBlock.setTrackOutput(bl);
				if (!bl) {
					baseCommandBlock.setLastOutput(null);
				}

				commandBlockEntity.setAutomatic(serverboundSetCommandBlockPacket.isAutomatic());
				if (mode != serverboundSetCommandBlockPacket.getMode()) {
					commandBlockEntity.onModeSwitch();
				}

				if (this.player.level().isCommandBlockEnabled()) {
					baseCommandBlock.onUpdated(this.player.level());
				}

				if (!StringUtil.isNullOrEmpty(string)) {
					this.player
						.sendSystemMessage(
							Component.translatable(this.player.level().isCommandBlockEnabled() ? "advMode.setCommand.success" : "advMode.setCommand.disabled", string)
						);
				}
			}
		}
	}

	@Override
	public void handleSetCommandMinecart(ServerboundSetCommandMinecartPacket serverboundSetCommandMinecartPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundSetCommandMinecartPacket, this, this.player.level());
		if (!this.player.canUseGameMasterBlocks()) {
			this.player.sendSystemMessage(Component.translatable("advMode.notAllowed"));
		} else {
			BaseCommandBlock baseCommandBlock = serverboundSetCommandMinecartPacket.getCommandBlock(this.player.level());
			if (baseCommandBlock != null) {
				String string = serverboundSetCommandMinecartPacket.getCommand();
				baseCommandBlock.setCommand(string);
				baseCommandBlock.setTrackOutput(serverboundSetCommandMinecartPacket.isTrackOutput());
				if (!serverboundSetCommandMinecartPacket.isTrackOutput()) {
					baseCommandBlock.setLastOutput(null);
				}

				boolean bl = this.player.level().isCommandBlockEnabled();
				if (bl) {
					baseCommandBlock.onUpdated(this.player.level());
				}

				if (!StringUtil.isNullOrEmpty(string)) {
					this.player.sendSystemMessage(Component.translatable(bl ? "advMode.setCommand.success" : "advMode.setCommand.disabled", string));
				}
			}
		}
	}

	@Override
	public void handlePickItemFromBlock(ServerboundPickItemFromBlockPacket serverboundPickItemFromBlockPacket) {
		ServerLevel serverLevel = this.player.level();
		PacketUtils.ensureRunningOnSameThread(serverboundPickItemFromBlockPacket, this, serverLevel);
		BlockPos blockPos = serverboundPickItemFromBlockPacket.pos();
		if (this.player.isWithinBlockInteractionRange(blockPos, 1.0)) {
			if (serverLevel.isLoaded(blockPos)) {
				BlockState blockState = serverLevel.getBlockState(blockPos);
				boolean bl = this.player.hasInfiniteMaterials() && serverboundPickItemFromBlockPacket.includeData();
				ItemStack itemStack = blockState.getCloneItemStack(serverLevel, blockPos, bl);
				if (!itemStack.isEmpty()) {
					if (bl) {
						addBlockDataToItem(blockState, serverLevel, blockPos, itemStack);
					}

					this.tryPickItem(itemStack);
				}
			}
		}
	}

	private static void addBlockDataToItem(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, ItemStack itemStack) {
		BlockEntity blockEntity = blockState.hasBlockEntity() ? serverLevel.getBlockEntity(blockPos) : null;
		if (blockEntity != null) {
			try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(blockEntity.problemPath(), LOGGER)) {
				TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, serverLevel.registryAccess());
				blockEntity.saveCustomOnly(tagValueOutput);
				blockEntity.removeComponentsFromTag(tagValueOutput);
				BlockItem.setBlockEntityData(itemStack, blockEntity.getType(), tagValueOutput);
				itemStack.applyComponents(blockEntity.collectComponents());
			}
		}
	}

	@Override
	public void handlePickItemFromEntity(ServerboundPickItemFromEntityPacket serverboundPickItemFromEntityPacket) {
		ServerLevel serverLevel = this.player.level();
		PacketUtils.ensureRunningOnSameThread(serverboundPickItemFromEntityPacket, this, serverLevel);
		Entity entity = serverLevel.getEntityOrPart(serverboundPickItemFromEntityPacket.id());
		if (entity != null && this.player.isWithinEntityInteractionRange(entity, 3.0)) {
			ItemStack itemStack = entity.getPickResult();
			if (itemStack != null && !itemStack.isEmpty()) {
				this.tryPickItem(itemStack);
			}
		}
	}

	private void tryPickItem(ItemStack itemStack) {
		if (itemStack.isItemEnabled(this.player.level().enabledFeatures())) {
			Inventory inventory = this.player.getInventory();
			int i = inventory.findSlotMatchingItem(itemStack);
			if (i != -1) {
				if (Inventory.isHotbarSlot(i)) {
					inventory.setSelectedSlot(i);
				} else {
					inventory.pickSlot(i);
				}
			} else if (this.player.hasInfiniteMaterials()) {
				inventory.addAndPickItem(itemStack);
			}

			this.send(new ClientboundSetHeldSlotPacket(inventory.getSelectedSlot()));
			this.player.inventoryMenu.broadcastChanges();
		}
	}

	@Override
	public void handleRenameItem(ServerboundRenameItemPacket serverboundRenameItemPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundRenameItemPacket, this, this.player.level());
		if (this.player.containerMenu instanceof AnvilMenu anvilMenu) {
			if (!anvilMenu.stillValid(this.player)) {
				LOGGER.debug("Player {} interacted with invalid menu {}", this.player, anvilMenu);
				return;
			}

			anvilMenu.setItemName(serverboundRenameItemPacket.getName());
		}
	}

	@Override
	public void handleSetBeaconPacket(ServerboundSetBeaconPacket serverboundSetBeaconPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundSetBeaconPacket, this, this.player.level());
		if (this.player.containerMenu instanceof BeaconMenu beaconMenu) {
			if (!this.player.containerMenu.stillValid(this.player)) {
				LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
				return;
			}

			beaconMenu.updateEffects(serverboundSetBeaconPacket.primary(), serverboundSetBeaconPacket.secondary());
		}
	}

	@Override
	public void handleSetStructureBlock(ServerboundSetStructureBlockPacket serverboundSetStructureBlockPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundSetStructureBlockPacket, this, this.player.level());
		if (this.player.canUseGameMasterBlocks()) {
			BlockPos blockPos = serverboundSetStructureBlockPacket.getPos();
			BlockState blockState = this.player.level().getBlockState(blockPos);
			if (this.player.level().getBlockEntity(blockPos) instanceof StructureBlockEntity structureBlockEntity) {
				structureBlockEntity.setMode(serverboundSetStructureBlockPacket.getMode());
				structureBlockEntity.setStructureName(serverboundSetStructureBlockPacket.getName());
				structureBlockEntity.setStructurePos(serverboundSetStructureBlockPacket.getOffset());
				structureBlockEntity.setStructureSize(serverboundSetStructureBlockPacket.getSize());
				structureBlockEntity.setMirror(serverboundSetStructureBlockPacket.getMirror());
				structureBlockEntity.setRotation(serverboundSetStructureBlockPacket.getRotation());
				structureBlockEntity.setMetaData(serverboundSetStructureBlockPacket.getData());
				structureBlockEntity.setIgnoreEntities(serverboundSetStructureBlockPacket.isIgnoreEntities());
				structureBlockEntity.setStrict(serverboundSetStructureBlockPacket.isStrict());
				structureBlockEntity.setShowAir(serverboundSetStructureBlockPacket.isShowAir());
				structureBlockEntity.setShowBoundingBox(serverboundSetStructureBlockPacket.isShowBoundingBox());
				structureBlockEntity.setIntegrity(serverboundSetStructureBlockPacket.getIntegrity());
				structureBlockEntity.setSeed(serverboundSetStructureBlockPacket.getSeed());
				if (structureBlockEntity.hasStructureName()) {
					String string = structureBlockEntity.getStructureName();
					if (serverboundSetStructureBlockPacket.getUpdateType() == StructureBlockEntity.UpdateType.SAVE_AREA) {
						if (structureBlockEntity.saveStructure()) {
							this.player.displayClientMessage(Component.translatable("structure_block.save_success", string), false);
						} else {
							this.player.displayClientMessage(Component.translatable("structure_block.save_failure", string), false);
						}
					} else if (serverboundSetStructureBlockPacket.getUpdateType() == StructureBlockEntity.UpdateType.LOAD_AREA) {
						if (!structureBlockEntity.isStructureLoadable()) {
							this.player.displayClientMessage(Component.translatable("structure_block.load_not_found", string), false);
						} else if (structureBlockEntity.placeStructureIfSameSize(this.player.level())) {
							this.player.displayClientMessage(Component.translatable("structure_block.load_success", string), false);
						} else {
							this.player.displayClientMessage(Component.translatable("structure_block.load_prepare", string), false);
						}
					} else if (serverboundSetStructureBlockPacket.getUpdateType() == StructureBlockEntity.UpdateType.SCAN_AREA) {
						if (structureBlockEntity.detectSize()) {
							this.player.displayClientMessage(Component.translatable("structure_block.size_success", string), false);
						} else {
							this.player.displayClientMessage(Component.translatable("structure_block.size_failure"), false);
						}
					}
				} else {
					this.player.displayClientMessage(Component.translatable("structure_block.invalid_structure_name", serverboundSetStructureBlockPacket.getName()), false);
				}

				structureBlockEntity.setChanged();
				this.player.level().sendBlockUpdated(blockPos, blockState, blockState, 3);
			}
		}
	}

	@Override
	public void handleSetTestBlock(ServerboundSetTestBlockPacket serverboundSetTestBlockPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundSetTestBlockPacket, this, this.player.level());
		if (this.player.canUseGameMasterBlocks()) {
			BlockPos blockPos = serverboundSetTestBlockPacket.position();
			BlockState blockState = this.player.level().getBlockState(blockPos);
			if (this.player.level().getBlockEntity(blockPos) instanceof TestBlockEntity testBlockEntity) {
				testBlockEntity.setMode(serverboundSetTestBlockPacket.mode());
				testBlockEntity.setMessage(serverboundSetTestBlockPacket.message());
				testBlockEntity.setChanged();
				this.player.level().sendBlockUpdated(blockPos, blockState, testBlockEntity.getBlockState(), 3);
			}
		}
	}

	@Override
	public void handleTestInstanceBlockAction(ServerboundTestInstanceBlockActionPacket serverboundTestInstanceBlockActionPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundTestInstanceBlockActionPacket, this, this.player.level());
		BlockPos blockPos = serverboundTestInstanceBlockActionPacket.pos();
		if (this.player.canUseGameMasterBlocks() && this.player.level().getBlockEntity(blockPos) instanceof TestInstanceBlockEntity testInstanceBlockEntity) {
			if (serverboundTestInstanceBlockActionPacket.action() != ServerboundTestInstanceBlockActionPacket.Action.QUERY
				&& serverboundTestInstanceBlockActionPacket.action() != ServerboundTestInstanceBlockActionPacket.Action.INIT) {
				testInstanceBlockEntity.set(serverboundTestInstanceBlockActionPacket.data());
				if (serverboundTestInstanceBlockActionPacket.action() == ServerboundTestInstanceBlockActionPacket.Action.RESET) {
					testInstanceBlockEntity.resetTest(this.player::sendSystemMessage);
				} else if (serverboundTestInstanceBlockActionPacket.action() == ServerboundTestInstanceBlockActionPacket.Action.SAVE) {
					testInstanceBlockEntity.saveTest(this.player::sendSystemMessage);
				} else if (serverboundTestInstanceBlockActionPacket.action() == ServerboundTestInstanceBlockActionPacket.Action.EXPORT) {
					testInstanceBlockEntity.exportTest(this.player::sendSystemMessage);
				} else if (serverboundTestInstanceBlockActionPacket.action() == ServerboundTestInstanceBlockActionPacket.Action.RUN) {
					testInstanceBlockEntity.runTest(this.player::sendSystemMessage);
				}

				BlockState blockState = this.player.level().getBlockState(blockPos);
				this.player.level().sendBlockUpdated(blockPos, Blocks.AIR.defaultBlockState(), blockState, 3);
			} else {
				Registry<GameTestInstance> registry = this.player.registryAccess().lookupOrThrow(Registries.TEST_INSTANCE);
				Optional<Holder.Reference<GameTestInstance>> optional = serverboundTestInstanceBlockActionPacket.data().test().flatMap(registry::get);
				Component component;
				if (optional.isPresent()) {
					component = optional.get().value().describe();
				} else {
					component = Component.translatable("test_instance.description.no_test").withStyle(ChatFormatting.RED);
				}

				Optional<Vec3i> optional2;
				if (serverboundTestInstanceBlockActionPacket.action() == ServerboundTestInstanceBlockActionPacket.Action.QUERY) {
					optional2 = serverboundTestInstanceBlockActionPacket.data()
						.test()
						.flatMap(resourceKey -> TestInstanceBlockEntity.getStructureSize(this.player.level(), (ResourceKey<GameTestInstance>)resourceKey));
				} else {
					optional2 = Optional.empty();
				}

				this.connection.send(new ClientboundTestInstanceBlockStatus(component, optional2));
			}
		}
	}

	@Override
	public void handleSetJigsawBlock(ServerboundSetJigsawBlockPacket serverboundSetJigsawBlockPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundSetJigsawBlockPacket, this, this.player.level());
		if (this.player.canUseGameMasterBlocks()) {
			BlockPos blockPos = serverboundSetJigsawBlockPacket.getPos();
			BlockState blockState = this.player.level().getBlockState(blockPos);
			if (this.player.level().getBlockEntity(blockPos) instanceof JigsawBlockEntity jigsawBlockEntity) {
				jigsawBlockEntity.setName(serverboundSetJigsawBlockPacket.getName());
				jigsawBlockEntity.setTarget(serverboundSetJigsawBlockPacket.getTarget());
				jigsawBlockEntity.setPool(ResourceKey.create(Registries.TEMPLATE_POOL, serverboundSetJigsawBlockPacket.getPool()));
				jigsawBlockEntity.setFinalState(serverboundSetJigsawBlockPacket.getFinalState());
				jigsawBlockEntity.setJoint(serverboundSetJigsawBlockPacket.getJoint());
				jigsawBlockEntity.setPlacementPriority(serverboundSetJigsawBlockPacket.getPlacementPriority());
				jigsawBlockEntity.setSelectionPriority(serverboundSetJigsawBlockPacket.getSelectionPriority());
				jigsawBlockEntity.setChanged();
				this.player.level().sendBlockUpdated(blockPos, blockState, blockState, 3);
			}
		}
	}

	@Override
	public void handleJigsawGenerate(ServerboundJigsawGeneratePacket serverboundJigsawGeneratePacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundJigsawGeneratePacket, this, this.player.level());
		if (this.player.canUseGameMasterBlocks()) {
			BlockPos blockPos = serverboundJigsawGeneratePacket.getPos();
			if (this.player.level().getBlockEntity(blockPos) instanceof JigsawBlockEntity jigsawBlockEntity) {
				jigsawBlockEntity.generate(this.player.level(), serverboundJigsawGeneratePacket.levels(), serverboundJigsawGeneratePacket.keepJigsaws());
			}
		}
	}

	@Override
	public void handleSelectTrade(ServerboundSelectTradePacket serverboundSelectTradePacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundSelectTradePacket, this, this.player.level());
		int i = serverboundSelectTradePacket.getItem();
		if (this.player.containerMenu instanceof MerchantMenu merchantMenu) {
			if (!merchantMenu.stillValid(this.player)) {
				LOGGER.debug("Player {} interacted with invalid menu {}", this.player, merchantMenu);
				return;
			}

			merchantMenu.setSelectionHint(i);
			merchantMenu.tryMoveItems(i);
		}
	}

	@Override
	public void handleEditBook(ServerboundEditBookPacket serverboundEditBookPacket) {
		int i = serverboundEditBookPacket.slot();
		if (Inventory.isHotbarSlot(i) || i == 40) {
			List<String> list = Lists.newArrayList();
			Optional<String> optional = serverboundEditBookPacket.title();
			optional.ifPresent(list::add);
			list.addAll(serverboundEditBookPacket.pages());
			Consumer<List<FilteredText>> consumer = optional.isPresent()
				? listx -> this.signBook((FilteredText)listx.get(0), listx.subList(1, listx.size()), i)
				: listx -> this.updateBookContents(listx, i);
			this.filterTextPacket(list).thenAcceptAsync(consumer, this.server);
		}
	}

	private void updateBookContents(List<FilteredText> list, int i) {
		ItemStack itemStack = this.player.getInventory().getItem(i);
		if (itemStack.has(DataComponents.WRITABLE_BOOK_CONTENT)) {
			List<Filterable<String>> list2 = list.stream().map(this::filterableFromOutgoing).toList();
			itemStack.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(list2));
		}
	}

	private void signBook(FilteredText filteredText, List<FilteredText> list, int i) {
		ItemStack itemStack = this.player.getInventory().getItem(i);
		if (itemStack.has(DataComponents.WRITABLE_BOOK_CONTENT)) {
			ItemStack itemStack2 = itemStack.transmuteCopy(Items.WRITTEN_BOOK);
			itemStack2.remove(DataComponents.WRITABLE_BOOK_CONTENT);
			List<Filterable<Component>> list2 = list.stream().map(filteredTextx -> this.filterableFromOutgoing(filteredTextx).map(Component::literal)).toList();
			itemStack2.set(
				DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(this.filterableFromOutgoing(filteredText), this.player.getPlainTextName(), 0, list2, true)
			);
			this.player.getInventory().setItem(i, itemStack2);
		}
	}

	private Filterable<String> filterableFromOutgoing(FilteredText filteredText) {
		return this.player.isTextFilteringEnabled() ? Filterable.passThrough(filteredText.filteredOrEmpty()) : Filterable.from(filteredText);
	}

	@Override
	public void handleEntityTagQuery(ServerboundEntityTagQueryPacket serverboundEntityTagQueryPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundEntityTagQueryPacket, this, this.player.level());
		if (this.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
			Entity entity = this.player.level().getEntity(serverboundEntityTagQueryPacket.getEntityId());
			if (entity != null) {
				try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
					TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, entity.registryAccess());
					entity.saveWithoutId(tagValueOutput);
					CompoundTag compoundTag = tagValueOutput.buildResult();
					this.send(new ClientboundTagQueryPacket(serverboundEntityTagQueryPacket.getTransactionId(), compoundTag));
				}
			}
		}
	}

	@Override
	public void handleContainerSlotStateChanged(ServerboundContainerSlotStateChangedPacket serverboundContainerSlotStateChangedPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundContainerSlotStateChangedPacket, this, this.player.level());
		if (!this.player.isSpectator() && serverboundContainerSlotStateChangedPacket.containerId() == this.player.containerMenu.containerId) {
			if (this.player.containerMenu instanceof CrafterMenu crafterMenu && crafterMenu.getContainer() instanceof CrafterBlockEntity crafterBlockEntity) {
				crafterBlockEntity.setSlotState(serverboundContainerSlotStateChangedPacket.slotId(), serverboundContainerSlotStateChangedPacket.newState());
			}
		}
	}

	@Override
	public void handleBlockEntityTagQuery(ServerboundBlockEntityTagQueryPacket serverboundBlockEntityTagQueryPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundBlockEntityTagQueryPacket, this, this.player.level());
		if (this.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
			BlockEntity blockEntity = this.player.level().getBlockEntity(serverboundBlockEntityTagQueryPacket.getPos());
			CompoundTag compoundTag = blockEntity != null ? blockEntity.saveWithoutMetadata(this.player.registryAccess()) : null;
			this.send(new ClientboundTagQueryPacket(serverboundBlockEntityTagQueryPacket.getTransactionId(), compoundTag));
		}
	}

	@Override
	public void handleMovePlayer(ServerboundMovePlayerPacket serverboundMovePlayerPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundMovePlayerPacket, this, this.player.level());
		if (containsInvalidValues(
			serverboundMovePlayerPacket.getX(0.0),
			serverboundMovePlayerPacket.getY(0.0),
			serverboundMovePlayerPacket.getZ(0.0),
			serverboundMovePlayerPacket.getYRot(0.0F),
			serverboundMovePlayerPacket.getXRot(0.0F)
		)) {
			this.disconnect(Component.translatable("multiplayer.disconnect.invalid_player_movement"));
		} else {
			ServerLevel serverLevel = this.player.level();
			if (!this.player.wonGame) {
				if (this.tickCount == 0) {
					this.resetPosition();
				}

				if (this.hasClientLoaded()) {
					float f = Mth.wrapDegrees(serverboundMovePlayerPacket.getYRot(this.player.getYRot()));
					float g = Mth.wrapDegrees(serverboundMovePlayerPacket.getXRot(this.player.getXRot()));
					if (this.updateAwaitingTeleport()) {
						this.player.absSnapRotationTo(f, g);
					} else {
						double d = clampHorizontal(serverboundMovePlayerPacket.getX(this.player.getX()));
						double e = clampVertical(serverboundMovePlayerPacket.getY(this.player.getY()));
						double h = clampHorizontal(serverboundMovePlayerPacket.getZ(this.player.getZ()));
						if (this.player.isPassenger()) {
							this.player.absSnapTo(this.player.getX(), this.player.getY(), this.player.getZ(), f, g);
							this.player.level().getChunkSource().move(this.player);
						} else {
							double i = this.player.getX();
							double j = this.player.getY();
							double k = this.player.getZ();
							double l = d - this.firstGoodX;
							double m = e - this.firstGoodY;
							double n = h - this.firstGoodZ;
							double o = this.player.getDeltaMovement().lengthSqr();
							double p = l * l + m * m + n * n;
							if (this.player.isSleeping()) {
								if (p > 1.0) {
									this.teleport(this.player.getX(), this.player.getY(), this.player.getZ(), f, g);
								}
							} else {
								boolean bl = this.player.isFallFlying();
								if (serverLevel.tickRateManager().runsNormally()) {
									this.receivedMovePacketCount++;
									int q = this.receivedMovePacketCount - this.knownMovePacketCount;
									if (q > 5) {
										LOGGER.debug("{} is sending move packets too frequently ({} packets since last tick)", this.player.getPlainTextName(), q);
										q = 1;
									}

									if (this.shouldCheckPlayerMovement(bl)) {
										float r = bl ? 300.0F : 100.0F;
										if (p - o > r * q) {
											LOGGER.warn("{} moved too quickly! {},{},{}", this.player.getPlainTextName(), l, m, n);
											this.teleport(this.player.getX(), this.player.getY(), this.player.getZ(), this.player.getYRot(), this.player.getXRot());
											return;
										}
									}
								}

								AABB aABB = this.player.getBoundingBox();
								l = d - this.lastGoodX;
								m = e - this.lastGoodY;
								n = h - this.lastGoodZ;
								boolean bl2 = m > 0.0;
								if (this.player.onGround() && !serverboundMovePlayerPacket.isOnGround() && bl2) {
									this.player.jumpFromGround();
								}

								boolean bl3 = this.player.verticalCollisionBelow;
								this.player.move(MoverType.PLAYER, new Vec3(l, m, n));
								double s = m;
								l = d - this.player.getX();
								m = e - this.player.getY();
								if (m > -0.5 || m < 0.5) {
									m = 0.0;
								}

								n = h - this.player.getZ();
								p = l * l + m * m + n * n;
								boolean bl4 = false;
								if (!this.player.isChangingDimension()
									&& p > 0.0625
									&& !this.player.isSleeping()
									&& !this.player.isCreative()
									&& !this.player.isSpectator()
									&& !this.player.isInPostImpulseGraceTime()) {
									bl4 = true;
									LOGGER.warn("{} moved wrongly!", this.player.getPlainTextName());
								}

								if (this.player.noPhysics
									|| this.player.isSleeping()
									|| (!bl4 || !serverLevel.noCollision(this.player, aABB)) && !this.isEntityCollidingWithAnythingNew(serverLevel, this.player, aABB, d, e, h)) {
									this.player.absSnapTo(d, e, h, f, g);
									boolean bl5 = this.player.isAutoSpinAttack();
									this.clientIsFloating = s >= -0.03125
										&& !bl3
										&& !this.player.isSpectator()
										&& !this.server.allowFlight()
										&& !this.player.getAbilities().mayfly
										&& !this.player.hasEffect(MobEffects.LEVITATION)
										&& !bl
										&& !bl5
										&& this.noBlocksAround(this.player);
									this.player.level().getChunkSource().move(this.player);
									Vec3 vec3 = new Vec3(this.player.getX() - i, this.player.getY() - j, this.player.getZ() - k);
									this.player.setOnGroundWithMovement(serverboundMovePlayerPacket.isOnGround(), serverboundMovePlayerPacket.horizontalCollision(), vec3);
									this.player.doCheckFallDamage(vec3.x, vec3.y, vec3.z, serverboundMovePlayerPacket.isOnGround());
									this.handlePlayerKnownMovement(vec3);
									if (bl2) {
										this.player.resetFallDistance();
									}

									if (serverboundMovePlayerPacket.isOnGround() || this.player.hasLandedInLiquid() || this.player.onClimbable() || this.player.isSpectator() || bl || bl5
										)
									 {
										this.player.tryResetCurrentImpulseContext();
									}

									this.player.checkMovementStatistics(this.player.getX() - i, this.player.getY() - j, this.player.getZ() - k);
									this.lastGoodX = this.player.getX();
									this.lastGoodY = this.player.getY();
									this.lastGoodZ = this.player.getZ();
								} else {
									this.teleport(i, j, k, f, g);
									this.player.doCheckFallDamage(this.player.getX() - i, this.player.getY() - j, this.player.getZ() - k, serverboundMovePlayerPacket.isOnGround());
									this.player.removeLatestMovementRecording();
								}
							}
						}
					}
				}
			}
		}
	}

	private boolean shouldCheckPlayerMovement(boolean bl) {
		if (this.isSingleplayerOwner()) {
			return false;
		}

		if (this.player.isChangingDimension()) {
			return false;
		}

		GameRules gameRules = this.player.level().getGameRules();
		return !gameRules.get(GameRules.PLAYER_MOVEMENT_CHECK) ? false : !bl || gameRules.get(GameRules.ELYTRA_MOVEMENT_CHECK);
	}

	private boolean updateAwaitingTeleport() {
		if (this.awaitingPositionFromClient != null) {
			if (this.tickCount - this.awaitingTeleportTime > 20) {
				this.awaitingTeleportTime = this.tickCount;
				this.teleport(
					this.awaitingPositionFromClient.x, this.awaitingPositionFromClient.y, this.awaitingPositionFromClient.z, this.player.getYRot(), this.player.getXRot()
				);
			}

			return true;
		} else {
			this.awaitingTeleportTime = this.tickCount;
			return false;
		}
	}

	private boolean isEntityCollidingWithAnythingNew(LevelReader levelReader, Entity entity, AABB aABB, double d, double e, double f) {
		AABB aABB2 = entity.getBoundingBox().move(d - entity.getX(), e - entity.getY(), f - entity.getZ());
		Iterable<VoxelShape> iterable = levelReader.getPreMoveCollisions(entity, aABB2.deflate(1.0E-5F), aABB.getBottomCenter());
		VoxelShape voxelShape = Shapes.create(aABB.deflate(1.0E-5F));

		for (VoxelShape voxelShape2 : iterable) {
			if (!Shapes.joinIsNotEmpty(voxelShape2, voxelShape, BooleanOp.AND)) {
				return true;
			}
		}

		return false;
	}

	public void teleport(double d, double e, double f, float g, float h) {
		this.teleport(new PositionMoveRotation(new Vec3(d, e, f), Vec3.ZERO, g, h), Collections.emptySet());
	}

	public void teleport(PositionMoveRotation positionMoveRotation, Set<Relative> set) {
		this.awaitingTeleportTime = this.tickCount;
		if (++this.awaitingTeleport == Integer.MAX_VALUE) {
			this.awaitingTeleport = 0;
		}

		this.player.teleportSetPosition(positionMoveRotation, set);
		this.awaitingPositionFromClient = this.player.position();
		this.send(ClientboundPlayerPositionPacket.of(this.awaitingTeleport, positionMoveRotation, set));
	}

	@Override
	public void handlePlayerAction(ServerboundPlayerActionPacket serverboundPlayerActionPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundPlayerActionPacket, this, this.player.level());
		if (this.hasClientLoaded()) {
			BlockPos blockPos = serverboundPlayerActionPacket.getPos();
			this.player.resetLastActionTime();
			ServerboundPlayerActionPacket.Action action = serverboundPlayerActionPacket.getAction();
			switch (action) {
				case STAB:
					if (this.player.isSpectator()) {
						return;
					} else {
						ItemStack itemStack = this.player.getItemInHand(InteractionHand.MAIN_HAND);
						if (this.player.cannotAttackWithItem(itemStack, 5)) {
							return;
						}

						PiercingWeapon piercingWeapon = itemStack.get(DataComponents.PIERCING_WEAPON);
						if (piercingWeapon != null) {
							piercingWeapon.attack(this.player, EquipmentSlot.MAINHAND);
						}

						return;
					}
				case SWAP_ITEM_WITH_OFFHAND:
					if (!this.player.isSpectator()) {
						ItemStack itemStack2 = this.player.getItemInHand(InteractionHand.OFF_HAND);
						this.player.setItemInHand(InteractionHand.OFF_HAND, this.player.getItemInHand(InteractionHand.MAIN_HAND));
						this.player.setItemInHand(InteractionHand.MAIN_HAND, itemStack2);
						this.player.stopUsingItem();
					}

					return;
				case DROP_ITEM:
					if (!this.player.isSpectator()) {
						this.player.drop(false);
					}

					return;
				case DROP_ALL_ITEMS:
					if (!this.player.isSpectator()) {
						this.player.drop(true);
					}

					return;
				case RELEASE_USE_ITEM:
					this.player.releaseUsingItem();
					return;
				case START_DESTROY_BLOCK:
				case ABORT_DESTROY_BLOCK:
				case STOP_DESTROY_BLOCK:
					this.player
						.gameMode
						.handleBlockBreakAction(
							blockPos, action, serverboundPlayerActionPacket.getDirection(), this.player.level().getMaxY(), serverboundPlayerActionPacket.getSequence()
						);
					this.ackBlockChangesUpTo(serverboundPlayerActionPacket.getSequence());
					return;
				default:
					throw new IllegalArgumentException("Invalid player action");
			}
		}
	}

	private static boolean wasBlockPlacementAttempt(ServerPlayer serverPlayer, ItemStack itemStack) {
		if (itemStack.isEmpty()) {
			return false;
		}

		Item item = itemStack.getItem();
		return (item instanceof BlockItem || item instanceof BucketItem bucketItem && bucketItem.getContent() != Fluids.EMPTY)
			&& !serverPlayer.getCooldowns().isOnCooldown(itemStack);
	}

	@Override
	public void handleUseItemOn(ServerboundUseItemOnPacket serverboundUseItemOnPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundUseItemOnPacket, this, this.player.level());
		if (this.hasClientLoaded()) {
			this.ackBlockChangesUpTo(serverboundUseItemOnPacket.getSequence());
			ServerLevel serverLevel = this.player.level();
			InteractionHand interactionHand = serverboundUseItemOnPacket.getHand();
			ItemStack itemStack = this.player.getItemInHand(interactionHand);
			if (itemStack.isItemEnabled(serverLevel.enabledFeatures())) {
				BlockHitResult blockHitResult = serverboundUseItemOnPacket.getHitResult();
				Vec3 vec3 = blockHitResult.getLocation();
				BlockPos blockPos = blockHitResult.getBlockPos();
				if (this.player.isWithinBlockInteractionRange(blockPos, 1.0)) {
					Vec3 vec32 = vec3.subtract(Vec3.atCenterOf(blockPos));
					double d = 1.0000001;
					if (Math.abs(vec32.x()) < 1.0000001 && Math.abs(vec32.y()) < 1.0000001 && Math.abs(vec32.z()) < 1.0000001) {
						Direction direction = blockHitResult.getDirection();
						this.player.resetLastActionTime();
						int i = this.player.level().getMaxY();
						if (blockPos.getY() <= i) {
							if (this.awaitingPositionFromClient == null && serverLevel.mayInteract(this.player, blockPos)) {
								InteractionResult interactionResult = this.player.gameMode.useItemOn(this.player, serverLevel, itemStack, interactionHand, blockHitResult);
								if (interactionResult.consumesAction()) {
									CriteriaTriggers.ANY_BLOCK_USE.trigger(this.player, blockHitResult.getBlockPos(), itemStack.copy());
								}

								if (direction == Direction.UP && !interactionResult.consumesAction() && blockPos.getY() >= i && wasBlockPlacementAttempt(this.player, itemStack)) {
									Component component = Component.translatable("build.tooHigh", i).withStyle(ChatFormatting.RED);
									this.player.sendSystemMessage(component, true);
								} else if (interactionResult instanceof InteractionResult.Success success && success.swingSource() == InteractionResult.SwingSource.SERVER) {
									this.player.swing(interactionHand, true);
								}
							}
						} else {
							Component component2 = Component.translatable("build.tooHigh", i).withStyle(ChatFormatting.RED);
							this.player.sendSystemMessage(component2, true);
						}

						this.send(new ClientboundBlockUpdatePacket(serverLevel, blockPos));
						this.send(new ClientboundBlockUpdatePacket(serverLevel, blockPos.relative(direction)));
					} else {
						LOGGER.warn("Rejecting UseItemOnPacket from {}: Location {} too far away from hit block {}.", this.player.getGameProfile().name(), vec3, blockPos);
					}
				}
			}
		}
	}

	@Override
	public void handleUseItem(ServerboundUseItemPacket serverboundUseItemPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundUseItemPacket, this, this.player.level());
		if (this.hasClientLoaded()) {
			this.ackBlockChangesUpTo(serverboundUseItemPacket.getSequence());
			ServerLevel serverLevel = this.player.level();
			InteractionHand interactionHand = serverboundUseItemPacket.getHand();
			ItemStack itemStack = this.player.getItemInHand(interactionHand);
			this.player.resetLastActionTime();
			if (!itemStack.isEmpty() && itemStack.isItemEnabled(serverLevel.enabledFeatures())) {
				float f = Mth.wrapDegrees(serverboundUseItemPacket.getYRot());
				float g = Mth.wrapDegrees(serverboundUseItemPacket.getXRot());
				if (g != this.player.getXRot() || f != this.player.getYRot()) {
					this.player.absSnapRotationTo(f, g);
				}

				if (this.player.gameMode.useItem(this.player, serverLevel, itemStack, interactionHand) instanceof InteractionResult.Success success
					&& success.swingSource() == InteractionResult.SwingSource.SERVER) {
					this.player.swing(interactionHand, true);
				}
			}
		}
	}

	@Override
	public void handleTeleportToEntityPacket(ServerboundTeleportToEntityPacket serverboundTeleportToEntityPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundTeleportToEntityPacket, this, this.player.level());
		if (this.player.isSpectator()) {
			for (ServerLevel serverLevel : this.server.getAllLevels()) {
				Entity entity = serverboundTeleportToEntityPacket.getEntity(serverLevel);
				if (entity != null) {
					this.player.teleportTo(serverLevel, entity.getX(), entity.getY(), entity.getZ(), Set.of(), entity.getYRot(), entity.getXRot(), true);
					return;
				}
			}
		}
	}

	@Override
	public void handlePaddleBoat(ServerboundPaddleBoatPacket serverboundPaddleBoatPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundPaddleBoatPacket, this, this.player.level());
		if (this.player.getControlledVehicle() instanceof AbstractBoat abstractBoat) {
			abstractBoat.setPaddleState(serverboundPaddleBoatPacket.getLeft(), serverboundPaddleBoatPacket.getRight());
		}
	}

	@Override
	public void onDisconnect(DisconnectionDetails disconnectionDetails) {
		LOGGER.info("{} lost connection: {}", this.player.getPlainTextName(), disconnectionDetails.reason().getString());
		this.removePlayerFromWorld();
		super.onDisconnect(disconnectionDetails);
	}

	private void removePlayerFromWorld() {
		this.chatMessageChain.close();
		this.server.invalidateStatus();
		this.server
			.getPlayerList()
			.broadcastSystemMessage(Component.translatable("multiplayer.player.left", this.player.getDisplayName()).withStyle(ChatFormatting.YELLOW), false);
		this.player.disconnect();
		this.server.getPlayerList().remove(this.player);
		this.player.getTextFilter().leave();
	}

	public void ackBlockChangesUpTo(int i) {
		if (i < 0) {
			throw new IllegalArgumentException("Expected packet sequence nr >= 0");
		}

		this.ackBlockChangesUpTo = Math.max(i, this.ackBlockChangesUpTo);
	}

	@Override
	public void handleSetCarriedItem(ServerboundSetCarriedItemPacket serverboundSetCarriedItemPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundSetCarriedItemPacket, this, this.player.level());
		if (serverboundSetCarriedItemPacket.getSlot() >= 0 && serverboundSetCarriedItemPacket.getSlot() < Inventory.getSelectionSize()) {
			if (this.player.getInventory().getSelectedSlot() != serverboundSetCarriedItemPacket.getSlot() && this.player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
				this.player.stopUsingItem();
			}

			this.player.getInventory().setSelectedSlot(serverboundSetCarriedItemPacket.getSlot());
			this.player.resetLastActionTime();
		} else {
			LOGGER.warn("{} tried to set an invalid carried item", this.player.getPlainTextName());
		}
	}

	@Override
	public void handleChat(ServerboundChatPacket serverboundChatPacket) {
		Optional<LastSeenMessages> optional = this.unpackAndApplyLastSeen(serverboundChatPacket.lastSeenMessages());
		if (!optional.isEmpty()) {
			this.tryHandleChat(serverboundChatPacket.message(), false, () -> {
				PlayerChatMessage playerChatMessage;
				try {
					playerChatMessage = this.getSignedMessage(serverboundChatPacket, optional.get());
				} catch (SignedMessageChain.DecodeException decodeException) {
					this.handleMessageDecodeFailure(decodeException);
					return;
				}

				CompletableFuture<FilteredText> completableFuture = this.filterTextPacket(playerChatMessage.signedContent());
				Component component = this.server.getChatDecorator().decorate(this.player, playerChatMessage.decoratedContent());
				this.chatMessageChain.append(completableFuture, filteredText -> {
					PlayerChatMessage playerChatMessage2 = playerChatMessage.withUnsignedContent(component).filter(filteredText.mask());
					this.broadcastChatMessage(playerChatMessage2);
				});
			});
		}
	}

	@Override
	public void handleChatCommand(ServerboundChatCommandPacket serverboundChatCommandPacket) {
		this.tryHandleChat(serverboundChatCommandPacket.command(), true, () -> {
			this.performUnsignedChatCommand(serverboundChatCommandPacket.command());
			this.detectRateSpam();
		});
	}

	private void performUnsignedChatCommand(String string) {
		ParseResults<CommandSourceStack> parseResults = this.parseCommand(string);
		if (this.server.enforceSecureProfile() && SignableCommand.hasSignableArguments(parseResults)) {
			LOGGER.error("Received unsigned command packet from {}, but the command requires signable arguments: {}", this.player.getGameProfile().name(), string);
			this.player.sendSystemMessage(INVALID_COMMAND_SIGNATURE);
		} else {
			this.server.getCommands().performCommand(parseResults, string);
		}
	}

	@Override
	public void handleSignedChatCommand(ServerboundChatCommandSignedPacket serverboundChatCommandSignedPacket) {
		Optional<LastSeenMessages> optional = this.unpackAndApplyLastSeen(serverboundChatCommandSignedPacket.lastSeenMessages());
		if (!optional.isEmpty()) {
			this.tryHandleChat(serverboundChatCommandSignedPacket.command(), true, () -> {
				this.performSignedChatCommand(serverboundChatCommandSignedPacket, optional.get());
				this.detectRateSpam();
			});
		}
	}

	private void performSignedChatCommand(ServerboundChatCommandSignedPacket serverboundChatCommandSignedPacket, LastSeenMessages lastSeenMessages) {
		ParseResults<CommandSourceStack> parseResults = this.parseCommand(serverboundChatCommandSignedPacket.command());

		Map<String, PlayerChatMessage> map;
		try {
			map = this.collectSignedArguments(serverboundChatCommandSignedPacket, SignableCommand.of(parseResults), lastSeenMessages);
		} catch (SignedMessageChain.DecodeException decodeException) {
			this.handleMessageDecodeFailure(decodeException);
			return;
		}

		CommandSigningContext commandSigningContext = new CommandSigningContext.SignedArguments(map);
		parseResults = Commands.mapSource(parseResults, commandSourceStack -> commandSourceStack.withSigningContext(commandSigningContext, this.chatMessageChain));
		this.server.getCommands().performCommand(parseResults, serverboundChatCommandSignedPacket.command());
	}

	private void handleMessageDecodeFailure(SignedMessageChain.DecodeException decodeException) {
		LOGGER.warn("Failed to update secure chat state for {}: '{}'", this.player.getGameProfile().name(), decodeException.getComponent().getString());
		this.player.sendSystemMessage(decodeException.getComponent().copy().withStyle(ChatFormatting.RED));
	}

	private <S> Map<String, PlayerChatMessage> collectSignedArguments(
		ServerboundChatCommandSignedPacket serverboundChatCommandSignedPacket, SignableCommand<S> signableCommand, LastSeenMessages lastSeenMessages
	) throws SignedMessageChain.DecodeException {
		List<ArgumentSignatures.Entry> list = serverboundChatCommandSignedPacket.argumentSignatures().entries();
		List<SignableCommand.Argument<S>> list2 = signableCommand.arguments();
		if (list.isEmpty()) {
			return this.collectUnsignedArguments(list2);
		}

		Map<String, PlayerChatMessage> map = new Object2ObjectOpenHashMap<>();

		for (ArgumentSignatures.Entry entry : list) {
			SignableCommand.Argument<S> argument = signableCommand.getArgument(entry.name());
			if (argument == null) {
				this.signedMessageDecoder.setChainBroken();
				throw createSignedArgumentMismatchException(serverboundChatCommandSignedPacket.command(), list, list2);
			}

			SignedMessageBody signedMessageBody = new SignedMessageBody(
				argument.value(), serverboundChatCommandSignedPacket.timeStamp(), serverboundChatCommandSignedPacket.salt(), lastSeenMessages
			);
			map.put(argument.name(), this.signedMessageDecoder.unpack(entry.signature(), signedMessageBody));
		}

		for (SignableCommand.Argument<S> argument2 : list2) {
			if (!map.containsKey(argument2.name())) {
				throw createSignedArgumentMismatchException(serverboundChatCommandSignedPacket.command(), list, list2);
			}
		}

		return map;
	}

	private <S> Map<String, PlayerChatMessage> collectUnsignedArguments(List<SignableCommand.Argument<S>> list) throws SignedMessageChain.DecodeException {
		Map<String, PlayerChatMessage> map = new HashMap<>();

		for (SignableCommand.Argument<S> argument : list) {
			SignedMessageBody signedMessageBody = SignedMessageBody.unsigned(argument.value());
			map.put(argument.name(), this.signedMessageDecoder.unpack(null, signedMessageBody));
		}

		return map;
	}

	private static <S> SignedMessageChain.DecodeException createSignedArgumentMismatchException(
		String string, List<ArgumentSignatures.Entry> list, List<SignableCommand.Argument<S>> list2
	) {
		String string2 = list.stream().map(ArgumentSignatures.Entry::name).collect(Collectors.joining(", "));
		String string3 = list2.stream().map(SignableCommand.Argument::name).collect(Collectors.joining(", "));
		LOGGER.error("Signed command mismatch between server and client ('{}'): got [{}] from client, but expected [{}]", string, string2, string3);
		return new SignedMessageChain.DecodeException(INVALID_COMMAND_SIGNATURE);
	}

	private ParseResults<CommandSourceStack> parseCommand(String string) {
		CommandDispatcher<CommandSourceStack> commandDispatcher = this.server.getCommands().getDispatcher();
		return commandDispatcher.parse(string, this.player.createCommandSourceStack());
	}

	private void tryHandleChat(String string, boolean bl, Runnable runnable) {
		if (isChatMessageIllegal(string)) {
			this.disconnect(Component.translatable("multiplayer.disconnect.illegal_characters"));
		} else if (!bl && this.player.getChatVisibility() == ChatVisiblity.HIDDEN) {
			this.send(new ClientboundSystemChatPacket(Component.translatable("chat.disabled.options").withStyle(ChatFormatting.RED), false));
		} else {
			this.player.resetLastActionTime();
			this.server.execute(runnable);
		}
	}

	private Optional<LastSeenMessages> unpackAndApplyLastSeen(LastSeenMessages.Update update) {
		synchronized (this.lastSeenMessages) {
			Optional var10000;
			try {
				LastSeenMessages lastSeenMessages = this.lastSeenMessages.applyUpdate(update);
				var10000 = Optional.of(lastSeenMessages);
			} catch (LastSeenMessagesValidator.ValidationException validationException) {
				LOGGER.error("Failed to validate message acknowledgements from {}: {}", this.player.getPlainTextName(), validationException.getMessage());
				this.disconnect(CHAT_VALIDATION_FAILED);
				return Optional.empty();
			}

			return var10000;
		}
	}

	private static boolean isChatMessageIllegal(String string) {
		for (int i = 0; i < string.length(); i++) {
			if (!StringUtil.isAllowedChatCharacter(string.charAt(i))) {
				return true;
			}
		}

		return false;
	}

	private PlayerChatMessage getSignedMessage(ServerboundChatPacket serverboundChatPacket, LastSeenMessages lastSeenMessages) throws SignedMessageChain.DecodeException {
		SignedMessageBody signedMessageBody = new SignedMessageBody(
			serverboundChatPacket.message(), serverboundChatPacket.timeStamp(), serverboundChatPacket.salt(), lastSeenMessages
		);
		return this.signedMessageDecoder.unpack(serverboundChatPacket.signature(), signedMessageBody);
	}

	private void broadcastChatMessage(PlayerChatMessage playerChatMessage) {
		this.server.getPlayerList().broadcastChatMessage(playerChatMessage, this.player, ChatType.bind(ChatType.CHAT, this.player));
		this.detectRateSpam();
	}

	private void detectRateSpam() {
		this.chatSpamThrottler.increment();
		if (!this.chatSpamThrottler.isUnderThreshold()
			&& !this.server.getPlayerList().isOp(this.player.nameAndId())
			&& !this.server.isSingleplayerOwner(this.player.nameAndId())) {
			this.disconnect(Component.translatable("disconnect.spam"));
		}
	}

	@Override
	public void handleChatAck(ServerboundChatAckPacket serverboundChatAckPacket) {
		synchronized (this.lastSeenMessages) {
			try {
				this.lastSeenMessages.applyOffset(serverboundChatAckPacket.offset());
			} catch (LastSeenMessagesValidator.ValidationException validationException) {
				LOGGER.error("Failed to validate message acknowledgement offset from {}: {}", this.player.getPlainTextName(), validationException.getMessage());
				this.disconnect(CHAT_VALIDATION_FAILED);
			}
		}
	}

	@Override
	public void handleAnimate(ServerboundSwingPacket serverboundSwingPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundSwingPacket, this, this.player.level());
		this.player.resetLastActionTime();
		this.player.swing(serverboundSwingPacket.getHand());
	}

	@Override
	public void handlePlayerCommand(ServerboundPlayerCommandPacket serverboundPlayerCommandPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundPlayerCommandPacket, this, this.player.level());
		if (this.hasClientLoaded()) {
			this.player.resetLastActionTime();
			switch (serverboundPlayerCommandPacket.getAction()) {
				case START_SPRINTING:
					this.player.setSprinting(true);
					break;
				case STOP_SPRINTING:
					this.player.setSprinting(false);
					break;
				case STOP_SLEEPING:
					if (this.player.isSleeping()) {
						this.player.stopSleepInBed(false, true);
						this.awaitingPositionFromClient = this.player.position();
					}
					break;
				case START_RIDING_JUMP:
					if (this.player.getControlledVehicle() instanceof PlayerRideableJumping playerRideableJumping) {
						int i = serverboundPlayerCommandPacket.getData();
						if (playerRideableJumping.canJump() && i > 0) {
							playerRideableJumping.handleStartJump(i);
						}
					}
					break;
				case STOP_RIDING_JUMP:
					if (this.player.getControlledVehicle() instanceof PlayerRideableJumping playerRideableJumping) {
						playerRideableJumping.handleStopJump();
					}
					break;
				case OPEN_INVENTORY:
					if (this.player.getVehicle() instanceof HasCustomInventoryScreen hasCustomInventoryScreen) {
						hasCustomInventoryScreen.openCustomInventoryScreen(this.player);
					}
					break;
				case START_FALL_FLYING:
					if (!this.player.tryToStartFallFlying()) {
						this.player.stopFallFlying();
					}
					break;
				default:
					throw new IllegalArgumentException("Invalid client command!");
			}
		}
	}

	public void sendPlayerChatMessage(PlayerChatMessage playerChatMessage, ChatType.Bound bound) {
		this.send(
			new ClientboundPlayerChatPacket(
				this.nextChatIndex++,
				playerChatMessage.link().sender(),
				playerChatMessage.link().index(),
				playerChatMessage.signature(),
				playerChatMessage.signedBody().pack(this.messageSignatureCache),
				playerChatMessage.unsignedContent(),
				playerChatMessage.filterMask(),
				bound
			)
		);
		MessageSignature messageSignature = playerChatMessage.signature();
		if (messageSignature != null) {
			this.messageSignatureCache.push(playerChatMessage.signedBody(), playerChatMessage.signature());
			int i;
			synchronized (this.lastSeenMessages) {
				this.lastSeenMessages.addPending(messageSignature);
				i = this.lastSeenMessages.trackedMessagesCount();
			}

			if (i > 4096) {
				this.disconnect(Component.translatable("multiplayer.disconnect.too_many_pending_chats"));
			}
		}
	}

	public void sendDisguisedChatMessage(Component component, ChatType.Bound bound) {
		this.send(new ClientboundDisguisedChatPacket(component, bound));
	}

	public SocketAddress getRemoteAddress() {
		return this.connection.getRemoteAddress();
	}

	public void switchToConfig() {
		this.waitingForSwitchToConfig = true;
		this.removePlayerFromWorld();
		this.send(ClientboundStartConfigurationPacket.INSTANCE);
		this.connection.setupOutboundProtocol(ConfigurationProtocols.CLIENTBOUND);
	}

	@Override
	public void handlePingRequest(ServerboundPingRequestPacket serverboundPingRequestPacket) {
		this.connection.send(new ClientboundPongResponsePacket(serverboundPingRequestPacket.getTime()));
	}

	@Override
	public void handleInteract(ServerboundInteractPacket serverboundInteractPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundInteractPacket, this, this.player.level());
		if (this.hasClientLoaded()) {
			final ServerLevel serverLevel = this.player.level();
			final Entity entity = serverboundInteractPacket.getTarget(serverLevel);
			this.player.resetLastActionTime();
			this.player.setShiftKeyDown(serverboundInteractPacket.isUsingSecondaryAction());
			if (entity != null) {
				if (!serverLevel.getWorldBorder().isWithinBounds(entity.blockPosition())) {
					return;
				}

				AABB aABB = entity.getBoundingBox();
				if (serverboundInteractPacket.isWithinRange(this.player, aABB, 3.0)) {
					serverboundInteractPacket.dispatch(
						new ServerboundInteractPacket.Handler() {
							private void performInteraction(InteractionHand interactionHand, ServerGamePacketListenerImpl.EntityInteraction entityInteraction) {
								ItemStack itemStack = ServerGamePacketListenerImpl.this.player.getItemInHand(interactionHand);
								if (itemStack.isItemEnabled(serverLevel.enabledFeatures())) {
									ItemStack itemStack2 = itemStack.copy();
									if (entityInteraction.run(ServerGamePacketListenerImpl.this.player, entity, interactionHand) instanceof InteractionResult.Success success) {
										ItemStack itemStack3 = success.wasItemInteraction() ? itemStack2 : ItemStack.EMPTY;
										CriteriaTriggers.PLAYER_INTERACTED_WITH_ENTITY.trigger(ServerGamePacketListenerImpl.this.player, itemStack3, entity);
										if (success.swingSource() == InteractionResult.SwingSource.SERVER) {
											ServerGamePacketListenerImpl.this.player.swing(interactionHand, true);
										}
									}
								}
							}

							@Override
							public void onInteraction(InteractionHand interactionHand) {
								this.performInteraction(interactionHand, Player::interactOn);
							}

							@Override
							public void onInteraction(InteractionHand interactionHand, Vec3 vec3) {
								this.performInteraction(interactionHand, (serverPlayer, entityxx, interactionHandx) -> entityxx.interactAt(serverPlayer, vec3, interactionHandx));
							}

							@Override
							public void onAttack() {
								if (!(entity instanceof ItemEntity)
									&& !(entity instanceof ExperienceOrb)
									&& entity != ServerGamePacketListenerImpl.this.player
									&& !(entity instanceof AbstractArrow abstractArrow && !abstractArrow.isAttackable())) {
									ItemStack itemStack = ServerGamePacketListenerImpl.this.player.getItemInHand(InteractionHand.MAIN_HAND);
									if (itemStack.isItemEnabled(serverLevel.enabledFeatures())) {
										if (!ServerGamePacketListenerImpl.this.player.cannotAttackWithItem(itemStack, 5)) {
											ServerGamePacketListenerImpl.this.player.attack(entity);
										}
									}
								} else {
									ServerGamePacketListenerImpl.this.disconnect(Component.translatable("multiplayer.disconnect.invalid_entity_attacked"));
									ServerGamePacketListenerImpl.LOGGER.warn("Player {} tried to attack an invalid entity", ServerGamePacketListenerImpl.this.player.getPlainTextName());
								}
							}
						}
					);
				}
			}
		}
	}

	@Override
	public void handleClientCommand(ServerboundClientCommandPacket serverboundClientCommandPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundClientCommandPacket, this, this.player.level());
		this.player.resetLastActionTime();
		ServerboundClientCommandPacket.Action action = serverboundClientCommandPacket.getAction();
		switch (action) {
			case PERFORM_RESPAWN:
				if (this.player.wonGame) {
					this.player.wonGame = false;
					this.player = this.server.getPlayerList().respawn(this.player, true, Entity.RemovalReason.CHANGED_DIMENSION);
					this.resetPosition();
					this.restartClientLoadTimerAfterRespawn();
					CriteriaTriggers.CHANGED_DIMENSION.trigger(this.player, Level.END, Level.OVERWORLD);
				} else {
					if (this.player.getHealth() > 0.0F) {
						return;
					}

					this.player = this.server.getPlayerList().respawn(this.player, false, Entity.RemovalReason.KILLED);
					this.resetPosition();
					this.restartClientLoadTimerAfterRespawn();
					if (this.server.isHardcore()) {
						this.player.setGameMode(GameType.SPECTATOR);
						this.player.level().getGameRules().set(GameRules.SPECTATORS_GENERATE_CHUNKS, false, this.server);
					}
				}
				break;
			case REQUEST_STATS:
				this.player.getStats().sendStats(this.player);
		}
	}

	@Override
	public void handleContainerClose(ServerboundContainerClosePacket serverboundContainerClosePacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundContainerClosePacket, this, this.player.level());
		this.player.doCloseContainer();
	}

	@Override
	public void handleContainerClick(ServerboundContainerClickPacket serverboundContainerClickPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundContainerClickPacket, this, this.player.level());
		this.player.resetLastActionTime();
		if (this.player.containerMenu.containerId == serverboundContainerClickPacket.containerId()) {
			if (this.player.isSpectator()) {
				this.player.containerMenu.sendAllDataToRemote();
			} else if (!this.player.containerMenu.stillValid(this.player)) {
				LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
			} else {
				int i = serverboundContainerClickPacket.slotNum();
				if (!this.player.containerMenu.isValidSlotIndex(i)) {
					LOGGER.debug("Player {} clicked invalid slot index: {}, available slots: {}", this.player.getPlainTextName(), i, this.player.containerMenu.slots.size());
				} else {
					boolean bl = serverboundContainerClickPacket.stateId() != this.player.containerMenu.getStateId();
					this.player.containerMenu.suppressRemoteUpdates();
					this.player.containerMenu.clicked(i, serverboundContainerClickPacket.buttonNum(), serverboundContainerClickPacket.clickType(), this.player);

					for (Entry<HashedStack> entry : Int2ObjectMaps.fastIterable(serverboundContainerClickPacket.changedSlots())) {
						this.player.containerMenu.setRemoteSlotUnsafe(entry.getIntKey(), entry.getValue());
					}

					this.player.containerMenu.setRemoteCarried(serverboundContainerClickPacket.carriedItem());
					this.player.containerMenu.resumeRemoteUpdates();
					if (bl) {
						this.player.containerMenu.broadcastFullState();
					} else {
						this.player.containerMenu.broadcastChanges();
					}
				}
			}
		}
	}

	@Override
	public void handlePlaceRecipe(ServerboundPlaceRecipePacket serverboundPlaceRecipePacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundPlaceRecipePacket, this, this.player.level());
		this.player.resetLastActionTime();
		if (!this.player.isSpectator() && this.player.containerMenu.containerId == serverboundPlaceRecipePacket.containerId()) {
			if (!this.player.containerMenu.stillValid(this.player)) {
				LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
			} else {
				RecipeManager.ServerDisplayInfo serverDisplayInfo = this.server.getRecipeManager().getRecipeFromDisplay(serverboundPlaceRecipePacket.recipe());
				if (serverDisplayInfo != null) {
					RecipeHolder<?> recipeHolder = serverDisplayInfo.parent();
					if (this.player.getRecipeBook().contains(recipeHolder.id())) {
						if (this.player.containerMenu instanceof RecipeBookMenu recipeBookMenu) {
							if (recipeHolder.value().placementInfo().isImpossibleToPlace()) {
								LOGGER.debug("Player {} tried to place impossible recipe {}", this.player, recipeHolder.id().identifier());
								return;
							}

							RecipeBookMenu.PostPlaceAction postPlaceAction = recipeBookMenu.handlePlacement(
								serverboundPlaceRecipePacket.useMaxItems(), this.player.isCreative(), recipeHolder, this.player.level(), this.player.getInventory()
							);
							if (postPlaceAction == RecipeBookMenu.PostPlaceAction.PLACE_GHOST_RECIPE) {
								this.send(new ClientboundPlaceGhostRecipePacket(this.player.containerMenu.containerId, serverDisplayInfo.display().display()));
							}
						}
					}
				}
			}
		}
	}

	@Override
	public void handleContainerButtonClick(ServerboundContainerButtonClickPacket serverboundContainerButtonClickPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundContainerButtonClickPacket, this, this.player.level());
		this.player.resetLastActionTime();
		if (this.player.containerMenu.containerId == serverboundContainerButtonClickPacket.containerId() && !this.player.isSpectator()) {
			if (!this.player.containerMenu.stillValid(this.player)) {
				LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
			} else {
				boolean bl = this.player.containerMenu.clickMenuButton(this.player, serverboundContainerButtonClickPacket.buttonId());
				if (bl) {
					this.player.containerMenu.broadcastChanges();
				}
			}
		}
	}

	@Override
	public void handleSetCreativeModeSlot(ServerboundSetCreativeModeSlotPacket serverboundSetCreativeModeSlotPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundSetCreativeModeSlotPacket, this, this.player.level());
		if (this.player.hasInfiniteMaterials()) {
			boolean bl = serverboundSetCreativeModeSlotPacket.slotNum() < 0;
			ItemStack itemStack = serverboundSetCreativeModeSlotPacket.itemStack();
			if (!itemStack.isItemEnabled(this.player.level().enabledFeatures())) {
				return;
			}

			boolean bl2 = serverboundSetCreativeModeSlotPacket.slotNum() >= 1 && serverboundSetCreativeModeSlotPacket.slotNum() <= 45;
			boolean bl3 = itemStack.isEmpty() || itemStack.getCount() <= itemStack.getMaxStackSize();
			if (bl2 && bl3) {
				this.player.inventoryMenu.getSlot(serverboundSetCreativeModeSlotPacket.slotNum()).setByPlayer(itemStack);
				this.player.inventoryMenu.setRemoteSlot(serverboundSetCreativeModeSlotPacket.slotNum(), itemStack);
				this.player.inventoryMenu.broadcastChanges();
			} else if (bl && bl3) {
				if (this.dropSpamThrottler.isUnderThreshold()) {
					this.dropSpamThrottler.increment();
					this.player.drop(itemStack, true);
				} else {
					LOGGER.warn("Player {} was dropping items too fast in creative mode, ignoring.", this.player.getPlainTextName());
				}
			}
		}
	}

	@Override
	public void handleSignUpdate(ServerboundSignUpdatePacket serverboundSignUpdatePacket) {
		List<String> list = Stream.of(serverboundSignUpdatePacket.getLines()).map(ChatFormatting::stripFormatting).collect(Collectors.toList());
		this.filterTextPacket(list).thenAcceptAsync(listx -> this.updateSignText(serverboundSignUpdatePacket, listx), this.server);
	}

	private void updateSignText(ServerboundSignUpdatePacket serverboundSignUpdatePacket, List<FilteredText> list) {
		this.player.resetLastActionTime();
		ServerLevel serverLevel = this.player.level();
		BlockPos blockPos = serverboundSignUpdatePacket.getPos();
		if (serverLevel.hasChunkAt(blockPos)) {
			if (!(serverLevel.getBlockEntity(blockPos) instanceof SignBlockEntity signBlockEntity)) {
				return;
			}

			signBlockEntity.updateSignText(this.player, serverboundSignUpdatePacket.isFrontText(), list);
		}
	}

	@Override
	public void handlePlayerAbilities(ServerboundPlayerAbilitiesPacket serverboundPlayerAbilitiesPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundPlayerAbilitiesPacket, this, this.player.level());
		this.player.getAbilities().flying = serverboundPlayerAbilitiesPacket.isFlying() && this.player.getAbilities().mayfly;
	}

	@Override
	public void handleClientInformation(ServerboundClientInformationPacket serverboundClientInformationPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundClientInformationPacket, this, this.player.level());
		boolean bl = this.player.isModelPartShown(PlayerModelPart.HAT);
		this.player.updateOptions(serverboundClientInformationPacket.information());
		if (this.player.isModelPartShown(PlayerModelPart.HAT) != bl) {
			this.server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_HAT, this.player));
		}
	}

	@Override
	public void handleChangeDifficulty(ServerboundChangeDifficultyPacket serverboundChangeDifficultyPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundChangeDifficultyPacket, this, this.player.level());
		if (!this.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) && !this.isSingleplayerOwner()) {
			LOGGER.warn(
				"Player {} tried to change difficulty to {} without required permissions",
				this.player.getGameProfile().name(),
				serverboundChangeDifficultyPacket.difficulty().getDisplayName()
			);
		} else {
			this.server.setDifficulty(serverboundChangeDifficultyPacket.difficulty(), false);
		}
	}

	@Override
	public void handleChangeGameMode(ServerboundChangeGameModePacket serverboundChangeGameModePacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundChangeGameModePacket, this, this.player.level());
		if (!GameModeCommand.PERMISSION_CHECK.check(this.player.permissions())) {
			LOGGER.warn(
				"Player {} tried to change game mode to {} without required permissions",
				this.player.getGameProfile().name(),
				serverboundChangeGameModePacket.mode().getShortDisplayName().getString()
			);
		} else {
			GameModeCommand.setGameMode(this.player, serverboundChangeGameModePacket.mode());
		}
	}

	@Override
	public void handleLockDifficulty(ServerboundLockDifficultyPacket serverboundLockDifficultyPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundLockDifficultyPacket, this, this.player.level());
		if (this.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || this.isSingleplayerOwner()) {
			this.server.setDifficultyLocked(serverboundLockDifficultyPacket.isLocked());
		}
	}

	@Override
	public void handleChatSessionUpdate(ServerboundChatSessionUpdatePacket serverboundChatSessionUpdatePacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundChatSessionUpdatePacket, this, this.player.level());
		RemoteChatSession.Data data = serverboundChatSessionUpdatePacket.chatSession();
		ProfilePublicKey.Data data2 = this.chatSession != null ? this.chatSession.profilePublicKey().data() : null;
		ProfilePublicKey.Data data3 = data.profilePublicKey();
		if (!Objects.equals(data2, data3)) {
			if (data2 != null && data3.expiresAt().isBefore(data2.expiresAt())) {
				this.disconnect(ProfilePublicKey.EXPIRED_PROFILE_PUBLIC_KEY);
			} else {
				try {
					SignatureValidator signatureValidator = this.server.services().profileKeySignatureValidator();
					if (signatureValidator == null) {
						LOGGER.warn("Ignoring chat session from {} due to missing Services public key", this.player.getGameProfile().name());
						return;
					}

					this.resetPlayerChatState(data.validate(this.player.getGameProfile(), signatureValidator));
				} catch (ProfilePublicKey.ValidationException validationException) {
					LOGGER.error("Failed to validate profile key: {}", validationException.getMessage());
					this.disconnect(validationException.getComponent());
				}
			}
		}
	}

	@Override
	public void handleConfigurationAcknowledged(ServerboundConfigurationAcknowledgedPacket serverboundConfigurationAcknowledgedPacket) {
		if (!this.waitingForSwitchToConfig) {
			throw new IllegalStateException("Client acknowledged config, but none was requested");
		}

		this.connection
			.setupInboundProtocol(
				ConfigurationProtocols.SERVERBOUND,
				new ServerConfigurationPacketListenerImpl(this.server, this.connection, this.createCookie(this.player.clientInformation()))
			);
	}

	@Override
	public void handleChunkBatchReceived(ServerboundChunkBatchReceivedPacket serverboundChunkBatchReceivedPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundChunkBatchReceivedPacket, this, this.player.level());
		this.chunkSender.onChunkBatchReceivedByClient(serverboundChunkBatchReceivedPacket.desiredChunksPerTick());
	}

	@Override
	public void handleDebugSubscriptionRequest(ServerboundDebugSubscriptionRequestPacket serverboundDebugSubscriptionRequestPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundDebugSubscriptionRequestPacket, this, this.player.level());
		this.player.requestDebugSubscriptions(serverboundDebugSubscriptionRequestPacket.subscriptions());
	}

	private void resetPlayerChatState(RemoteChatSession remoteChatSession) {
		this.chatSession = remoteChatSession;
		this.signedMessageDecoder = remoteChatSession.createMessageDecoder(this.player.getUUID());
		this.chatMessageChain
			.append(
				() -> {
					this.player.setChatSession(remoteChatSession);
					this.server
						.getPlayerList()
						.broadcastAll(new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.INITIALIZE_CHAT), List.of(this.player)));
				}
			);
	}

	@Override
	public void handleCustomPayload(ServerboundCustomPayloadPacket serverboundCustomPayloadPacket) {
	}

	@Override
	public void handleClientTickEnd(ServerboundClientTickEndPacket serverboundClientTickEndPacket) {
		PacketUtils.ensureRunningOnSameThread(serverboundClientTickEndPacket, this, this.player.level());
		if (!this.receivedMovementThisTick) {
			this.player.setKnownMovement(Vec3.ZERO);
		}

		this.receivedMovementThisTick = false;
	}

	private void handlePlayerKnownMovement(Vec3 vec3) {
		if (vec3.lengthSqr() > 1.0E-5F) {
			this.player.resetLastActionTime();
		}

		this.player.setKnownMovement(vec3);
		this.receivedMovementThisTick = true;
	}

	@Override
	public boolean hasInfiniteMaterials() {
		return this.player.hasInfiniteMaterials();
	}

	@Override
	public ServerPlayer getPlayer() {
		return this.player;
	}

	public boolean hasClientLoaded() {
		return !this.waitingForRespawn && this.clientLoadedTimeoutTimer <= 0;
	}

	public void tickClientLoadTimeout() {
		if (this.clientLoadedTimeoutTimer > 0) {
			this.clientLoadedTimeoutTimer--;
		}
	}

	private void markClientLoaded() {
		this.clientLoadedTimeoutTimer = 0;
	}

	public void markClientUnloadedAfterDeath() {
		this.waitingForRespawn = true;
	}

	private void restartClientLoadTimerAfterRespawn() {
		this.waitingForRespawn = false;
		this.clientLoadedTimeoutTimer = 60;
	}

	@FunctionalInterface
	interface EntityInteraction {
		InteractionResult run(ServerPlayer serverPlayer, Entity entity, InteractionHand interactionHand);
	}
}
