package net.minecraft.world.level.block.piston;

import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PistonMovingBlockEntity extends BlockEntity {
	private static final int TICKS_TO_EXTEND = 2;
	private static final double PUSH_OFFSET = 0.01;
	public static final double TICK_MOVEMENT = 0.51;
	private static final BlockState DEFAULT_BLOCK_STATE = Blocks.AIR.defaultBlockState();
	private static final float DEFAULT_PROGRESS = 0.0F;
	private static final boolean DEFAULT_EXTENDING = false;
	private static final boolean DEFAULT_SOURCE = false;
	private BlockState movedState = DEFAULT_BLOCK_STATE;
	private Direction direction;
	private boolean extending = false;
	private boolean isSourcePiston = false;
	private static final ThreadLocal<Direction> NOCLIP = ThreadLocal.withInitial(() -> null);
	private float progress = 0.0F;
	private float progressO = 0.0F;
	private long lastTicked;
	private int deathTicks;

	public PistonMovingBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BlockEntityType.PISTON, blockPos, blockState);
	}

	public PistonMovingBlockEntity(BlockPos blockPos, BlockState blockState, BlockState blockState2, Direction direction, boolean bl, boolean bl2) {
		this(blockPos, blockState);
		this.movedState = blockState2;
		this.direction = direction;
		this.extending = bl;
		this.isSourcePiston = bl2;
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		return this.saveCustomOnly(provider);
	}

	public boolean isExtending() {
		return this.extending;
	}

	public Direction getDirection() {
		return this.direction;
	}

	public boolean isSourcePiston() {
		return this.isSourcePiston;
	}

	public float getProgress(float f) {
		if (f > 1.0F) {
			f = 1.0F;
		}

		return Mth.lerp(f, this.progressO, this.progress);
	}

	public float getXOff(float f) {
		return this.direction.getStepX() * this.getExtendedProgress(this.getProgress(f));
	}

	public float getYOff(float f) {
		return this.direction.getStepY() * this.getExtendedProgress(this.getProgress(f));
	}

	public float getZOff(float f) {
		return this.direction.getStepZ() * this.getExtendedProgress(this.getProgress(f));
	}

	private float getExtendedProgress(float f) {
		return this.extending ? f - 1.0F : 1.0F - f;
	}

	private BlockState getCollisionRelatedBlockState() {
		return !this.isExtending() && this.isSourcePiston() && this.movedState.getBlock() instanceof PistonBaseBlock
			? Blocks.PISTON_HEAD
				.defaultBlockState()
				.setValue(PistonHeadBlock.SHORT, this.progress > 0.25F)
				.setValue(PistonHeadBlock.TYPE, this.movedState.is(Blocks.STICKY_PISTON) ? PistonType.STICKY : PistonType.DEFAULT)
				.setValue(PistonHeadBlock.FACING, this.movedState.getValue(PistonBaseBlock.FACING))
			: this.movedState;
	}

	private static void moveCollidedEntities(Level level, BlockPos blockPos, float f, PistonMovingBlockEntity pistonMovingBlockEntity) {
		Direction direction = pistonMovingBlockEntity.getMovementDirection();
		double d = f - pistonMovingBlockEntity.progress;
		VoxelShape voxelShape = pistonMovingBlockEntity.getCollisionRelatedBlockState().getCollisionShape(level, blockPos);
		if (!voxelShape.isEmpty()) {
			AABB aABB = moveByPositionAndProgress(blockPos, voxelShape.bounds(), pistonMovingBlockEntity);
			List<Entity> list = level.getEntities(null, PistonMath.getMovementArea(aABB, direction, d).minmax(aABB));
			if (!list.isEmpty()) {
				List<AABB> list2 = voxelShape.toAabbs();
				boolean bl = pistonMovingBlockEntity.movedState.is(Blocks.SLIME_BLOCK);
				Iterator var12 = list.iterator();

				while (true) {
					Entity entity;
					while (true) {
						if (!var12.hasNext()) {
							return;
						}

						entity = (Entity)var12.next();
						if (entity.getPistonPushReaction() != PushReaction.IGNORE) {
							if (!bl) {
								break;
							}

							if (!(entity instanceof ServerPlayer)) {
								Vec3 vec3 = entity.getDeltaMovement();
								double e = vec3.x;
								double g = vec3.y;
								double h = vec3.z;
								switch (direction.getAxis()) {
									case X:
										e = direction.getStepX();
										break;
									case Y:
										g = direction.getStepY();
										break;
									case Z:
										h = direction.getStepZ();
								}

								entity.setDeltaMovement(e, g, h);
								break;
							}
						}
					}

					double i = 0.0;

					for (AABB aABB2 : list2) {
						AABB aABB3 = PistonMath.getMovementArea(moveByPositionAndProgress(blockPos, aABB2, pistonMovingBlockEntity), direction, d);
						AABB aABB4 = entity.getBoundingBox();
						if (aABB3.intersects(aABB4)) {
							i = Math.max(i, getMovement(aABB3, direction, aABB4));
							if (i >= d) {
								break;
							}
						}
					}

					if (!(i <= 0.0)) {
						i = Math.min(i, d) + 0.01;
						moveEntityByPiston(direction, entity, i, direction);
						if (!pistonMovingBlockEntity.extending && pistonMovingBlockEntity.isSourcePiston) {
							fixEntityWithinPistonBase(blockPos, entity, direction, d);
						}
					}
				}
			}
		}
	}

	private static void moveEntityByPiston(Direction direction, Entity entity, double d, Direction direction2) {
		NOCLIP.set(direction);
		Vec3 vec3 = entity.position();
		entity.move(MoverType.PISTON, new Vec3(d * direction2.getStepX(), d * direction2.getStepY(), d * direction2.getStepZ()));
		entity.applyEffectsFromBlocks(vec3, entity.position());
		entity.removeLatestMovementRecording();
		NOCLIP.set(null);
	}

	private static void moveStuckEntities(Level level, BlockPos blockPos, float f, PistonMovingBlockEntity pistonMovingBlockEntity) {
		if (pistonMovingBlockEntity.isStickyForEntities()) {
			Direction direction = pistonMovingBlockEntity.getMovementDirection();
			if (direction.getAxis().isHorizontal()) {
				double d = pistonMovingBlockEntity.movedState.getCollisionShape(level, blockPos).max(Direction.Axis.Y);
				AABB aABB = moveByPositionAndProgress(blockPos, new AABB(0.0, d, 0.0, 1.0, 1.5000010000000001, 1.0), pistonMovingBlockEntity);
				double e = f - pistonMovingBlockEntity.progress;

				for (Entity entity : level.getEntities((Entity)null, aABB, entityx -> matchesStickyCritera(aABB, entityx, blockPos))) {
					moveEntityByPiston(direction, entity, e, direction);
				}
			}
		}
	}

	private static boolean matchesStickyCritera(AABB aABB, Entity entity, BlockPos blockPos) {
		return entity.getPistonPushReaction() == PushReaction.NORMAL
			&& entity.onGround()
			&& (entity.isSupportedBy(blockPos) || entity.getX() >= aABB.minX && entity.getX() <= aABB.maxX && entity.getZ() >= aABB.minZ && entity.getZ() <= aABB.maxZ);
	}

	private boolean isStickyForEntities() {
		return this.movedState.is(Blocks.HONEY_BLOCK);
	}

	public Direction getMovementDirection() {
		return this.extending ? this.direction : this.direction.getOpposite();
	}

	private static double getMovement(AABB aABB, Direction direction, AABB aABB2) {
		switch (direction) {
			case EAST:
				return aABB.maxX - aABB2.minX;
			case WEST:
				return aABB2.maxX - aABB.minX;
			case UP:
			default:
				return aABB.maxY - aABB2.minY;
			case DOWN:
				return aABB2.maxY - aABB.minY;
			case SOUTH:
				return aABB.maxZ - aABB2.minZ;
			case NORTH:
				return aABB2.maxZ - aABB.minZ;
		}
	}

	private static AABB moveByPositionAndProgress(BlockPos blockPos, AABB aABB, PistonMovingBlockEntity pistonMovingBlockEntity) {
		double d = pistonMovingBlockEntity.getExtendedProgress(pistonMovingBlockEntity.progress);
		return aABB.move(
			blockPos.getX() + d * pistonMovingBlockEntity.direction.getStepX(),
			blockPos.getY() + d * pistonMovingBlockEntity.direction.getStepY(),
			blockPos.getZ() + d * pistonMovingBlockEntity.direction.getStepZ()
		);
	}

	private static void fixEntityWithinPistonBase(BlockPos blockPos, Entity entity, Direction direction, double d) {
		AABB aABB = entity.getBoundingBox();
		AABB aABB2 = Shapes.block().bounds().move(blockPos);
		if (aABB.intersects(aABB2)) {
			Direction direction2 = direction.getOpposite();
			double e = getMovement(aABB2, direction2, aABB) + 0.01;
			double f = getMovement(aABB2, direction2, aABB.intersect(aABB2)) + 0.01;
			if (Math.abs(e - f) < 0.01) {
				e = Math.min(e, d) + 0.01;
				moveEntityByPiston(direction, entity, e, direction2);
			}
		}
	}

	public BlockState getMovedState() {
		return this.movedState;
	}

	public void finalTick() {
		if (this.level != null && (this.progressO < 1.0F || this.level.isClientSide())) {
			this.progress = 1.0F;
			this.progressO = this.progress;
			this.level.removeBlockEntity(this.worldPosition);
			this.setRemoved();
			if (this.level.getBlockState(this.worldPosition).is(Blocks.MOVING_PISTON)) {
				BlockState blockState;
				if (this.isSourcePiston) {
					blockState = Blocks.AIR.defaultBlockState();
				} else {
					blockState = Block.updateFromNeighbourShapes(this.movedState, this.level, this.worldPosition);
				}

				this.level.setBlock(this.worldPosition, blockState, 3);
				this.level
					.neighborChanged(this.worldPosition, blockState.getBlock(), ExperimentalRedstoneUtils.initialOrientation(this.level, this.getPushDirection(), null));
			}
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos blockPos, BlockState blockState) {
		this.finalTick();
	}

	public Direction getPushDirection() {
		return this.extending ? this.direction : this.direction.getOpposite();
	}

	public static void tick(Level level, BlockPos blockPos, BlockState blockState, PistonMovingBlockEntity pistonMovingBlockEntity) {
		pistonMovingBlockEntity.lastTicked = level.getGameTime();
		pistonMovingBlockEntity.progressO = pistonMovingBlockEntity.progress;
		if (pistonMovingBlockEntity.progressO >= 1.0F) {
			if (level.isClientSide() && pistonMovingBlockEntity.deathTicks < 5) {
				pistonMovingBlockEntity.deathTicks++;
			} else {
				level.removeBlockEntity(blockPos);
				pistonMovingBlockEntity.setRemoved();
				if (level.getBlockState(blockPos).is(Blocks.MOVING_PISTON)) {
					BlockState blockState2 = Block.updateFromNeighbourShapes(pistonMovingBlockEntity.movedState, level, blockPos);
					if (blockState2.isAir()) {
						level.setBlock(blockPos, pistonMovingBlockEntity.movedState, 340);
						Block.updateOrDestroy(pistonMovingBlockEntity.movedState, blockState2, level, blockPos, 3);
					} else {
						if (blockState2.hasProperty(BlockStateProperties.WATERLOGGED) && blockState2.getValue(BlockStateProperties.WATERLOGGED)) {
							blockState2 = blockState2.setValue(BlockStateProperties.WATERLOGGED, false);
						}

						level.setBlock(blockPos, blockState2, 67);
						level.neighborChanged(
							blockPos, blockState2.getBlock(), ExperimentalRedstoneUtils.initialOrientation(level, pistonMovingBlockEntity.getPushDirection(), null)
						);
					}
				}
			}
		} else {
			float f = pistonMovingBlockEntity.progress + 0.5F;
			moveCollidedEntities(level, blockPos, f, pistonMovingBlockEntity);
			moveStuckEntities(level, blockPos, f, pistonMovingBlockEntity);
			pistonMovingBlockEntity.progress = f;
			if (pistonMovingBlockEntity.progress >= 1.0F) {
				pistonMovingBlockEntity.progress = 1.0F;
			}
		}
	}

	@Override
	protected void loadAdditional(ValueInput valueInput) {
		super.loadAdditional(valueInput);
		this.movedState = valueInput.read("blockState", BlockState.CODEC).orElse(DEFAULT_BLOCK_STATE);
		this.direction = valueInput.read("facing", Direction.LEGACY_ID_CODEC).orElse(Direction.DOWN);
		this.progress = valueInput.getFloatOr("progress", 0.0F);
		this.progressO = this.progress;
		this.extending = valueInput.getBooleanOr("extending", false);
		this.isSourcePiston = valueInput.getBooleanOr("source", false);
	}

	@Override
	protected void saveAdditional(ValueOutput valueOutput) {
		super.saveAdditional(valueOutput);
		valueOutput.store("blockState", BlockState.CODEC, this.movedState);
		valueOutput.store("facing", Direction.LEGACY_ID_CODEC, this.direction);
		valueOutput.putFloat("progress", this.progressO);
		valueOutput.putBoolean("extending", this.extending);
		valueOutput.putBoolean("source", this.isSourcePiston);
	}

	public VoxelShape getCollisionShape(BlockGetter blockGetter, BlockPos blockPos) {
		VoxelShape voxelShape;
		if (!this.extending && this.isSourcePiston && this.movedState.getBlock() instanceof PistonBaseBlock) {
			voxelShape = this.movedState.setValue(PistonBaseBlock.EXTENDED, true).getCollisionShape(blockGetter, blockPos);
		} else {
			voxelShape = Shapes.empty();
		}

		Direction direction = NOCLIP.get();
		if (this.progress < 1.0 && direction == this.getMovementDirection()) {
			return voxelShape;
		}

		BlockState blockState;
		if (this.isSourcePiston()) {
			blockState = Blocks.PISTON_HEAD
				.defaultBlockState()
				.setValue(PistonHeadBlock.FACING, this.direction)
				.setValue(PistonHeadBlock.SHORT, this.extending != 1.0F - this.progress < 0.25F);
		} else {
			blockState = this.movedState;
		}

		float f = this.getExtendedProgress(this.progress);
		double d = this.direction.getStepX() * f;
		double e = this.direction.getStepY() * f;
		double g = this.direction.getStepZ() * f;
		return Shapes.or(voxelShape, blockState.getCollisionShape(blockGetter, blockPos).move(d, e, g));
	}

	public long getLastTicked() {
		return this.lastTicked;
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		if (level.holderLookup(Registries.BLOCK).get(this.movedState.getBlock().builtInRegistryHolder().key()).isEmpty()) {
			this.movedState = Blocks.AIR.defaultBlockState();
		}
	}
}
