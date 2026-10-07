package net.minecraft.gametest.framework;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public record GeneratedTest(
	Map<Identifier, TestData<ResourceKey<TestEnvironmentDefinition>>> tests, ResourceKey<Consumer<GameTestHelper>> functionKey, Consumer<GameTestHelper> function
) {
	public GeneratedTest(Map<Identifier, TestData<ResourceKey<TestEnvironmentDefinition>>> map, Identifier identifier, Consumer<GameTestHelper> consumer) {
		this(map, ResourceKey.create(Registries.TEST_FUNCTION, identifier), consumer);
	}

	public GeneratedTest(Identifier identifier, TestData<ResourceKey<TestEnvironmentDefinition>> testData, Consumer<GameTestHelper> consumer) {
		this(Map.of(identifier, testData), identifier, consumer);
	}
}
