package net.minecraft.world.entity.vehicle.minecart;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class MinecartCommandBlock extends AbstractMinecart {
	static final EntityDataAccessor<String> DATA_ID_COMMAND_NAME = SynchedEntityData.defineId(MinecartCommandBlock.class, EntityDataSerializers.STRING);
	static final EntityDataAccessor<Component> DATA_ID_LAST_OUTPUT = SynchedEntityData.defineId(MinecartCommandBlock.class, EntityDataSerializers.COMPONENT);
	private final BaseCommandBlock commandBlock = new MinecartCommandBlock.MinecartCommandBase();
	private static final int ACTIVATION_DELAY = 4;
	private int lastActivated;

	public MinecartCommandBlock(EntityType<? extends MinecartCommandBlock> entityType, Level level) {
		super(entityType, level);
	}

	@Override
	protected Item getDropItem() {
		return Items.MINECART;
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(Items.COMMAND_BLOCK_MINECART);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ID_COMMAND_NAME, "");
		builder.define(DATA_ID_LAST_OUTPUT, CommonComponents.EMPTY);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.commandBlock.load(valueInput);
		this.getEntityData().set(DATA_ID_COMMAND_NAME, this.getCommandBlock().getCommand());
		this.getEntityData().set(DATA_ID_LAST_OUTPUT, this.getCommandBlock().getLastOutput());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		this.commandBlock.save(valueOutput);
	}

	@Override
	public BlockState getDefaultDisplayBlockState() {
		return Blocks.COMMAND_BLOCK.defaultBlockState();
	}

	public BaseCommandBlock getCommandBlock() {
		return this.commandBlock;
	}

	@Override
	public void activateMinecart(ServerLevel serverLevel, int i, int j, int k, boolean bl) {
		if (bl && this.tickCount - this.lastActivated >= 4) {
			this.getCommandBlock().performCommand(serverLevel);
			this.lastActivated = this.tickCount;
		}
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand interactionHand) {
		if (!player.canUseGameMasterBlocks()) {
			return InteractionResult.PASS;
		}

		if (player.level().isClientSide()) {
			player.openMinecartCommandBlock(this);
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> entityDataAccessor) {
		super.onSyncedDataUpdated(entityDataAccessor);
		if (DATA_ID_LAST_OUTPUT.equals(entityDataAccessor)) {
			try {
				this.commandBlock.setLastOutput(this.getEntityData().get(DATA_ID_LAST_OUTPUT));
			} catch (Throwable var3) {
			}
		} else if (DATA_ID_COMMAND_NAME.equals(entityDataAccessor)) {
			this.commandBlock.setCommand(this.getEntityData().get(DATA_ID_COMMAND_NAME));
		}
	}

	class MinecartCommandBase extends BaseCommandBlock {
		@Override
		public void onUpdated(ServerLevel serverLevel) {
			MinecartCommandBlock.this.getEntityData().set(MinecartCommandBlock.DATA_ID_COMMAND_NAME, this.getCommand());
			MinecartCommandBlock.this.getEntityData().set(MinecartCommandBlock.DATA_ID_LAST_OUTPUT, this.getLastOutput());
		}

		@Override
		public CommandSourceStack createCommandSourceStack(ServerLevel serverLevel, CommandSource commandSource) {
			return new CommandSourceStack(
				commandSource,
				MinecartCommandBlock.this.position(),
				MinecartCommandBlock.this.getRotationVector(),
				serverLevel,
				LevelBasedPermissionSet.GAMEMASTER,
				this.getName().getString(),
				MinecartCommandBlock.this.getDisplayName(),
				serverLevel.getServer(),
				MinecartCommandBlock.this
			);
		}

		@Override
		public boolean isValid() {
			return !MinecartCommandBlock.this.isRemoved();
		}
	}
}
