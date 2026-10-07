package net.minecraft.gametest.framework;

import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

public class GameTestBatchFactory {
	private static final int MAX_TESTS_PER_BATCH = 50;
	public static final GameTestBatchFactory.TestDecorator DIRECT = (reference, serverLevel) -> Stream.of(
		new GameTestInfo(reference, Rotation.NONE, serverLevel, RetryOptions.noRetries())
	);

	public static List<GameTestBatch> divideIntoBatches(
		Collection<Holder.Reference<GameTestInstance>> collection, GameTestBatchFactory.TestDecorator testDecorator, ServerLevel serverLevel
	) {
		Map<Holder<TestEnvironmentDefinition>, List<GameTestInfo>> map = collection.stream()
			.flatMap(reference -> testDecorator.decorate((Holder.Reference<GameTestInstance>)reference, serverLevel))
			.collect(Collectors.groupingBy(gameTestInfo -> gameTestInfo.getTest().batch()));
		return map.entrySet().stream().flatMap(entry -> {
			Holder<TestEnvironmentDefinition> holder = entry.getKey();
			List<GameTestInfo> list = entry.getValue();
			return Streams.mapWithIndex(Lists.partition(list, 50).stream(), (listx, l) -> toGameTestBatch(listx, holder, (int)l));
		}).toList();
	}

	public static GameTestRunner.GameTestBatcher fromGameTestInfo() {
		return fromGameTestInfo(50);
	}

	public static GameTestRunner.GameTestBatcher fromGameTestInfo(int i) {
		return collection -> {
			Map<Holder<TestEnvironmentDefinition>, List<GameTestInfo>> map = collection.stream()
				.filter(Objects::nonNull)
				.collect(Collectors.groupingBy(gameTestInfo -> gameTestInfo.getTest().batch()));
			return map.entrySet().stream().flatMap(entry -> {
				Holder<TestEnvironmentDefinition> holder = entry.getKey();
				List<GameTestInfo> list = entry.getValue();
				return Streams.mapWithIndex(Lists.partition(list, i).stream(), (listx, l) -> toGameTestBatch(List.copyOf(listx), holder, (int)l));
			}).toList();
		};
	}

	public static GameTestBatch toGameTestBatch(Collection<GameTestInfo> collection, Holder<TestEnvironmentDefinition> holder, int i) {
		return new GameTestBatch(i, collection, holder);
	}

	@FunctionalInterface
	public interface TestDecorator {
		Stream<GameTestInfo> decorate(Holder.Reference<GameTestInstance> reference, ServerLevel serverLevel);
	}
}
