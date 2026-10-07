package net.minecraft.client.multiplayer;

import com.google.common.collect.Lists;
import com.google.common.primitives.Shorts;
import com.google.common.primitives.SignedBytes;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerSlotStateChangedPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.StatsCounter;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class MultiPlayerGameMode {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final Minecraft minecraft;
	private final ClientPacketListener connection;
	private BlockPos destroyBlockPos = new BlockPos(-1, -1, -1);
	private ItemStack destroyingItem = ItemStack.EMPTY;
	private float destroyProgress;
	private float destroyTicks;
	private int destroyDelay;
	private boolean isDestroying;
	private GameType localPlayerMode = GameType.DEFAULT_MODE;
	private @Nullable GameType previousLocalPlayerMode;
	private int carriedIndex;

	public MultiPlayerGameMode(Minecraft minecraft, ClientPacketListener clientPacketListener) {
		this.minecraft = minecraft;
		this.connection = clientPacketListener;
	}

	public void adjustPlayer(Player player) {
		this.localPlayerMode.updatePlayerAbilities(player.getAbilities());
	}

	public void setLocalMode(GameType gameType, @Nullable GameType gameType2) {
		this.localPlayerMode = gameType;
		this.previousLocalPlayerMode = gameType2;
		this.localPlayerMode.updatePlayerAbilities(this.minecraft.player.getAbilities());
	}

	public void setLocalMode(GameType gameType) {
		if (gameType != this.localPlayerMode) {
			this.previousLocalPlayerMode = this.localPlayerMode;
		}

		this.localPlayerMode = gameType;
		this.localPlayerMode.updatePlayerAbilities(this.minecraft.player.getAbilities());
	}

	public boolean canHurtPlayer() {
		return this.localPlayerMode.isSurvival();
	}

	public boolean destroyBlock(BlockPos blockPos) {
		if (this.minecraft.player.blockActionRestricted(this.minecraft.level, blockPos, this.localPlayerMode)) {
			return false;
		}

		Level level = this.minecraft.level;
		BlockState blockState = level.getBlockState(blockPos);
		if (!this.minecraft.player.getMainHandItem().canDestroyBlock(blockState, level, blockPos, this.minecraft.player)) {
			return false;
		}

		Block block = blockState.getBlock();
		if (block instanceof GameMasterBlock && !this.minecraft.player.canUseGameMasterBlocks()) {
			return false;
		}

		if (blockState.isAir()) {
			return false;
		}

		block.playerWillDestroy(level, blockPos, blockState, this.minecraft.player);
		FluidState fluidState = level.getFluidState(blockPos);
		boolean bl = level.setBlock(blockPos, fluidState.createLegacyBlock(), 11);
		if (bl) {
			block.destroy(level, blockPos, blockState);
		}

		if (SharedConstants.DEBUG_BLOCK_BREAK) {
			LOGGER.error("client broke {} {} -> {}", blockPos, blockState, level.getBlockState(blockPos));
		}

		return bl;
	}

	public boolean startDestroyBlock(BlockPos blockPos, Direction direction) {
		if (this.minecraft.player.blockActionRestricted(this.minecraft.level, blockPos, this.localPlayerMode)) {
			return false;
		}

		if (!this.minecraft.level.getWorldBorder().isWithinBounds(blockPos)) {
			return false;
		}

		if (this.minecraft.player.getAbilities().instabuild) {
			BlockState blockState = this.minecraft.level.getBlockState(blockPos);
			this.minecraft.getTutorial().onDestroyBlock(this.minecraft.level, blockPos, blockState, 1.0F);
			if (SharedConstants.DEBUG_BLOCK_BREAK) {
				LOGGER.info("Creative start {} {}", blockPos, blockState);
			}

			this.startPrediction(this.minecraft.level, i -> {
				this.destroyBlock(blockPos);
				return new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, blockPos, direction, i);
			});
			this.destroyDelay = 5;
		} else if (!this.isDestroying || !this.sameDestroyTarget(blockPos)) {
			if (this.isDestroying) {
				if (SharedConstants.DEBUG_BLOCK_BREAK) {
					LOGGER.info("Abort old break {} {}", blockPos, this.minecraft.level.getBlockState(blockPos));
				}

				this.connection.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, this.destroyBlockPos, direction));
			}

			BlockState blockState = this.minecraft.level.getBlockState(blockPos);
			this.minecraft.getTutorial().onDestroyBlock(this.minecraft.level, blockPos, blockState, 0.0F);
			if (SharedConstants.DEBUG_BLOCK_BREAK) {
				LOGGER.info("Start break {} {}", blockPos, blockState);
			}

			this.startPrediction(this.minecraft.level, i -> {
				boolean bl = !blockState.isAir();
				if (bl && this.destroyProgress == 0.0F) {
					blockState.attack(this.minecraft.level, blockPos, this.minecraft.player);
				}

				if (bl && blockState.getDestroyProgress(this.minecraft.player, this.minecraft.player.level(), blockPos) >= 1.0F) {
					this.destroyBlock(blockPos);
				} else {
					this.isDestroying = true;
					this.destroyBlockPos = blockPos;
					this.destroyingItem = this.minecraft.player.getMainHandItem();
					this.destroyProgress = 0.0F;
					this.destroyTicks = 0.0F;
					this.minecraft.level.destroyBlockProgress(this.minecraft.player.getId(), this.destroyBlockPos, this.getDestroyStage());
				}

				return new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, blockPos, direction, i);
			});
		}

		return true;
	}

	public void stopDestroyBlock() {
		if (this.isDestroying) {
			BlockState blockState = this.minecraft.level.getBlockState(this.destroyBlockPos);
			this.minecraft.getTutorial().onDestroyBlock(this.minecraft.level, this.destroyBlockPos, blockState, -1.0F);
			if (SharedConstants.DEBUG_BLOCK_BREAK) {
				LOGGER.info("Stop dest {} {}", this.destroyBlockPos, blockState);
			}

			this.connection.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, this.destroyBlockPos, Direction.DOWN));
			this.isDestroying = false;
			this.destroyProgress = 0.0F;
			this.minecraft.level.destroyBlockProgress(this.minecraft.player.getId(), this.destroyBlockPos, -1);
			this.minecraft.player.resetAttackStrengthTicker();
		}
	}

	public boolean continueDestroyBlock(BlockPos blockPos, Direction direction) {
		this.ensureHasSentCarriedItem();
		if (this.destroyDelay > 0) {
			this.destroyDelay--;
			return true;
		}

		if (this.minecraft.player.getAbilities().instabuild && this.minecraft.level.getWorldBorder().isWithinBounds(blockPos)) {
			this.destroyDelay = 5;
			BlockState blockState = this.minecraft.level.getBlockState(blockPos);
			this.minecraft.getTutorial().onDestroyBlock(this.minecraft.level, blockPos, blockState, 1.0F);
			if (SharedConstants.DEBUG_BLOCK_BREAK) {
				LOGGER.info("Creative cont {} {}", blockPos, blockState);
			}

			this.startPrediction(this.minecraft.level, i -> {
				this.destroyBlock(blockPos);
				return new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, blockPos, direction, i);
			});
			return true;
		} else if (this.sameDestroyTarget(blockPos)) {
			BlockState blockState = this.minecraft.level.getBlockState(blockPos);
			if (blockState.isAir()) {
				this.isDestroying = false;
				return false;
			}

			this.destroyProgress = this.destroyProgress + blockState.getDestroyProgress(this.minecraft.player, this.minecraft.player.level(), blockPos);
			if (this.destroyTicks % 4.0F == 0.0F) {
				SoundType soundType = blockState.getSoundType();
				this.minecraft
					.getSoundManager()
					.play(
						new SimpleSoundInstance(
							soundType.getHitSound(),
							SoundSource.BLOCKS,
							(soundType.getVolume() + 1.0F) / 8.0F,
							soundType.getPitch() * 0.5F,
							SoundInstance.createUnseededRandom(),
							blockPos
						)
					);
			}

			this.destroyTicks++;
			this.minecraft.getTutorial().onDestroyBlock(this.minecraft.level, blockPos, blockState, Mth.clamp(this.destroyProgress, 0.0F, 1.0F));
			if (this.destroyProgress >= 1.0F) {
				this.isDestroying = false;
				if (SharedConstants.DEBUG_BLOCK_BREAK) {
					LOGGER.info("Finished breaking {} {}", blockPos, blockState);
				}

				this.startPrediction(this.minecraft.level, i -> {
					this.destroyBlock(blockPos);
					return new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, blockPos, direction, i);
				});
				this.destroyProgress = 0.0F;
				this.destroyTicks = 0.0F;
				this.destroyDelay = 5;
			}

			this.minecraft.level.destroyBlockProgress(this.minecraft.player.getId(), this.destroyBlockPos, this.getDestroyStage());
			return true;
		} else {
			return this.startDestroyBlock(blockPos, direction);
		}
	}

	private void startPrediction(ClientLevel clientLevel, PredictiveAction predictiveAction) {
		try (BlockStatePredictionHandler blockStatePredictionHandler = clientLevel.getBlockStatePredictionHandler().startPredicting()) {
			int i = blockStatePredictionHandler.currentSequence();
			Packet<ServerGamePacketListener> packet = predictiveAction.predict(i);
			this.connection.send(packet);
		}
	}

	public void tick() {
		this.ensureHasSentCarriedItem();
		if (this.connection.getConnection().isConnected()) {
			this.connection.getConnection().tick();
		} else {
			this.connection.getConnection().handleDisconnection();
		}
	}

	private boolean sameDestroyTarget(BlockPos blockPos) {
		ItemStack itemStack = this.minecraft.player.getMainHandItem();
		return blockPos.equals(this.destroyBlockPos) && ItemStack.isSameItemSameComponents(itemStack, this.destroyingItem);
	}

	private void ensureHasSentCarriedItem() {
		int i = this.minecraft.player.getInventory().getSelectedSlot();
		if (i != this.carriedIndex) {
			this.carriedIndex = i;
			this.connection.send(new ServerboundSetCarriedItemPacket(this.carriedIndex));
		}
	}

	public InteractionResult useItemOn(LocalPlayer localPlayer, InteractionHand interactionHand, BlockHitResult blockHitResult) {
		this.ensureHasSentCarriedItem();
		if (!this.minecraft.level.getWorldBorder().isWithinBounds(blockHitResult.getBlockPos())) {
			return InteractionResult.FAIL;
		}

		MutableObject<InteractionResult> mutableObject = new MutableObject<>();
		this.startPrediction(this.minecraft.level, i -> {
			mutableObject.setValue(this.performUseItemOn(localPlayer, interactionHand, blockHitResult));
			return new ServerboundUseItemOnPacket(interactionHand, blockHitResult, i);
		});
		return mutableObject.get();
	}

	private InteractionResult performUseItemOn(LocalPlayer localPlayer, InteractionHand interactionHand, BlockHitResult blockHitResult) {
		BlockPos blockPos = blockHitResult.getBlockPos();
		ItemStack itemStack = localPlayer.getItemInHand(interactionHand);
		if (this.localPlayerMode == GameType.SPECTATOR) {
			return InteractionResult.CONSUME;
		}

		boolean bl = !localPlayer.getMainHandItem().isEmpty() || !localPlayer.getOffhandItem().isEmpty();
		boolean bl2 = localPlayer.isSecondaryUseActive() && bl;
		if (!bl2) {
			BlockState blockState = this.minecraft.level.getBlockState(blockPos);
			if (!this.connection.isFeatureEnabled(blockState.getBlock().requiredFeatures())) {
				return InteractionResult.FAIL;
			}

			InteractionResult interactionResult = blockState.useItemOn(
				localPlayer.getItemInHand(interactionHand), this.minecraft.level, localPlayer, interactionHand, blockHitResult
			);
			if (interactionResult.consumesAction()) {
				return interactionResult;
			}

			if (interactionResult instanceof InteractionResult.TryEmptyHandInteraction && interactionHand == InteractionHand.MAIN_HAND) {
				InteractionResult interactionResult2 = blockState.useWithoutItem(this.minecraft.level, localPlayer, blockHitResult);
				if (interactionResult2.consumesAction()) {
					return interactionResult2;
				}
			}
		}

		if (!itemStack.isEmpty() && !localPlayer.getCooldowns().isOnCooldown(itemStack)) {
			UseOnContext useOnContext = new UseOnContext(localPlayer, interactionHand, blockHitResult);
			InteractionResult interactionResult3;
			if (localPlayer.hasInfiniteMaterials()) {
				int i = itemStack.getCount();
				interactionResult3 = itemStack.useOn(useOnContext);
				itemStack.setCount(i);
			} else {
				interactionResult3 = itemStack.useOn(useOnContext);
			}

			return interactionResult3;
		} else {
			return InteractionResult.PASS;
		}
	}

	public InteractionResult useItem(Player player, InteractionHand interactionHand) {
		if (this.localPlayerMode == GameType.SPECTATOR) {
			return InteractionResult.PASS;
		}

		this.ensureHasSentCarriedItem();
		MutableObject<InteractionResult> mutableObject = new MutableObject<>();
		this.startPrediction(this.minecraft.level, i -> {
			ServerboundUseItemPacket serverboundUseItemPacket = new ServerboundUseItemPacket(interactionHand, i, player.getYRot(), player.getXRot());
			ItemStack itemStack = player.getItemInHand(interactionHand);
			if (player.getCooldowns().isOnCooldown(itemStack)) {
				mutableObject.setValue(InteractionResult.PASS);
				return serverboundUseItemPacket;
			}

			InteractionResult interactionResult = itemStack.use(this.minecraft.level, player, interactionHand);
			ItemStack itemStack2;
			if (interactionResult instanceof InteractionResult.Success success) {
				itemStack2 = Objects.requireNonNullElseGet(success.heldItemTransformedTo(), () -> player.getItemInHand(interactionHand));
			} else {
				itemStack2 = player.getItemInHand(interactionHand);
			}

			if (itemStack2 != itemStack) {
				player.setItemInHand(interactionHand, itemStack2);
			}

			mutableObject.setValue(interactionResult);
			return serverboundUseItemPacket;
		});
		return mutableObject.get();
	}

	public LocalPlayer createPlayer(ClientLevel clientLevel, StatsCounter statsCounter, ClientRecipeBook clientRecipeBook) {
		return this.createPlayer(clientLevel, statsCounter, clientRecipeBook, Input.EMPTY, false);
	}

	public LocalPlayer createPlayer(ClientLevel clientLevel, StatsCounter statsCounter, ClientRecipeBook clientRecipeBook, Input input, boolean bl) {
		return new LocalPlayer(this.minecraft, clientLevel, this.connection, statsCounter, clientRecipeBook, input, bl);
	}

	public void attack(Player player, Entity entity) {
		this.ensureHasSentCarriedItem();
		this.connection.send(ServerboundInteractPacket.createAttackPacket(entity, player.isShiftKeyDown()));
		if (this.localPlayerMode != GameType.SPECTATOR) {
			player.attack(entity);
			player.resetAttackStrengthTicker();
		}
	}

	public InteractionResult interact(Player player, Entity entity, InteractionHand interactionHand) {
		this.ensureHasSentCarriedItem();
		this.connection.send(ServerboundInteractPacket.createInteractionPacket(entity, player.isShiftKeyDown(), interactionHand));
		return this.localPlayerMode == GameType.SPECTATOR ? InteractionResult.PASS : player.interactOn(entity, interactionHand);
	}

	public InteractionResult interactAt(Player player, Entity entity, EntityHitResult entityHitResult, InteractionHand interactionHand) {
		this.ensureHasSentCarriedItem();
		Vec3 vec3 = entityHitResult.getLocation().subtract(entity.getX(), entity.getY(), entity.getZ());
		this.connection.send(ServerboundInteractPacket.createInteractionPacket(entity, player.isShiftKeyDown(), interactionHand, vec3));
		return this.localPlayerMode == GameType.SPECTATOR ? InteractionResult.PASS : entity.interactAt(player, vec3, interactionHand);
	}

	public void handleInventoryMouseClick(int i, int j, int k, ClickType clickType, Player player) {
		AbstractContainerMenu abstractContainerMenu = player.containerMenu;
		if (i != abstractContainerMenu.containerId) {
			LOGGER.warn("Ignoring click in mismatching container. Click in {}, player has {}.", i, abstractContainerMenu.containerId);
		} else {
			NonNullList<Slot> nonNullList = abstractContainerMenu.slots;
			int l = nonNullList.size();
			List<ItemStack> list = Lists.newArrayListWithCapacity(l);

			for (Slot slot : nonNullList) {
				list.add(slot.getItem().copy());
			}

			abstractContainerMenu.clicked(j, k, clickType, player);
			Int2ObjectMap<HashedStack> int2ObjectMap = new Int2ObjectOpenHashMap<>();

			for (int m = 0; m < l; m++) {
				ItemStack itemStack = list.get(m);
				ItemStack itemStack2 = nonNullList.get(m).getItem();
				if (!ItemStack.matches(itemStack, itemStack2)) {
					int2ObjectMap.put(m, HashedStack.create(itemStack2, this.connection.decoratedHashOpsGenenerator()));
				}
			}

			HashedStack hashedStack = HashedStack.create(abstractContainerMenu.getCarried(), this.connection.decoratedHashOpsGenenerator());
			this.connection
				.send(
					new ServerboundContainerClickPacket(
						i, abstractContainerMenu.getStateId(), Shorts.checkedCast(j), SignedBytes.checkedCast(k), clickType, int2ObjectMap, hashedStack
					)
				);
		}
	}

	public void handlePlaceRecipe(int i, RecipeDisplayId recipeDisplayId, boolean bl) {
		this.connection.send(new ServerboundPlaceRecipePacket(i, recipeDisplayId, bl));
	}

	public void handleInventoryButtonClick(int i, int j) {
		this.connection.send(new ServerboundContainerButtonClickPacket(i, j));
	}

	public void handleCreativeModeItemAdd(ItemStack itemStack, int i) {
		if (this.minecraft.player.hasInfiniteMaterials() && this.connection.isFeatureEnabled(itemStack.getItem().requiredFeatures())) {
			this.connection.send(new ServerboundSetCreativeModeSlotPacket(i, itemStack));
		}
	}

	public void handleCreativeModeItemDrop(ItemStack itemStack) {
		boolean bl = this.minecraft.screen instanceof AbstractContainerScreen && !(this.minecraft.screen instanceof CreativeModeInventoryScreen);
		if (this.minecraft.player.hasInfiniteMaterials() && !bl && !itemStack.isEmpty() && this.connection.isFeatureEnabled(itemStack.getItem().requiredFeatures())) {
			this.connection.send(new ServerboundSetCreativeModeSlotPacket(-1, itemStack));
			this.minecraft.player.getDropSpamThrottler().increment();
		}
	}

	public void releaseUsingItem(Player player) {
		this.ensureHasSentCarriedItem();
		this.connection.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
		player.releaseUsingItem();
	}

	public void piercingAttack(PiercingWeapon piercingWeapon) {
		this.ensureHasSentCarriedItem();
		this.connection.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STAB, BlockPos.ZERO, Direction.DOWN));
		this.minecraft.player.onAttack();
		this.minecraft.player.lungeForwardMaybe();
		piercingWeapon.makeSound(this.minecraft.player);
	}

	public boolean hasExperience() {
		return this.localPlayerMode.isSurvival();
	}

	public boolean hasMissTime() {
		return !this.localPlayerMode.isCreative();
	}

	public boolean isServerControlledInventory() {
		return this.minecraft.player.isPassenger() && this.minecraft.player.getVehicle() instanceof HasCustomInventoryScreen;
	}

	public boolean isSpectator() {
		return this.localPlayerMode == GameType.SPECTATOR;
	}

	public @Nullable GameType getPreviousPlayerMode() {
		return this.previousLocalPlayerMode;
	}

	public GameType getPlayerMode() {
		return this.localPlayerMode;
	}

	public boolean isDestroying() {
		return this.isDestroying;
	}

	public int getDestroyStage() {
		return this.destroyProgress > 0.0F ? (int)(this.destroyProgress * 10.0F) : -1;
	}

	public void handlePickItemFromBlock(BlockPos blockPos, boolean bl) {
		this.connection.send(new ServerboundPickItemFromBlockPacket(blockPos, bl));
	}

	public void handlePickItemFromEntity(Entity entity, boolean bl) {
		this.connection.send(new ServerboundPickItemFromEntityPacket(entity.getId(), bl));
	}

	public void handleSlotStateChanged(int i, int j, boolean bl) {
		this.connection.send(new ServerboundContainerSlotStateChangedPacket(i, j, bl));
	}
}
