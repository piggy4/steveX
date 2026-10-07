package net.minecraft.world.entity.animal.feline;

import java.util.List;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.variant.MoonBrightnessCheck;
import net.minecraft.world.entity.variant.PriorityProvider;
import net.minecraft.world.entity.variant.SpawnPrioritySelectors;
import net.minecraft.world.entity.variant.StructureCheck;
import net.minecraft.world.level.levelgen.structure.Structure;

public interface CatVariants {
	ResourceKey<CatVariant> TABBY = createKey("tabby");
	ResourceKey<CatVariant> BLACK = createKey("black");
	ResourceKey<CatVariant> RED = createKey("red");
	ResourceKey<CatVariant> SIAMESE = createKey("siamese");
	ResourceKey<CatVariant> BRITISH_SHORTHAIR = createKey("british_shorthair");
	ResourceKey<CatVariant> CALICO = createKey("calico");
	ResourceKey<CatVariant> PERSIAN = createKey("persian");
	ResourceKey<CatVariant> RAGDOLL = createKey("ragdoll");
	ResourceKey<CatVariant> WHITE = createKey("white");
	ResourceKey<CatVariant> JELLIE = createKey("jellie");
	ResourceKey<CatVariant> ALL_BLACK = createKey("all_black");

	private static ResourceKey<CatVariant> createKey(String string) {
		return ResourceKey.create(Registries.CAT_VARIANT, Identifier.withDefaultNamespace(string));
	}

	static void bootstrap(BootstrapContext<CatVariant> bootstrapContext) {
		HolderGetter<Structure> holderGetter = bootstrapContext.lookup(Registries.STRUCTURE);
		registerForAnyConditions(bootstrapContext, TABBY, "entity/cat/tabby");
		registerForAnyConditions(bootstrapContext, BLACK, "entity/cat/black");
		registerForAnyConditions(bootstrapContext, RED, "entity/cat/red");
		registerForAnyConditions(bootstrapContext, SIAMESE, "entity/cat/siamese");
		registerForAnyConditions(bootstrapContext, BRITISH_SHORTHAIR, "entity/cat/british_shorthair");
		registerForAnyConditions(bootstrapContext, CALICO, "entity/cat/calico");
		registerForAnyConditions(bootstrapContext, PERSIAN, "entity/cat/persian");
		registerForAnyConditions(bootstrapContext, RAGDOLL, "entity/cat/ragdoll");
		registerForAnyConditions(bootstrapContext, WHITE, "entity/cat/white");
		registerForAnyConditions(bootstrapContext, JELLIE, "entity/cat/jellie");
		register(
			bootstrapContext,
			ALL_BLACK,
			"entity/cat/all_black",
			new SpawnPrioritySelectors(
				List.of(
					new PriorityProvider.Selector<>(new StructureCheck(holderGetter.getOrThrow(StructureTags.CATS_SPAWN_AS_BLACK)), 1),
					new PriorityProvider.Selector<>(new MoonBrightnessCheck(MinMaxBounds.Doubles.atLeast(0.9)), 0)
				)
			)
		);
	}

	private static void registerForAnyConditions(BootstrapContext<CatVariant> bootstrapContext, ResourceKey<CatVariant> resourceKey, String string) {
		register(bootstrapContext, resourceKey, string, SpawnPrioritySelectors.fallback(0));
	}

	private static void register(
		BootstrapContext<CatVariant> bootstrapContext, ResourceKey<CatVariant> resourceKey, String string, SpawnPrioritySelectors spawnPrioritySelectors
	) {
		bootstrapContext.register(resourceKey, new CatVariant(new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace(string)), spawnPrioritySelectors));
	}
}
