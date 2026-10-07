package net.minecraft.world.level.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class ButtonBlock extends FaceAttachedHorizontalDirectionalBlock {
	public static final MapCodec<ButtonBlock> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				BlockSetType.CODEC.fieldOf("block_set_type").forGetter(buttonBlock -> buttonBlock.type),
				Codec.intRange(1, 1024).fieldOf("ticks_to_stay_pressed").forGetter(buttonBlock -> buttonBlock.ticksToStayPressed),
				propertiesCodec()
			)
			.apply(instance, ButtonBlock::new)
	);
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	private final BlockSetType type;
	private final int ticksToStayPressed;
	private final Function<BlockState, VoxelShape> shapes;

	@Override
	public MapCodec<ButtonBlock> codec() {
		return CODEC;
	}

	public ButtonBlock(BlockSetType blockSetType, int i, BlockBehaviour.Properties properties) {
		super(properties.sound(blockSetType.soundType()));
		this.type = blockSetType;
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false).setValue(FACE, AttachFace.WALL));
		this.ticksToStayPressed = i;
		this.shapes = this.makeShapes();
	}

	private Function<BlockState, VoxelShape> makeShapes() {
		VoxelShape voxelShape = Block.cube(14.0);
		VoxelShape voxelShape2 = Block.cube(12.0);
		Map<AttachFace, Map<Direction, VoxelShape>> map = Shapes.rotateAttachFace(Block.boxZ(6.0, 4.0, 8.0, 16.0));
		return this.getShapeForEachState(
			blockState -> Shapes.join(
				map.get(blockState.getValue(FACE)).get(blockState.getValue(FACING)), blockState.getValue(POWERED) ? voxelShape : voxelShape2, BooleanOp.ONLY_FIRST
			)
		);
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return this.shapes.apply(blockState);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState blockState, Level level, BlockPos blockPos, Player player, BlockHitResult blockHitResult) {
		if (blockState.getValue(POWERED)) {
			return InteractionResult.CONSUME;
		}

		this.press(blockState, level, blockPos, player);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void onExplosionHit(
		BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, Explosion explosion, BiConsumer<ItemStack, BlockPos> biConsumer
	) {
		if (explosion.canTriggerBlocks() && !blockState.getValue(POWERED)) {
			this.press(blockState, serverLevel, blockPos, null);
		}

		super.onExplosionHit(blockState, serverLevel, blockPos, explosion, biConsumer);
	}

	public void press(BlockState blockState, Level level, BlockPos blockPos, @Nullable Player player) {
		level.setBlock(blockPos, blockState.setValue(POWERED, true), 3);
		this.updateNeighbours(blockState, level, blockPos);
		level.scheduleTick(blockPos, this, this.ticksToStayPressed);
		this.playSound(player, level, blockPos, true);
		level.gameEvent(player, GameEvent.BLOCK_ACTIVATE, blockPos);
	}

	protected void playSound(@Nullable Player player, LevelAccessor levelAccessor, BlockPos blockPos, boolean bl) {
		levelAccessor.playSound(bl ? player : null, blockPos, this.getSound(bl), SoundSource.BLOCKS);
	}

	protected SoundEvent getSound(boolean bl) {
		return bl ? this.type.buttonClickOn() : this.type.buttonClickOff();
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, boolean bl) {
		if (!bl && blockState.getValue(POWERED)) {
			this.updateNeighbours(blockState, serverLevel, blockPos);
		}
	}

	@Override
	protected int getSignal(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, Direction direction) {
		return blockState.getValue(POWERED) ? 15 : 0;
	}

	@Override
	protected int getDirectSignal(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, Direction direction) {
		return blockState.getValue(POWERED) && getConnectedDirection(blockState) == direction ? 15 : 0;
	}

	@Override
	protected boolean isSignalSource(BlockState blockState) {
		return true;
	}

	@Override
	protected void tick(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, RandomSource randomSource) {
		if (blockState.getValue(POWERED)) {
			this.checkPressed(blockState, serverLevel, blockPos);
		}
	}

	@Override
	protected void entityInside(
		BlockState blockState, Level level, BlockPos blockPos, Entity entity, InsideBlockEffectApplier insideBlockEffectApplier, boolean bl
	) {
		if (!level.isClientSide() && this.type.canButtonBeActivatedByArrows() && !blockState.getValue(POWERED)) {
			this.checkPressed(blockState, level, blockPos);
		}
	}

	protected void checkPressed(BlockState blockState, Level level, BlockPos blockPos) {
		AbstractArrow abstractArrow = this.type.canButtonBeActivatedByArrows()
			? level.getEntitiesOfClass(AbstractArrow.class, blockState.getShape(level, blockPos).bounds().move(blockPos)).stream().findFirst().orElse(null)
			: null;
		boolean bl = abstractArrow != null;
		boolean bl2 = blockState.getValue(POWERED);
		if (bl != bl2) {
			level.setBlock(blockPos, blockState.setValue(POWERED, bl), 3);
			this.updateNeighbours(blockState, level, blockPos);
			this.playSound(null, level, blockPos, bl);
			level.gameEvent(abstractArrow, bl ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, blockPos);
		}

		if (bl) {
			level.scheduleTick(new BlockPos(blockPos), this, this.ticksToStayPressed);
		}
	}

	private void updateNeighbours(BlockState blockState, Level level, BlockPos blockPos) {
		Direction direction = getConnectedDirection(blockState).getOpposite();
		Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(
			level, direction, direction.getAxis().isHorizontal() ? Direction.UP : blockState.getValue(FACING)
		);
		level.updateNeighborsAt(blockPos, this, orientation);
		level.updateNeighborsAt(blockPos.relative(direction), this, orientation);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, POWERED, FACE);
	}
}
