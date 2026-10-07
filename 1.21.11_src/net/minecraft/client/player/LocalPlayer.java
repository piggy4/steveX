package net.minecraft.client.player;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.CommandBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.HangingSignEditScreen;
import net.minecraft.client.gui.screens.inventory.JigsawBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.MinecartCommandBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.TestBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.TestInstanceBlockEditScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.sounds.AmbientSoundHandler;
import net.minecraft.client.resources.sounds.BiomeAmbientSoundsHandler;
import net.minecraft.client.resources.sounds.BubbleColumnAmbientSoundHandler;
import net.minecraft.client.resources.sounds.ElytraOnPlayerSoundInstance;
import net.minecraft.client.resources.sounds.RidingEntitySoundInstance;
import net.minecraft.client.resources.sounds.RidingMinecartSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.UnderwaterAmbientSoundHandler;
import net.minecraft.client.resources.sounds.UnderwaterAmbientSoundInstances;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.StatsCounter;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.TickThrottler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartCommandBlock;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.entity.TestBlockEntity;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class LocalPlayer extends AbstractClientPlayer {
	public static final Logger LOGGER = LogUtils.getLogger();
	private static final int POSITION_REMINDER_INTERVAL = 20;
	private static final int WATER_VISION_MAX_TIME = 600;
	private static final int WATER_VISION_QUICK_TIME = 100;
	private static final float WATER_VISION_QUICK_PERCENT = 0.6F;
	private static final double SUFFOCATING_COLLISION_CHECK_SCALE = 0.35;
	private static final double MINOR_COLLISION_ANGLE_THRESHOLD_RADIAN = 0.13962634F;
	public final ClientPacketListener connection;
	private final StatsCounter stats;
	private final ClientRecipeBook recipeBook;
	private final TickThrottler dropSpamThrottler = new TickThrottler(20, 1280);
	private final List<AmbientSoundHandler> ambientSoundHandlers = Lists.newArrayList();
	private PermissionSet permissions = PermissionSet.NO_PERMISSIONS;
	private double xLast;
	private double yLast;
	private double zLast;
	private float yRotLast;
	private float xRotLast;
	private boolean lastOnGround;
	private boolean lastHorizontalCollision;
	private boolean crouching;
	private boolean wasSprinting;
	private int positionReminder;
	private boolean flashOnSetHealth;
	public ClientInput input = new ClientInput();
	private Input lastSentInput;
	protected final Minecraft minecraft;
	protected int sprintTriggerTime;
	private static final int EXPERIENCE_DISPLAY_UNREADY_TO_SET = Integer.MIN_VALUE;
	private static final int EXPERIENCE_DISPLAY_READY_TO_SET = -2147483647;
	public int experienceDisplayStartTick = Integer.MIN_VALUE;
	public float yBob;
	public float xBob;
	public float yBobO;
	public float xBobO;
	private int jumpRidingTicks;
	private float jumpRidingScale;
	public float portalEffectIntensity;
	public float oPortalEffectIntensity;
	private boolean startedUsingItem;
	private @Nullable InteractionHand usingItemHand;
	private boolean handsBusy;
	private boolean autoJumpEnabled = true;
	private int autoJumpTime;
	private boolean wasFallFlying;
	private int waterVisionTime;
	private boolean showDeathScreen = true;
	private boolean doLimitedCrafting = false;

	public LocalPlayer(
		Minecraft minecraft,
		ClientLevel clientLevel,
		ClientPacketListener clientPacketListener,
		StatsCounter statsCounter,
		ClientRecipeBook clientRecipeBook,
		Input input,
		boolean bl
	) {
		super(clientLevel, clientPacketListener.getLocalGameProfile());
		this.minecraft = minecraft;
		this.connection = clientPacketListener;
		this.stats = statsCounter;
		this.recipeBook = clientRecipeBook;
		this.lastSentInput = input;
		this.wasSprinting = bl;
		this.ambientSoundHandlers.add(new UnderwaterAmbientSoundHandler(this, minecraft.getSoundManager()));
		this.ambientSoundHandlers.add(new BubbleColumnAmbientSoundHandler(this));
		this.ambientSoundHandlers.add(new BiomeAmbientSoundsHandler(this, minecraft.getSoundManager()));
	}

	@Override
	public void heal(float f) {
	}

	@Override
	public boolean startRiding(Entity entity, boolean bl, boolean bl2) {
		if (!super.startRiding(entity, bl, bl2)) {
			return false;
		}

		if (entity instanceof AbstractMinecart abstractMinecart) {
			this.minecraft
				.getSoundManager()
				.play(new RidingMinecartSoundInstance(this, abstractMinecart, true, SoundEvents.MINECART_INSIDE_UNDERWATER, 0.0F, 0.75F, 1.0F));
			this.minecraft.getSoundManager().play(new RidingMinecartSoundInstance(this, abstractMinecart, false, SoundEvents.MINECART_INSIDE, 0.0F, 0.75F, 1.0F));
		} else if (entity instanceof HappyGhast happyGhast) {
			this.minecraft
				.getSoundManager()
				.play(new RidingEntitySoundInstance(this, happyGhast, false, SoundEvents.HAPPY_GHAST_RIDING, happyGhast.getSoundSource(), 0.0F, 1.0F, 5.0F));
		} else if (entity instanceof AbstractNautilus abstractNautilus) {
			this.minecraft
				.getSoundManager()
				.play(new RidingEntitySoundInstance(this, abstractNautilus, true, SoundEvents.NAUTILUS_RIDING, abstractNautilus.getSoundSource(), 0.0F, 1.0F, 5.0F));
		}

		return true;
	}

	@Override
	public void removeVehicle() {
		super.removeVehicle();
		this.handsBusy = false;
	}

	@Override
	public float getViewXRot(float f) {
		return this.getXRot();
	}

	@Override
	public float getViewYRot(float f) {
		return this.isPassenger() ? super.getViewYRot(f) : this.getYRot();
	}

	@Override
	public void tick() {
		if (this.connection.hasClientLoaded()) {
			this.dropSpamThrottler.tick();
			super.tick();
			if (!this.lastSentInput.equals(this.input.keyPresses)) {
				this.connection.send(new ServerboundPlayerInputPacket(this.input.keyPresses));
				this.lastSentInput = this.input.keyPresses;
			}

			if (this.isPassenger()) {
				this.connection.send(new ServerboundMovePlayerPacket.Rot(this.getYRot(), this.getXRot(), this.onGround(), this.horizontalCollision));
				Entity entity = this.getRootVehicle();
				if (entity != this && entity.isLocalInstanceAuthoritative()) {
					this.connection.send(ServerboundMoveVehiclePacket.fromEntity(entity));
					this.sendIsSprintingIfNeeded();
				}
			} else {
				this.sendPosition();
			}

			for (AmbientSoundHandler ambientSoundHandler : this.ambientSoundHandlers) {
				ambientSoundHandler.tick();
			}
		}
	}

	public float getCurrentMood() {
		for (AmbientSoundHandler ambientSoundHandler : this.ambientSoundHandlers) {
			if (ambientSoundHandler instanceof BiomeAmbientSoundsHandler) {
				return ((BiomeAmbientSoundsHandler)ambientSoundHandler).getMoodiness();
			}
		}

		return 0.0F;
	}

	private void sendPosition() {
		this.sendIsSprintingIfNeeded();
		if (this.isControlledCamera()) {
			double d = this.getX() - this.xLast;
			double e = this.getY() - this.yLast;
			double f = this.getZ() - this.zLast;
			double g = this.getYRot() - this.yRotLast;
			double h = this.getXRot() - this.xRotLast;
			this.positionReminder++;
			boolean bl = Mth.lengthSquared(d, e, f) > Mth.square(2.0E-4) || this.positionReminder >= 20;
			boolean bl2 = g != 0.0 || h != 0.0;
			if (bl && bl2) {
				this.connection.send(new ServerboundMovePlayerPacket.PosRot(this.position(), this.getYRot(), this.getXRot(), this.onGround(), this.horizontalCollision));
			} else if (bl) {
				this.connection.send(new ServerboundMovePlayerPacket.Pos(this.position(), this.onGround(), this.horizontalCollision));
			} else if (bl2) {
				this.connection.send(new ServerboundMovePlayerPacket.Rot(this.getYRot(), this.getXRot(), this.onGround(), this.horizontalCollision));
			} else if (this.lastOnGround != this.onGround() || this.lastHorizontalCollision != this.horizontalCollision) {
				this.connection.send(new ServerboundMovePlayerPacket.StatusOnly(this.onGround(), this.horizontalCollision));
			}

			if (bl) {
				this.xLast = this.getX();
				this.yLast = this.getY();
				this.zLast = this.getZ();
				this.positionReminder = 0;
			}

			if (bl2) {
				this.yRotLast = this.getYRot();
				this.xRotLast = this.getXRot();
			}

			this.lastOnGround = this.onGround();
			this.lastHorizontalCollision = this.horizontalCollision;
			this.autoJumpEnabled = this.minecraft.options.autoJump().get();
		}
	}

	private void sendIsSprintingIfNeeded() {
		boolean bl = this.isSprinting();
		if (bl != this.wasSprinting) {
			ServerboundPlayerCommandPacket.Action action = bl
				? ServerboundPlayerCommandPacket.Action.START_SPRINTING
				: ServerboundPlayerCommandPacket.Action.STOP_SPRINTING;
			this.connection.send(new ServerboundPlayerCommandPacket(this, action));
			this.wasSprinting = bl;
		}
	}

	public boolean drop(boolean bl) {
		ServerboundPlayerActionPacket.Action action = bl ? ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS : ServerboundPlayerActionPacket.Action.DROP_ITEM;
		ItemStack itemStack = this.getInventory().removeFromSelected(bl);
		this.connection.send(new ServerboundPlayerActionPacket(action, BlockPos.ZERO, Direction.DOWN));
		return !itemStack.isEmpty();
	}

	@Override
	public void swing(InteractionHand interactionHand) {
		super.swing(interactionHand);
		this.connection.send(new ServerboundSwingPacket(interactionHand));
	}

	public void respawn() {
		this.connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
		KeyMapping.resetToggleKeys();
	}

	@Override
	public void closeContainer() {
		this.connection.send(new ServerboundContainerClosePacket(this.containerMenu.containerId));
		this.clientSideCloseContainer();
	}

	public void clientSideCloseContainer() {
		super.closeContainer();
		this.minecraft.setScreen(null);
	}

	public void hurtTo(float f) {
		if (this.flashOnSetHealth) {
			float g = this.getHealth() - f;
			if (g <= 0.0F) {
				this.setHealth(f);
				if (g < 0.0F) {
					this.invulnerableTime = 10;
				}
			} else {
				this.lastHurt = g;
				this.invulnerableTime = 20;
				this.setHealth(f);
				this.hurtDuration = 10;
				this.hurtTime = this.hurtDuration;
			}
		} else {
			this.setHealth(f);
			this.flashOnSetHealth = true;
		}
	}

	@Override
	public void onUpdateAbilities() {
		this.connection.send(new ServerboundPlayerAbilitiesPacket(this.getAbilities()));
	}

	@Override
	public void setReducedDebugInfo(boolean bl) {
		super.setReducedDebugInfo(bl);
		this.minecraft.debugEntries.rebuildCurrentList();
	}

	@Override
	public boolean isLocalPlayer() {
		return true;
	}

	@Override
	public boolean isSuppressingSlidingDownLadder() {
		return !this.getAbilities().flying && super.isSuppressingSlidingDownLadder();
	}

	@Override
	public boolean canSpawnSprintParticle() {
		return !this.getAbilities().flying && super.canSpawnSprintParticle();
	}

	protected void sendRidingJump() {
		this.connection
			.send(new ServerboundPlayerCommandPacket(this, ServerboundPlayerCommandPacket.Action.START_RIDING_JUMP, Mth.floor(this.getJumpRidingScale() * 100.0F)));
	}

	public void sendOpenInventory() {
		this.connection.send(new ServerboundPlayerCommandPacket(this, ServerboundPlayerCommandPacket.Action.OPEN_INVENTORY));
	}

	public StatsCounter getStats() {
		return this.stats;
	}

	public ClientRecipeBook getRecipeBook() {
		return this.recipeBook;
	}

	public void removeRecipeHighlight(RecipeDisplayId recipeDisplayId) {
		if (this.recipeBook.willHighlight(recipeDisplayId)) {
			this.recipeBook.removeHighlight(recipeDisplayId);
			this.connection.send(new ServerboundRecipeBookSeenRecipePacket(recipeDisplayId));
		}
	}

	@Override
	public PermissionSet permissions() {
		return this.permissions;
	}

	public void setPermissions(PermissionSet permissionSet) {
		this.permissions = permissionSet;
	}

	@Override
	public void displayClientMessage(Component component, boolean bl) {
		this.minecraft.getChatListener().handleSystemMessage(component, bl);
	}

	private void moveTowardsClosestSpace(double d, double e) {
		BlockPos blockPos = BlockPos.containing(d, this.getY(), e);
		if (this.suffocatesAt(blockPos)) {
			double f = d - blockPos.getX();
			double g = e - blockPos.getZ();
			Direction direction = null;
			double h = Double.MAX_VALUE;
			Direction[] directions = new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};

			for (Direction direction2 : directions) {
				double i = direction2.getAxis().choose(f, 0.0, g);
				double j = direction2.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 - i : i;
				if (j < h && !this.suffocatesAt(blockPos.relative(direction2))) {
					h = j;
					direction = direction2;
				}
			}

			if (direction != null) {
				Vec3 vec3 = this.getDeltaMovement();
				if (direction.getAxis() == Direction.Axis.X) {
					this.setDeltaMovement(0.1 * direction.getStepX(), vec3.y, vec3.z);
				} else {
					this.setDeltaMovement(vec3.x, vec3.y, 0.1 * direction.getStepZ());
				}
			}
		}
	}

	private boolean suffocatesAt(BlockPos blockPos) {
		AABB aABB = this.getBoundingBox();
		AABB aABB2 = new AABB(blockPos.getX(), aABB.minY, blockPos.getZ(), blockPos.getX() + 1.0, aABB.maxY, blockPos.getZ() + 1.0).deflate(1.0E-7);
		return this.level().collidesWithSuffocatingBlock(this, aABB2);
	}

	public void setExperienceValues(float f, int i, int j) {
		if (f != this.experienceProgress) {
			this.setExperienceDisplayStartTickToTickCount();
		}

		this.experienceProgress = f;
		this.totalExperience = i;
		this.experienceLevel = j;
	}

	private void setExperienceDisplayStartTickToTickCount() {
		if (this.experienceDisplayStartTick == Integer.MIN_VALUE) {
			this.experienceDisplayStartTick = -2147483647;
		} else {
			this.experienceDisplayStartTick = this.tickCount;
		}
	}

	@Override
	public void handleEntityEvent(byte b) {
		switch (b) {
			case 24:
				this.setPermissions(PermissionSet.NO_PERMISSIONS);
				break;
			case 25:
				this.setPermissions(LevelBasedPermissionSet.MODERATOR);
				break;
			case 26:
				this.setPermissions(LevelBasedPermissionSet.GAMEMASTER);
				break;
			case 27:
				this.setPermissions(LevelBasedPermissionSet.ADMIN);
				break;
			case 28:
				this.setPermissions(LevelBasedPermissionSet.OWNER);
				break;
			default:
				super.handleEntityEvent(b);
		}
	}

	public void setShowDeathScreen(boolean bl) {
		this.showDeathScreen = bl;
	}

	public boolean shouldShowDeathScreen() {
		return this.showDeathScreen;
	}

	public void setDoLimitedCrafting(boolean bl) {
		this.doLimitedCrafting = bl;
	}

	public boolean getDoLimitedCrafting() {
		return this.doLimitedCrafting;
	}

	@Override
	public void playSound(SoundEvent soundEvent, float f, float g) {
		this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), soundEvent, this.getSoundSource(), f, g, false);
	}

	@Override
	public void startUsingItem(InteractionHand interactionHand) {
		ItemStack itemStack = this.getItemInHand(interactionHand);
		if (!itemStack.isEmpty() && !this.isUsingItem()) {
			super.startUsingItem(interactionHand);
			this.startedUsingItem = true;
			this.usingItemHand = interactionHand;
		}
	}

	@Override
	public boolean isUsingItem() {
		return this.startedUsingItem;
	}

	private boolean isSlowDueToUsingItem() {
		return this.isUsingItem() && !this.useItem.getOrDefault(DataComponents.USE_EFFECTS, UseEffects.DEFAULT).canSprint();
	}

	private float itemUseSpeedMultiplier() {
		return this.useItem.getOrDefault(DataComponents.USE_EFFECTS, UseEffects.DEFAULT).speedMultiplier();
	}

	@Override
	public void stopUsingItem() {
		super.stopUsingItem();
		this.startedUsingItem = false;
	}

	@Override
	public InteractionHand getUsedItemHand() {
		return Objects.requireNonNullElse(this.usingItemHand, InteractionHand.MAIN_HAND);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> entityDataAccessor) {
		super.onSyncedDataUpdated(entityDataAccessor);
		if (DATA_LIVING_ENTITY_FLAGS.equals(entityDataAccessor)) {
			boolean bl = (this.entityData.get(DATA_LIVING_ENTITY_FLAGS) & 1) > 0;
			InteractionHand interactionHand = (this.entityData.get(DATA_LIVING_ENTITY_FLAGS) & 2) > 0 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			if (bl && !this.startedUsingItem) {
				this.startUsingItem(interactionHand);
			} else if (!bl && this.startedUsingItem) {
				this.stopUsingItem();
			}
		}

		if (DATA_SHARED_FLAGS_ID.equals(entityDataAccessor) && this.isFallFlying() && !this.wasFallFlying) {
			this.minecraft.getSoundManager().play(new ElytraOnPlayerSoundInstance(this));
		}
	}

	public @Nullable PlayerRideableJumping jumpableVehicle() {
		return this.getControlledVehicle() instanceof PlayerRideableJumping playerRideableJumping && playerRideableJumping.canJump() ? playerRideableJumping : null;
	}

	public float getJumpRidingScale() {
		return this.jumpRidingScale;
	}

	@Override
	public boolean isTextFilteringEnabled() {
		return this.minecraft.isTextFilteringEnabled();
	}

	@Override
	public void openTextEdit(SignBlockEntity signBlockEntity, boolean bl) {
		if (signBlockEntity instanceof HangingSignBlockEntity hangingSignBlockEntity) {
			this.minecraft.setScreen(new HangingSignEditScreen(hangingSignBlockEntity, bl, this.minecraft.isTextFilteringEnabled()));
		} else {
			this.minecraft.setScreen(new SignEditScreen(signBlockEntity, bl, this.minecraft.isTextFilteringEnabled()));
		}
	}

	@Override
	public void openMinecartCommandBlock(MinecartCommandBlock minecartCommandBlock) {
		this.minecraft.setScreen(new MinecartCommandBlockEditScreen(minecartCommandBlock));
	}

	@Override
	public void openCommandBlock(CommandBlockEntity commandBlockEntity) {
		this.minecraft.setScreen(new CommandBlockEditScreen(commandBlockEntity));
	}

	@Override
	public void openStructureBlock(StructureBlockEntity structureBlockEntity) {
		this.minecraft.setScreen(new StructureBlockEditScreen(structureBlockEntity));
	}

	@Override
	public void openTestBlock(TestBlockEntity testBlockEntity) {
		this.minecraft.setScreen(new TestBlockEditScreen(testBlockEntity));
	}

	@Override
	public void openTestInstanceBlock(TestInstanceBlockEntity testInstanceBlockEntity) {
		this.minecraft.setScreen(new TestInstanceBlockEditScreen(testInstanceBlockEntity));
	}

	@Override
	public void openJigsawBlock(JigsawBlockEntity jigsawBlockEntity) {
		this.minecraft.setScreen(new JigsawBlockEditScreen(jigsawBlockEntity));
	}

	@Override
	public void openDialog(Holder<Dialog> holder) {
		this.connection.showDialog(holder, this.minecraft.screen);
	}

	@Override
	public void openItemGui(ItemStack itemStack, InteractionHand interactionHand) {
		WritableBookContent writableBookContent = itemStack.get(DataComponents.WRITABLE_BOOK_CONTENT);
		if (writableBookContent != null) {
			this.minecraft.setScreen(new BookEditScreen(this, itemStack, interactionHand, writableBookContent));
		}
	}

	@Override
	public void crit(Entity entity) {
		this.minecraft.particleEngine.createTrackingEmitter(entity, ParticleTypes.CRIT);
	}

	@Override
	public void magicCrit(Entity entity) {
		this.minecraft.particleEngine.createTrackingEmitter(entity, ParticleTypes.ENCHANTED_HIT);
	}

	@Override
	public boolean isShiftKeyDown() {
		return this.input.keyPresses.shift();
	}

	@Override
	public boolean isCrouching() {
		return this.crouching;
	}

	public boolean isMovingSlowly() {
		return this.isCrouching() || this.isVisuallyCrawling();
	}

	@Override
	public void applyInput() {
		if (this.isControlledCamera()) {
			Vec2 vec2 = this.modifyInput(this.input.getMoveVector());
			this.xxa = vec2.x;
			this.zza = vec2.y;
			this.jumping = this.input.keyPresses.jump();
			this.yBobO = this.yBob;
			this.xBobO = this.xBob;
			this.xBob = this.xBob + (this.getXRot() - this.xBob) * 0.5F;
			this.yBob = this.yBob + (this.getYRot() - this.yBob) * 0.5F;
		} else {
			super.applyInput();
		}
	}

	private Vec2 modifyInput(Vec2 vec2) {
		if (vec2.lengthSquared() == 0.0F) {
			return vec2;
		}

		Vec2 vec22 = vec2.scale(0.98F);
		if (this.isUsingItem() && !this.isPassenger()) {
			vec22 = vec22.scale(this.itemUseSpeedMultiplier());
		}

		if (this.isMovingSlowly()) {
			float f = (float)this.getAttributeValue(Attributes.SNEAKING_SPEED);
			vec22 = vec22.scale(f);
		}

		return modifyInputSpeedForSquareMovement(vec22);
	}

	private static Vec2 modifyInputSpeedForSquareMovement(Vec2 vec2) {
		float f = vec2.length();
		if (f <= 0.0F) {
			return vec2;
		}

		Vec2 vec22 = vec2.scale(1.0F / f);
		float g = distanceToUnitSquare(vec22);
		float h = Math.min(f * g, 1.0F);
		return vec22.scale(h);
	}

	private static float distanceToUnitSquare(Vec2 vec2) {
		float f = Math.abs(vec2.x);
		float g = Math.abs(vec2.y);
		float h = g > f ? f / g : g / f;
		return Mth.sqrt(1.0F + Mth.square(h));
	}

	protected boolean isControlledCamera() {
		return this.minecraft.getCameraEntity() == this;
	}

	public void resetPos() {
		this.setPose(Pose.STANDING);
		if (this.level() != null) {
			for (double d = this.getY(); d > this.level().getMinY() && d <= this.level().getMaxY(); d++) {
				this.setPos(this.getX(), d, this.getZ());
				if (this.level().noCollision(this)) {
					break;
				}
			}

			this.setDeltaMovement(Vec3.ZERO);
			this.setXRot(0.0F);
		}

		this.setHealth(this.getMaxHealth());
		this.deathTime = 0;
	}

	@Override
	public void aiStep() {
		if (this.sprintTriggerTime > 0) {
			this.sprintTriggerTime--;
		}

		if (!(this.minecraft.screen instanceof LevelLoadingScreen)) {
			this.handlePortalTransitionEffect(this.getActivePortalLocalTransition() == Portal.Transition.CONFUSION);
			this.processPortalCooldown();
		}

		boolean bl = this.input.keyPresses.jump();
		boolean bl2 = this.input.keyPresses.shift();
		boolean bl3 = this.input.hasForwardImpulse();
		Abilities abilities = this.getAbilities();
		this.crouching = !abilities.flying
			&& !this.isSwimming()
			&& !this.isPassenger()
			&& this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.CROUCHING)
			&& (this.isShiftKeyDown() || !this.isSleeping() && !this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.STANDING));
		this.input.tick();
		this.minecraft.getTutorial().onInput(this.input);
		boolean bl4 = false;
		if (this.autoJumpTime > 0) {
			this.autoJumpTime--;
			bl4 = true;
			this.input.makeJump();
		}

		if (!this.noPhysics) {
			this.moveTowardsClosestSpace(this.getX() - this.getBbWidth() * 0.35, this.getZ() + this.getBbWidth() * 0.35);
			this.moveTowardsClosestSpace(this.getX() - this.getBbWidth() * 0.35, this.getZ() - this.getBbWidth() * 0.35);
			this.moveTowardsClosestSpace(this.getX() + this.getBbWidth() * 0.35, this.getZ() - this.getBbWidth() * 0.35);
			this.moveTowardsClosestSpace(this.getX() + this.getBbWidth() * 0.35, this.getZ() + this.getBbWidth() * 0.35);
		}

		if (bl2 || this.isSlowDueToUsingItem() && !this.isPassenger() || this.input.keyPresses.backward()) {
			this.sprintTriggerTime = 0;
		}

		if (this.canStartSprinting()) {
			if (!bl3) {
				if (this.sprintTriggerTime > 0) {
					this.setSprinting(true);
				} else {
					this.sprintTriggerTime = this.minecraft.options.sprintWindow().get();
				}
			}

			if (this.input.keyPresses.sprint()) {
				this.setSprinting(true);
			}
		}

		if (this.isSprinting()) {
			if (this.isSwimming()) {
				if (this.shouldStopSwimSprinting()) {
					this.setSprinting(false);
				}
			} else if (this.shouldStopRunSprinting()) {
				this.setSprinting(false);
			}
		}

		boolean bl5 = false;
		if (abilities.mayfly) {
			if (this.minecraft.gameMode.isSpectator()) {
				if (!abilities.flying) {
					abilities.flying = true;
					bl5 = true;
					this.onUpdateAbilities();
				}
			} else if (!bl && this.input.keyPresses.jump() && !bl4) {
				if (this.jumpTriggerTime == 0) {
					this.jumpTriggerTime = 7;
				} else if (!this.isSwimming() && (this.getVehicle() == null || this.jumpableVehicle() != null)) {
					abilities.flying = !abilities.flying;
					if (abilities.flying && this.onGround()) {
						this.jumpFromGround();
					}

					bl5 = true;
					this.onUpdateAbilities();
					this.jumpTriggerTime = 0;
				}
			}
		}

		if (this.input.keyPresses.jump() && !bl5 && !bl && !this.onClimbable() && this.tryToStartFallFlying()) {
			this.connection.send(new ServerboundPlayerCommandPacket(this, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
		}

		this.wasFallFlying = this.isFallFlying();
		if (this.isInWater() && this.input.keyPresses.shift() && this.isAffectedByFluids()) {
			this.goDownInWater();
		}

		if (this.isEyeInFluid(FluidTags.WATER)) {
			int i = this.isSpectator() ? 10 : 1;
			this.waterVisionTime = Mth.clamp(this.waterVisionTime + i, 0, 600);
		} else if (this.waterVisionTime > 0) {
			this.isEyeInFluid(FluidTags.WATER);
			this.waterVisionTime = Mth.clamp(this.waterVisionTime - 10, 0, 600);
		}

		if (abilities.flying && this.isControlledCamera()) {
			int i = 0;
			if (this.input.keyPresses.shift()) {
				i--;
			}

			if (this.input.keyPresses.jump()) {
				i++;
			}

			if (i != 0) {
				this.setDeltaMovement(this.getDeltaMovement().add(0.0, i * abilities.getFlyingSpeed() * 3.0F, 0.0));
			}
		}

		PlayerRideableJumping playerRideableJumping = this.jumpableVehicle();
		if (playerRideableJumping != null && playerRideableJumping.getJumpCooldown() == 0) {
			if (this.jumpRidingTicks < 0) {
				this.jumpRidingTicks++;
				if (this.jumpRidingTicks == 0) {
					this.jumpRidingScale = 0.0F;
				}
			}

			if (bl && !this.input.keyPresses.jump()) {
				this.jumpRidingTicks = -10;
				playerRideableJumping.onPlayerJump(Mth.floor(this.getJumpRidingScale() * 100.0F));
				this.sendRidingJump();
			} else if (!bl && this.input.keyPresses.jump()) {
				this.jumpRidingTicks = 0;
				this.jumpRidingScale = 0.0F;
			} else if (bl) {
				this.jumpRidingTicks++;
				if (this.jumpRidingTicks < 10) {
					this.jumpRidingScale = this.jumpRidingTicks * 0.1F;
				} else {
					this.jumpRidingScale = 0.8F + 2.0F / (this.jumpRidingTicks - 9) * 0.1F;
				}
			}
		} else {
			this.jumpRidingScale = 0.0F;
		}

		super.aiStep();
		if (this.onGround() && abilities.flying && !this.minecraft.gameMode.isSpectator()) {
			abilities.flying = false;
			this.onUpdateAbilities();
		}
	}

	private boolean shouldStopRunSprinting() {
		return !this.isSprintingPossible(this.getAbilities().flying) || !this.input.hasForwardImpulse() || this.horizontalCollision && !this.minorHorizontalCollision;
	}

	private boolean shouldStopSwimSprinting() {
		return !this.isSprintingPossible(true) || !this.isInWater() || !this.input.hasForwardImpulse() && !this.onGround() && !this.input.keyPresses.shift();
	}

	public Portal.Transition getActivePortalLocalTransition() {
		return this.portalProcess == null ? Portal.Transition.NONE : this.portalProcess.getPortalLocalTransition();
	}

	@Override
	protected void tickDeath() {
		this.deathTime++;
		if (this.deathTime == 20) {
			this.remove(Entity.RemovalReason.KILLED);
		}
	}

	private void handlePortalTransitionEffect(boolean bl) {
		this.oPortalEffectIntensity = this.portalEffectIntensity;
		float f = 0.0F;
		if (bl && this.portalProcess != null && this.portalProcess.isInsidePortalThisTick()) {
			if (this.minecraft.screen != null && !this.minecraft.screen.isAllowedInPortal()) {
				if (this.minecraft.screen instanceof AbstractContainerScreen) {
					this.closeContainer();
				}

				this.minecraft.setScreen(null);
			}

			if (this.portalEffectIntensity == 0.0F) {
				this.minecraft.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(SoundEvents.PORTAL_TRIGGER, this.random.nextFloat() * 0.4F + 0.8F, 0.25F));
			}

			f = 0.0125F;
			this.portalProcess.setAsInsidePortalThisTick(false);
		} else if (this.portalEffectIntensity > 0.0F) {
			f = -0.05F;
		}

		this.portalEffectIntensity = Mth.clamp(this.portalEffectIntensity + f, 0.0F, 1.0F);
	}

	@Override
	public void rideTick() {
		super.rideTick();
		this.handsBusy = false;
		if (this.getControlledVehicle() instanceof AbstractBoat abstractBoat) {
			abstractBoat.setInput(this.input.keyPresses.left(), this.input.keyPresses.right(), this.input.keyPresses.forward(), this.input.keyPresses.backward());
			this.handsBusy = this.handsBusy
				| (this.input.keyPresses.left() || this.input.keyPresses.right() || this.input.keyPresses.forward() || this.input.keyPresses.backward());
		}
	}

	public boolean isHandsBusy() {
		return this.handsBusy;
	}

	@Override
	public void move(MoverType moverType, Vec3 vec3) {
		double d = this.getX();
		double e = this.getZ();
		super.move(moverType, vec3);
		float f = (float)(this.getX() - d);
		float g = (float)(this.getZ() - e);
		this.updateAutoJump(f, g);
		this.addWalkedDistance(Mth.length(f, g) * 0.6F);
	}

	public boolean isAutoJumpEnabled() {
		return this.autoJumpEnabled;
	}

	@Override
	public boolean shouldRotateWithMinecart() {
		return this.minecraft.options.rotateWithMinecart().get();
	}

	protected void updateAutoJump(float f, float g) {
		if (this.canAutoJump()) {
			Vec3 vec3 = this.position();
			Vec3 vec32 = vec3.add(f, 0.0, g);
			Vec3 vec33 = new Vec3(f, 0.0, g);
			float h = this.getSpeed();
			float i = (float)vec33.lengthSqr();
			if (i <= 0.001F) {
				Vec2 vec2 = this.input.getMoveVector();
				float j = h * vec2.x;
				float k = h * vec2.y;
				float l = Mth.sin(this.getYRot() * (float) (Math.PI / 180.0));
				float m = Mth.cos(this.getYRot() * (float) (Math.PI / 180.0));
				vec33 = new Vec3(j * m - k * l, vec33.y, k * m + j * l);
				i = (float)vec33.lengthSqr();
				if (i <= 0.001F) {
					return;
				}
			}

			float n = Mth.invSqrt(i);
			Vec3 vec34 = vec33.scale(n);
			Vec3 vec35 = this.getForward();
			float l = (float)(vec35.x * vec34.x + vec35.z * vec34.z);
			if (!(l < -0.15F)) {
				CollisionContext collisionContext = CollisionContext.of(this);
				BlockPos blockPos = BlockPos.containing(this.getX(), this.getBoundingBox().maxY, this.getZ());
				BlockState blockState = this.level().getBlockState(blockPos);
				if (blockState.getCollisionShape(this.level(), blockPos, collisionContext).isEmpty()) {
					blockPos = blockPos.above();
					BlockState blockState2 = this.level().getBlockState(blockPos);
					if (blockState2.getCollisionShape(this.level(), blockPos, collisionContext).isEmpty()) {
						float o = 7.0F;
						float p = 1.2F;
						if (this.hasEffect(MobEffects.JUMP_BOOST)) {
							p += (this.getEffect(MobEffects.JUMP_BOOST).getAmplifier() + 1) * 0.75F;
						}

						float q = Math.max(h * 7.0F, 1.0F / n);
						Vec3 vec36 = vec3;
						Vec3 vec37 = vec32.add(vec34.scale(q));
						float r = this.getBbWidth();
						float s = this.getBbHeight();
						AABB aABB = new AABB(vec36, vec37.add(0.0, s, 0.0)).inflate(r, 0.0, r);
						vec36 = vec36.add(0.0, 0.51F, 0.0);
						vec37 = vec37.add(0.0, 0.51F, 0.0);
						Vec3 vec38 = vec34.cross(new Vec3(0.0, 1.0, 0.0));
						Vec3 vec39 = vec38.scale(r * 0.5F);
						Vec3 vec310 = vec36.subtract(vec39);
						Vec3 vec311 = vec37.subtract(vec39);
						Vec3 vec312 = vec36.add(vec39);
						Vec3 vec313 = vec37.add(vec39);
						Iterable<VoxelShape> iterable = this.level().getCollisions(this, aABB);
						Iterator<AABB> iterator = StreamSupport.stream(iterable.spliterator(), false).flatMap(voxelShapex -> voxelShapex.toAabbs().stream()).iterator();
						float t = Float.MIN_VALUE;

						while (iterator.hasNext()) {
							AABB aABB2 = iterator.next();
							if (aABB2.intersects(vec310, vec311) || aABB2.intersects(vec312, vec313)) {
								t = (float)aABB2.maxY;
								Vec3 vec314 = aABB2.getCenter();
								BlockPos blockPos2 = BlockPos.containing(vec314);

								for (int u = 1; u < p; u++) {
									BlockPos blockPos3 = blockPos2.above(u);
									BlockState blockState3 = this.level().getBlockState(blockPos3);
									VoxelShape voxelShape;
									if (!(voxelShape = blockState3.getCollisionShape(this.level(), blockPos3, collisionContext)).isEmpty()) {
										t = (float)voxelShape.max(Direction.Axis.Y) + blockPos3.getY();
										if (t - this.getY() > p) {
											return;
										}
									}

									if (u > 1) {
										blockPos = blockPos.above();
										BlockState blockState4 = this.level().getBlockState(blockPos);
										if (!blockState4.getCollisionShape(this.level(), blockPos, collisionContext).isEmpty()) {
											return;
										}
									}
								}
								break;
							}
						}

						if (t != Float.MIN_VALUE) {
							float v = (float)(t - this.getY());
							if (!(v <= 0.5F) && !(v > p)) {
								this.autoJumpTime = 1;
							}
						}
					}
				}
			}
		}
	}

	@Override
	protected boolean isHorizontalCollisionMinor(Vec3 vec3) {
		float f = this.getYRot() * (float) (Math.PI / 180.0);
		double d = Mth.sin(f);
		double e = Mth.cos(f);
		double g = this.xxa * e - this.zza * d;
		double h = this.zza * e + this.xxa * d;
		double i = Mth.square(g) + Mth.square(h);
		double j = Mth.square(vec3.x) + Mth.square(vec3.z);
		if (!(i < 1.0E-5F) && !(j < 1.0E-5F)) {
			double k = g * vec3.x + h * vec3.z;
			double l = Math.acos(k / Math.sqrt(i * j));
			return l < 0.13962634F;
		} else {
			return false;
		}
	}

	private boolean canAutoJump() {
		return this.isAutoJumpEnabled()
			&& this.autoJumpTime <= 0
			&& this.onGround()
			&& !this.isStayingOnGroundSurface()
			&& !this.isPassenger()
			&& this.isMoving()
			&& this.getBlockJumpFactor() >= 1.0;
	}

	private boolean isMoving() {
		return this.input.getMoveVector().lengthSquared() > 0.0F;
	}

	private boolean isSprintingPossible(boolean bl) {
		return !this.isMobilityRestricted()
			&& (this.isPassenger() ? this.vehicleCanSprint(this.getVehicle()) : this.hasEnoughFoodToDoExhaustiveManoeuvres())
			&& (bl || !this.isInShallowWater());
	}

	private boolean canStartSprinting() {
		return !this.isSprinting()
			&& this.input.hasForwardImpulse()
			&& this.isSprintingPossible(this.getAbilities().flying)
			&& !this.isSlowDueToUsingItem()
			&& (!this.isFallFlying() || this.isUnderWater())
			&& (!this.isMovingSlowly() || this.isUnderWater());
	}

	private boolean vehicleCanSprint(Entity entity) {
		return entity.canSprint() && entity.isLocalInstanceAuthoritative();
	}

	public float getWaterVision() {
		if (!this.isEyeInFluid(FluidTags.WATER)) {
			return 0.0F;
		}

		float f = 600.0F;
		float g = 100.0F;
		if (this.waterVisionTime >= 600.0F) {
			return 1.0F;
		}

		float h = Mth.clamp(this.waterVisionTime / 100.0F, 0.0F, 1.0F);
		float i = this.waterVisionTime < 100.0F ? 0.0F : Mth.clamp((this.waterVisionTime - 100.0F) / 500.0F, 0.0F, 1.0F);
		return h * 0.6F + i * 0.39999998F;
	}

	public void onGameModeChanged(GameType gameType) {
		if (gameType == GameType.SPECTATOR) {
			this.setDeltaMovement(this.getDeltaMovement().with(Direction.Axis.Y, 0.0));
		}
	}

	@Override
	public boolean isUnderWater() {
		return this.wasUnderwater;
	}

	@Override
	protected boolean updateIsUnderwater() {
		boolean bl = this.wasUnderwater;
		boolean bl2 = super.updateIsUnderwater();
		if (this.isSpectator()) {
			return this.wasUnderwater;
		}

		if (!bl && bl2) {
			this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundSource.AMBIENT, 1.0F, 1.0F, false);
			this.minecraft.getSoundManager().play(new UnderwaterAmbientSoundInstances.UnderwaterAmbientSoundInstance(this));
		}

		if (bl && !bl2) {
			this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.AMBIENT_UNDERWATER_EXIT, SoundSource.AMBIENT, 1.0F, 1.0F, false);
		}

		return this.wasUnderwater;
	}

	@Override
	public Vec3 getRopeHoldPosition(float f) {
		if (this.minecraft.options.getCameraType().isFirstPerson()) {
			float g = Mth.lerp(f * 0.5F, this.getYRot(), this.yRotO) * (float) (Math.PI / 180.0);
			float h = Mth.lerp(f * 0.5F, this.getXRot(), this.xRotO) * (float) (Math.PI / 180.0);
			double d = this.getMainArm() == HumanoidArm.RIGHT ? -1.0 : 1.0;
			Vec3 vec3 = new Vec3(0.39 * d, -0.6, 0.3);
			return vec3.xRot(-h).yRot(-g).add(this.getEyePosition(f));
		} else {
			return super.getRopeHoldPosition(f);
		}
	}

	@Override
	public void updateTutorialInventoryAction(ItemStack itemStack, ItemStack itemStack2, ClickAction clickAction) {
		this.minecraft.getTutorial().onInventoryAction(itemStack, itemStack2, clickAction);
	}

	@Override
	public float getVisualRotationYInDegrees() {
		return this.getYRot();
	}

	@Override
	public void handleCreativeModeItemDrop(ItemStack itemStack) {
		this.minecraft.gameMode.handleCreativeModeItemDrop(itemStack);
	}

	@Override
	public boolean canDropItems() {
		return this.dropSpamThrottler.isUnderThreshold();
	}

	public TickThrottler getDropSpamThrottler() {
		return this.dropSpamThrottler;
	}

	public Input getLastSentInput() {
		return this.lastSentInput;
	}

	public HitResult raycastHitResult(float f, Entity entity) {
		ItemStack itemStack = this.getActiveItem();
		AttackRange attackRange = itemStack.get(DataComponents.ATTACK_RANGE);
		double d = this.blockInteractionRange();
		HitResult hitResult = null;
		if (attackRange != null) {
			hitResult = attackRange.getClosesetHit(entity, f, EntitySelector.CAN_BE_PICKED);
			if (hitResult instanceof BlockHitResult) {
				hitResult = filterHitResult(hitResult, entity.getEyePosition(f), d);
			}
		}

		if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
			double e = this.entityInteractionRange();
			hitResult = pick(entity, d, e, f);
		}

		return hitResult;
	}

	private static HitResult pick(Entity entity, double d, double e, float f) {
		double g = Math.max(d, e);
		double h = Mth.square(g);
		Vec3 vec3 = entity.getEyePosition(f);
		HitResult hitResult = entity.pick(g, f, false);
		double i = hitResult.getLocation().distanceToSqr(vec3);
		if (hitResult.getType() != HitResult.Type.MISS) {
			h = i;
			g = Math.sqrt(h);
		}

		Vec3 vec32 = entity.getViewVector(f);
		Vec3 vec33 = vec3.add(vec32.x * g, vec32.y * g, vec32.z * g);
		float j = 1.0F;
		AABB aABB = entity.getBoundingBox().expandTowards(vec32.scale(g)).inflate(1.0, 1.0, 1.0);
		EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(entity, vec3, vec33, aABB, EntitySelector.CAN_BE_PICKED, h);
		return entityHitResult != null && entityHitResult.getLocation().distanceToSqr(vec3) < i
			? filterHitResult(entityHitResult, vec3, e)
			: filterHitResult(hitResult, vec3, d);
	}

	private static HitResult filterHitResult(HitResult hitResult, Vec3 vec3, double d) {
		Vec3 vec32 = hitResult.getLocation();
		if (!vec32.closerThan(vec3, d)) {
			Vec3 vec33 = hitResult.getLocation();
			Direction direction = Direction.getApproximateNearest(vec33.x - vec3.x, vec33.y - vec3.y, vec33.z - vec3.z);
			return BlockHitResult.miss(vec33, direction, BlockPos.containing(vec33));
		} else {
			return hitResult;
		}
	}
}
