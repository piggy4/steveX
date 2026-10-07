package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SweetBerryBushBlock extends VegetationBlock implements BonemealableBlock {
	public static final MapCodec<SweetBerryBushBlock> CODEC = simpleCodec(SweetBerryBushBlock::new);
	private static final float HURT_SPEED_THRESHOLD = 0.003F;
	public static final int MAX_AGE = 3;
	public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
	private static final VoxelShape SHAPE_SAPLING = Block.column(10.0, 0.0, 8.0);
	private static final VoxelShape SHAPE_GROWING = Block.column(14.0, 0.0, 16.0);

	@Override
	public MapCodec<SweetBerryBushBlock> codec() {
		return CODEC;
	}

	public SweetBerryBushBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader levelReader, BlockPos blockPos, BlockState blockState, boolean bl) {
		return new ItemStack(Items.SWEET_BERRIES);
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return switch (blockState.getValue(AGE)) {
			case 0 -> SHAPE_SAPLING;
			case 3 -> Shapes.block();
			default -> SHAPE_GROWING;
		};
	}

	@Override
	protected boolean isRandomlyTicking(BlockState blockState) {
		return blockState.getValue(AGE) < 3;
	}

	@Override
	protected void randomTick(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, RandomSource randomSource) {
		int i = blockState.getValue(AGE);
		if (i < 3 && randomSource.nextInt(5) == 0 && serverLevel.getRawBrightness(blockPos.above(), 0) >= 9) {
			BlockState blockState2 = blockState.setValue(AGE, i + 1);
			serverLevel.setBlock(blockPos, blockState2, 2);
			serverLevel.gameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Context.of(blockState2));
		}
	}

	@Override
	protected void entityInside(
		BlockState blockState, Level level, BlockPos blockPos, Entity entity, InsideBlockEffectApplier insideBlockEffectApplier, boolean bl
	) {
		if (entity instanceof LivingEntity && entity.getType() != EntityType.FOX && entity.getType() != EntityType.BEE) {
			entity.makeStuckInBlock(blockState, new Vec3(0.8F, 0.75, 0.8F));
			if (level instanceof ServerLevel serverLevel && blockState.getValue(AGE) != 0) {
				Vec3 vec3 = entity.isClientAuthoritative() ? entity.getKnownMovement() : entity.oldPosition().subtract(entity.position());
				if (vec3.horizontalDistanceSqr() > 0.0) {
					double d = Math.abs(vec3.x());
					double e = Math.abs(vec3.z());
					if (d >= 0.003F || e >= 0.003F) {
						entity.hurtServer(serverLevel, level.damageSources().sweetBerryBush(), 1.0F);
					}
				}
			}
		}
	}

	@Override
	protected InteractionResult useItemOn(
		ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult
	) {
		int i = blockState.getValue(AGE);
		boolean bl = i == 3;
		return !bl && itemStack.is(Items.BONE_MEAL)
			? InteractionResult.PASS
			: super.useItemOn(itemStack, blockState, level, blockPos, player, interactionHand, blockHitResult);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState blockState, Level level, BlockPos blockPos, Player player, BlockHitResult blockHitResult) {
		if (blockState.getValue(AGE) > 1) {
			if (level instanceof ServerLevel serverLevel) {
				Block.dropFromBlockInteractLootTable(
					serverLevel,
					BuiltInLootTables.HARVEST_SWEET_BERRY_BUSH,
					blockState,
					level.getBlockEntity(blockPos),
					null,
					player,
					(serverLevelx, itemStack) -> Block.popResource(serverLevelx, blockPos, itemStack)
				);
				serverLevel.playSound(null, blockPos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + serverLevel.random.nextFloat() * 0.4F);
				BlockState blockState2 = blockState.setValue(AGE, 1);
				serverLevel.setBlock(blockPos, blockState2, 2);
				serverLevel.gameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Context.of(player, blockState2));
			}

			return InteractionResult.SUCCESS;
		} else {
			return super.useWithoutItem(blockState, level, blockPos, player, blockHitResult);
		}
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AGE);
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader levelReader, BlockPos blockPos, BlockState blockState) {
		return blockState.getValue(AGE) < 3;
	}

	@Override
	public boolean isBonemealSuccess(Level level, RandomSource randomSource, BlockPos blockPos, BlockState blockState) {
		return true;
	}

	@Override
	public void performBonemeal(ServerLevel serverLevel, RandomSource randomSource, BlockPos blockPos, BlockState blockState) {
		int i = Math.min(3, blockState.getValue(AGE) + 1);
		serverLevel.setBlock(blockPos, blockState.setValue(AGE, i), 2);
	}
}
