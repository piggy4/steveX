package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;

public class CarvedPumpkinBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<CarvedPumpkinBlock> CODEC = simpleCodec(CarvedPumpkinBlock::new);
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	private @Nullable BlockPattern snowGolemBase;
	private @Nullable BlockPattern snowGolemFull;
	private @Nullable BlockPattern ironGolemBase;
	private @Nullable BlockPattern ironGolemFull;
	private @Nullable BlockPattern copperGolemBase;
	private @Nullable BlockPattern copperGolemFull;
	private static final Predicate<BlockState> PUMPKINS_PREDICATE = blockState -> blockState.is(Blocks.CARVED_PUMPKIN) || blockState.is(Blocks.JACK_O_LANTERN);

	@Override
	public MapCodec<? extends CarvedPumpkinBlock> codec() {
		return CODEC;
	}

	public CarvedPumpkinBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void onPlace(BlockState blockState, Level level, BlockPos blockPos, BlockState blockState2, boolean bl) {
		if (!blockState2.is(blockState.getBlock())) {
			this.trySpawnGolem(level, blockPos);
		}
	}

	public boolean canSpawnGolem(LevelReader levelReader, BlockPos blockPos) {
		return this.getOrCreateSnowGolemBase().find(levelReader, blockPos) != null
			|| this.getOrCreateIronGolemBase().find(levelReader, blockPos) != null
			|| this.getOrCreateCopperGolemBase().find(levelReader, blockPos) != null;
	}

	private void trySpawnGolem(Level level, BlockPos blockPos) {
		BlockPattern.BlockPatternMatch blockPatternMatch = this.getOrCreateSnowGolemFull().find(level, blockPos);
		if (blockPatternMatch != null) {
			SnowGolem snowGolem = EntityType.SNOW_GOLEM.create(level, EntitySpawnReason.TRIGGERED);
			if (snowGolem != null) {
				spawnGolemInWorld(level, blockPatternMatch, snowGolem, blockPatternMatch.getBlock(0, 2, 0).getPos());
				return;
			}
		}

		BlockPattern.BlockPatternMatch blockPatternMatch2 = this.getOrCreateIronGolemFull().find(level, blockPos);
		if (blockPatternMatch2 != null) {
			IronGolem ironGolem = EntityType.IRON_GOLEM.create(level, EntitySpawnReason.TRIGGERED);
			if (ironGolem != null) {
				ironGolem.setPlayerCreated(true);
				spawnGolemInWorld(level, blockPatternMatch2, ironGolem, blockPatternMatch2.getBlock(1, 2, 0).getPos());
				return;
			}
		}

		BlockPattern.BlockPatternMatch blockPatternMatch3 = this.getOrCreateCopperGolemFull().find(level, blockPos);
		if (blockPatternMatch3 != null) {
			CopperGolem copperGolem = EntityType.COPPER_GOLEM.create(level, EntitySpawnReason.TRIGGERED);
			if (copperGolem != null) {
				spawnGolemInWorld(level, blockPatternMatch3, copperGolem, blockPatternMatch3.getBlock(0, 0, 0).getPos());
				this.replaceCopperBlockWithChest(level, blockPatternMatch3);
				copperGolem.spawn(this.getWeatherStateFromPattern(blockPatternMatch3));
			}
		}
	}

	private WeatheringCopper.WeatherState getWeatherStateFromPattern(BlockPattern.BlockPatternMatch blockPatternMatch) {
		BlockState blockState = blockPatternMatch.getBlock(0, 1, 0).getState();
		return blockState.getBlock() instanceof WeatheringCopper weatheringCopper
			? weatheringCopper.getAge()
			: Optional.ofNullable(HoneycombItem.WAX_OFF_BY_BLOCK.get().get(blockState.getBlock()))
				.filter(block -> block instanceof WeatheringCopper)
				.map(block -> (WeatheringCopper)block)
				.orElse((WeatheringCopper)Blocks.COPPER_BLOCK)
				.getAge();
	}

	private static void spawnGolemInWorld(Level level, BlockPattern.BlockPatternMatch blockPatternMatch, Entity entity, BlockPos blockPos) {
		clearPatternBlocks(level, blockPatternMatch);
		entity.snapTo(blockPos.getX() + 0.5, blockPos.getY() + 0.05, blockPos.getZ() + 0.5, 0.0F, 0.0F);
		level.addFreshEntity(entity);

		for (ServerPlayer serverPlayer : level.getEntitiesOfClass(ServerPlayer.class, entity.getBoundingBox().inflate(5.0))) {
			CriteriaTriggers.SUMMONED_ENTITY.trigger(serverPlayer, entity);
		}

		updatePatternBlocks(level, blockPatternMatch);
	}

	public static void clearPatternBlocks(Level level, BlockPattern.BlockPatternMatch blockPatternMatch) {
		for (int i = 0; i < blockPatternMatch.getWidth(); i++) {
			for (int j = 0; j < blockPatternMatch.getHeight(); j++) {
				BlockInWorld blockInWorld = blockPatternMatch.getBlock(i, j, 0);
				level.setBlock(blockInWorld.getPos(), Blocks.AIR.defaultBlockState(), 2);
				level.levelEvent(2001, blockInWorld.getPos(), Block.getId(blockInWorld.getState()));
			}
		}
	}

	public static void updatePatternBlocks(Level level, BlockPattern.BlockPatternMatch blockPatternMatch) {
		for (int i = 0; i < blockPatternMatch.getWidth(); i++) {
			for (int j = 0; j < blockPatternMatch.getHeight(); j++) {
				BlockInWorld blockInWorld = blockPatternMatch.getBlock(i, j, 0);
				level.updateNeighborsAt(blockInWorld.getPos(), Blocks.AIR);
			}
		}
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
		return this.defaultBlockState().setValue(FACING, blockPlaceContext.getHorizontalDirection().getOpposite());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	private BlockPattern getOrCreateSnowGolemBase() {
		if (this.snowGolemBase == null) {
			this.snowGolemBase = BlockPatternBuilder.start()
				.aisle(" ", "#", "#")
				.where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.SNOW_BLOCK)))
				.build();
		}

		return this.snowGolemBase;
	}

	private BlockPattern getOrCreateSnowGolemFull() {
		if (this.snowGolemFull == null) {
			this.snowGolemFull = BlockPatternBuilder.start()
				.aisle("^", "#", "#")
				.where('^', BlockInWorld.hasState(PUMPKINS_PREDICATE))
				.where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.SNOW_BLOCK)))
				.build();
		}

		return this.snowGolemFull;
	}

	private BlockPattern getOrCreateIronGolemBase() {
		if (this.ironGolemBase == null) {
			this.ironGolemBase = BlockPatternBuilder.start()
				.aisle("~ ~", "###", "~#~")
				.where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.IRON_BLOCK)))
				.where('~', BlockInWorld.hasState(BlockBehaviour.BlockStateBase::isAir))
				.build();
		}

		return this.ironGolemBase;
	}

	private BlockPattern getOrCreateIronGolemFull() {
		if (this.ironGolemFull == null) {
			this.ironGolemFull = BlockPatternBuilder.start()
				.aisle("~^~", "###", "~#~")
				.where('^', BlockInWorld.hasState(PUMPKINS_PREDICATE))
				.where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.IRON_BLOCK)))
				.where('~', BlockInWorld.hasState(BlockBehaviour.BlockStateBase::isAir))
				.build();
		}

		return this.ironGolemFull;
	}

	private BlockPattern getOrCreateCopperGolemBase() {
		if (this.copperGolemBase == null) {
			this.copperGolemBase = BlockPatternBuilder.start().aisle(" ", "#").where('#', BlockInWorld.hasState(blockState -> blockState.is(BlockTags.COPPER))).build();
		}

		return this.copperGolemBase;
	}

	private BlockPattern getOrCreateCopperGolemFull() {
		if (this.copperGolemFull == null) {
			this.copperGolemFull = BlockPatternBuilder.start()
				.aisle("^", "#")
				.where('^', BlockInWorld.hasState(PUMPKINS_PREDICATE))
				.where('#', BlockInWorld.hasState(blockState -> blockState.is(BlockTags.COPPER)))
				.build();
		}

		return this.copperGolemFull;
	}

	public void replaceCopperBlockWithChest(Level level, BlockPattern.BlockPatternMatch blockPatternMatch) {
		BlockInWorld blockInWorld = blockPatternMatch.getBlock(0, 1, 0);
		BlockInWorld blockInWorld2 = blockPatternMatch.getBlock(0, 0, 0);
		Direction direction = blockInWorld2.getState().getValue(FACING);
		BlockState blockState = CopperChestBlock.getFromCopperBlock(blockInWorld.getState().getBlock(), direction, level, blockInWorld.getPos());
		level.setBlock(blockInWorld.getPos(), blockState, 2);
	}
}
