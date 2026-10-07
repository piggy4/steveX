package net.minecraft.world.entity.decoration;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class LeashFenceKnotEntity extends BlockAttachedEntity {
	public static final double OFFSET_Y = 0.375;

	public LeashFenceKnotEntity(EntityType<? extends LeashFenceKnotEntity> entityType, Level level) {
		super(entityType, level);
	}

	public LeashFenceKnotEntity(Level level, BlockPos blockPos) {
		super(EntityType.LEASH_KNOT, level, blockPos);
		this.setPos(blockPos.getX(), blockPos.getY(), blockPos.getZ());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	protected void recalculateBoundingBox() {
		this.setPosRaw(this.pos.getX() + 0.5, this.pos.getY() + 0.375, this.pos.getZ() + 0.5);
		double d = this.getType().getWidth() / 2.0;
		double e = this.getType().getHeight();
		this.setBoundingBox(new AABB(this.getX() - d, this.getY(), this.getZ() - d, this.getX() + d, this.getY() + e, this.getZ() + d));
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 1024.0;
	}

	@Override
	public void dropItem(ServerLevel serverLevel, @Nullable Entity entity) {
		this.playSound(SoundEvents.LEAD_UNTIED, 1.0F, 1.0F);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand interactionHand) {
		if (this.level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (player.getItemInHand(interactionHand).is(Items.SHEARS)) {
			InteractionResult interactionResult = super.interact(player, interactionHand);
			if (interactionResult instanceof InteractionResult.Success success && success.wasItemInteraction()) {
				return interactionResult;
			}
		}

		boolean bl = false;

		for (Leashable leashable : Leashable.leashableLeashedTo(player)) {
			if (leashable.canHaveALeashAttachedTo(this)) {
				leashable.setLeashedTo(this, true);
				bl = true;
			}
		}

		boolean bl2 = false;
		if (!bl && !player.isSecondaryUseActive()) {
			for (Leashable leashable2 : Leashable.leashableLeashedTo(this)) {
				if (leashable2.canHaveALeashAttachedTo(player)) {
					leashable2.setLeashedTo(player, true);
					bl2 = true;
				}
			}
		}

		if (!bl && !bl2) {
			return super.interact(player, interactionHand);
		}

		this.gameEvent(GameEvent.BLOCK_ATTACH, player);
		this.playSound(SoundEvents.LEAD_TIED);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void notifyLeasheeRemoved(Leashable leashable) {
		if (Leashable.leashableLeashedTo(this).isEmpty()) {
			this.discard();
		}
	}

	@Override
	public boolean survives() {
		return this.level().getBlockState(this.pos).is(BlockTags.FENCES);
	}

	public static LeashFenceKnotEntity getOrCreateKnot(Level level, BlockPos blockPos) {
		int i = blockPos.getX();
		int j = blockPos.getY();
		int k = blockPos.getZ();

		for (LeashFenceKnotEntity leashFenceKnotEntity : level.getEntitiesOfClass(
			LeashFenceKnotEntity.class, new AABB(i - 1.0, j - 1.0, k - 1.0, i + 1.0, j + 1.0, k + 1.0)
		)) {
			if (leashFenceKnotEntity.getPos().equals(blockPos)) {
				return leashFenceKnotEntity;
			}
		}

		LeashFenceKnotEntity leashFenceKnotEntity2 = new LeashFenceKnotEntity(level, blockPos);
		level.addFreshEntity(leashFenceKnotEntity2);
		return leashFenceKnotEntity2;
	}

	public void playPlacementSound() {
		this.playSound(SoundEvents.LEAD_TIED, 1.0F, 1.0F);
	}

	@Override
	public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
		return new ClientboundAddEntityPacket(this, 0, this.getPos());
	}

	@Override
	public Vec3 getRopeHoldPosition(float f) {
		return this.getPosition(f).add(0.0, 0.2, 0.0);
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(Items.LEAD);
	}
}
