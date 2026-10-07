package net.minecraft.gametest.framework;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TestBlock;
import net.minecraft.world.level.block.entity.TestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.TestBlockMode;

public class BlockBasedTestInstance extends GameTestInstance {
	public static final MapCodec<BlockBasedTestInstance> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(TestData.CODEC.forGetter(GameTestInstance::info)).apply(instance, BlockBasedTestInstance::new)
	);

	public BlockBasedTestInstance(TestData<Holder<TestEnvironmentDefinition>> testData) {
		super(testData);
	}

	@Override
	public void run(GameTestHelper gameTestHelper) {
		BlockPos blockPos = this.findStartBlock(gameTestHelper);
		TestBlockEntity testBlockEntity = gameTestHelper.getBlockEntity(blockPos, TestBlockEntity.class);
		testBlockEntity.trigger();
		gameTestHelper.onEachTick(
			() -> {
				List<BlockPos> list = this.findTestBlocks(gameTestHelper, TestBlockMode.ACCEPT);
				if (list.isEmpty()) {
					gameTestHelper.fail(Component.translatable("test_block.error.missing", TestBlockMode.ACCEPT.getDisplayName()));
				}

				boolean bl = list.stream().map(blockPosx -> gameTestHelper.getBlockEntity(blockPosx, TestBlockEntity.class)).anyMatch(TestBlockEntity::hasTriggered);
				if (bl) {
					gameTestHelper.succeed();
				} else {
					this.forAllTriggeredTestBlocks(
						gameTestHelper, TestBlockMode.FAIL, testBlockEntityx -> gameTestHelper.fail(Component.literal(testBlockEntityx.getMessage()))
					);
					this.forAllTriggeredTestBlocks(gameTestHelper, TestBlockMode.LOG, TestBlockEntity::trigger);
				}
			}
		);
	}

	private void forAllTriggeredTestBlocks(GameTestHelper gameTestHelper, TestBlockMode testBlockMode, Consumer<TestBlockEntity> consumer) {
		for (BlockPos blockPos : this.findTestBlocks(gameTestHelper, testBlockMode)) {
			TestBlockEntity testBlockEntity = gameTestHelper.getBlockEntity(blockPos, TestBlockEntity.class);
			if (testBlockEntity.hasTriggered()) {
				consumer.accept(testBlockEntity);
				testBlockEntity.reset();
			}
		}
	}

	private BlockPos findStartBlock(GameTestHelper gameTestHelper) {
		List<BlockPos> list = this.findTestBlocks(gameTestHelper, TestBlockMode.START);
		if (list.isEmpty()) {
			gameTestHelper.fail(Component.translatable("test_block.error.missing", TestBlockMode.START.getDisplayName()));
		}

		if (list.size() != 1) {
			gameTestHelper.fail(Component.translatable("test_block.error.too_many", TestBlockMode.START.getDisplayName()));
		}

		return list.getFirst();
	}

	private List<BlockPos> findTestBlocks(GameTestHelper gameTestHelper, TestBlockMode testBlockMode) {
		List<BlockPos> list = new ArrayList<>();
		gameTestHelper.forEveryBlockInStructure(blockPos -> {
			BlockState blockState = gameTestHelper.getBlockState(blockPos);
			if (blockState.is(Blocks.TEST_BLOCK) && blockState.getValue(TestBlock.MODE) == testBlockMode) {
				list.add(blockPos.immutable());
			}
		});
		return list;
	}

	@Override
	public MapCodec<BlockBasedTestInstance> codec() {
		return CODEC;
	}

	@Override
	protected MutableComponent typeDescription() {
		return Component.translatable("test_instance.type.block_based");
	}
}
