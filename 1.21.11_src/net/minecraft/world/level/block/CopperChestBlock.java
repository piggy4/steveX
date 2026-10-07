package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

public class CopperChestBlock extends ChestBlock {
	public static final MapCodec<CopperChestBlock> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				WeatheringCopper.WeatherState.CODEC.fieldOf("weathering_state").forGetter(CopperChestBlock::getState),
				BuiltInRegistries.SOUND_EVENT.byNameCodec().fieldOf("open_sound").forGetter(ChestBlock::getOpenChestSound),
				BuiltInRegistries.SOUND_EVENT.byNameCodec().fieldOf("close_sound").forGetter(ChestBlock::getCloseChestSound),
				propertiesCodec()
			)
			.apply(instance, CopperChestBlock::new)
	);
	private static final Map<Block, Supplier<Block>> COPPER_TO_COPPER_CHEST_MAPPING = Map.of(
		Blocks.COPPER_BLOCK,
		() -> Blocks.COPPER_CHEST,
		Blocks.EXPOSED_COPPER,
		() -> Blocks.EXPOSED_COPPER_CHEST,
		Blocks.WEATHERED_COPPER,
		() -> Blocks.WEATHERED_COPPER_CHEST,
		Blocks.OXIDIZED_COPPER,
		() -> Blocks.OXIDIZED_COPPER_CHEST,
		Blocks.WAXED_COPPER_BLOCK,
		() -> Blocks.COPPER_CHEST,
		Blocks.WAXED_EXPOSED_COPPER,
		() -> Blocks.EXPOSED_COPPER_CHEST,
		Blocks.WAXED_WEATHERED_COPPER,
		() -> Blocks.WEATHERED_COPPER_CHEST,
		Blocks.WAXED_OXIDIZED_COPPER,
		() -> Blocks.OXIDIZED_COPPER_CHEST
	);
	private final WeatheringCopper.WeatherState weatherState;

	@Override
	public MapCodec<? extends CopperChestBlock> codec() {
		return CODEC;
	}

	public CopperChestBlock(WeatheringCopper.WeatherState weatherState, SoundEvent soundEvent, SoundEvent soundEvent2, BlockBehaviour.Properties properties) {
		super(() -> BlockEntityType.CHEST, soundEvent, soundEvent2, properties);
		this.weatherState = weatherState;
	}

	@Override
	public boolean chestCanConnectTo(BlockState blockState) {
		return blockState.is(BlockTags.COPPER_CHESTS) && blockState.hasProperty(ChestBlock.TYPE);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
		BlockState blockState = super.getStateForPlacement(blockPlaceContext);
		return getLeastOxidizedChestOfConnectedBlocks(blockState, blockPlaceContext.getLevel(), blockPlaceContext.getClickedPos());
	}

	private static BlockState getLeastOxidizedChestOfConnectedBlocks(BlockState blockState, Level level, BlockPos blockPos) {
		BlockState blockState2 = level.getBlockState(blockPos.relative(getConnectedDirection(blockState)));
		if (!blockState.getValue(ChestBlock.TYPE).equals(ChestType.SINGLE)
			&& blockState.getBlock() instanceof CopperChestBlock copperChestBlock
			&& blockState2.getBlock() instanceof CopperChestBlock copperChestBlock2) {
			BlockState blockState3 = blockState;
			BlockState blockState4 = blockState2;
			if (copperChestBlock.isWaxed() != copperChestBlock2.isWaxed()) {
				blockState3 = unwaxBlock(copperChestBlock, blockState).orElse(blockState3);
				blockState4 = unwaxBlock(copperChestBlock2, blockState2).orElse(blockState4);
			}

			Block block = copperChestBlock.weatherState.ordinal() <= copperChestBlock2.weatherState.ordinal() ? blockState3.getBlock() : blockState4.getBlock();
			return block.withPropertiesOf(blockState3);
		} else {
			return blockState;
		}
	}

	@Override
	protected BlockState updateShape(
		BlockState blockState,
		LevelReader levelReader,
		ScheduledTickAccess scheduledTickAccess,
		BlockPos blockPos,
		Direction direction,
		BlockPos blockPos2,
		BlockState blockState2,
		RandomSource randomSource
	) {
		BlockState blockState3 = super.updateShape(blockState, levelReader, scheduledTickAccess, blockPos, direction, blockPos2, blockState2, randomSource);
		if (this.chestCanConnectTo(blockState2)) {
			ChestType chestType = blockState3.getValue(ChestBlock.TYPE);
			if (!chestType.equals(ChestType.SINGLE) && getConnectedDirection(blockState3) == direction) {
				return blockState2.getBlock().withPropertiesOf(blockState3);
			}
		}

		return blockState3;
	}

	private static Optional<BlockState> unwaxBlock(CopperChestBlock copperChestBlock, BlockState blockState) {
		return !copperChestBlock.isWaxed()
			? Optional.of(blockState)
			: Optional.ofNullable(HoneycombItem.WAX_OFF_BY_BLOCK.get().get(blockState.getBlock())).map(block -> block.withPropertiesOf(blockState));
	}

	public WeatheringCopper.WeatherState getState() {
		return this.weatherState;
	}

	public static BlockState getFromCopperBlock(Block block, Direction direction, Level level, BlockPos blockPos) {
		CopperChestBlock copperChestBlock = (CopperChestBlock)COPPER_TO_COPPER_CHEST_MAPPING.getOrDefault(block, Blocks.COPPER_CHEST::asBlock).get();
		ChestType chestType = copperChestBlock.getChestType(level, blockPos, direction);
		BlockState blockState = copperChestBlock.defaultBlockState().setValue(FACING, direction).setValue(TYPE, chestType);
		return getLeastOxidizedChestOfConnectedBlocks(blockState, level, blockPos);
	}

	public boolean isWaxed() {
		return true;
	}

	@Override
	public boolean shouldChangedStateKeepBlockEntity(BlockState blockState) {
		return blockState.is(BlockTags.COPPER_CHESTS);
	}
}
