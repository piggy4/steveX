package net.minecraft.gametest.framework;

import java.util.function.Consumer;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public interface GameTestInstances {
	ResourceKey<GameTestInstance> ALWAYS_PASS = create("always_pass");

	static void bootstrap(BootstrapContext<GameTestInstance> bootstrapContext) {
		HolderGetter<Consumer<GameTestHelper>> holderGetter = bootstrapContext.lookup(Registries.TEST_FUNCTION);
		HolderGetter<TestEnvironmentDefinition> holderGetter2 = bootstrapContext.lookup(Registries.TEST_ENVIRONMENT);
		bootstrapContext.register(
			ALWAYS_PASS,
			new FunctionGameTestInstance(
				BuiltinTestFunctions.ALWAYS_PASS,
				new TestData<>(holderGetter2.getOrThrow(GameTestEnvironments.DEFAULT_KEY), Identifier.withDefaultNamespace("empty"), 1, 1, false)
			)
		);
	}

	private static ResourceKey<GameTestInstance> create(String string) {
		return ResourceKey.create(Registries.TEST_INSTANCE, Identifier.withDefaultNamespace(string));
	}
}
