package net.minecraft.world.entity.vehicle.minecart;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class MinecartSpawner extends AbstractMinecart {
	private final BaseSpawner spawner = new BaseSpawner() {
		@Override
		public void broadcastEvent(Level level, BlockPos blockPos, int i) {
			level.broadcastEntityEvent(MinecartSpawner.this, (byte)i);
		}
	};
	private final Runnable ticker;

	public MinecartSpawner(EntityType<? extends MinecartSpawner> entityType, Level level) {
		super(entityType, level);
		this.ticker = this.createTicker(level);
	}

	@Override
	protected Item getDropItem() {
		return Items.MINECART;
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(Items.MINECART);
	}

	private Runnable createTicker(Level level) {
		return level instanceof ServerLevel
			? () -> this.spawner.serverTick((ServerLevel)level, this.blockPosition())
			: () -> this.spawner.clientTick(level, this.blockPosition());
	}

	@Override
	public BlockState getDefaultDisplayBlockState() {
		return Blocks.SPAWNER.defaultBlockState();
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.spawner.load(this.level(), this.blockPosition(), valueInput);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		this.spawner.save(valueOutput);
	}

	@Override
	public void handleEntityEvent(byte b) {
		this.spawner.onEventTriggered(this.level(), b);
	}

	@Override
	public void tick() {
		super.tick();
		this.ticker.run();
	}

	public BaseSpawner getSpawner() {
		return this.spawner;
	}
}
