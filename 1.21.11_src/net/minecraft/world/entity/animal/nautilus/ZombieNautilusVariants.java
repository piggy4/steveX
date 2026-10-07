package net.minecraft.world.entity.animal.nautilus;

import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.animal.TemperatureVariants;
import net.minecraft.world.entity.variant.BiomeCheck;
import net.minecraft.world.entity.variant.ModelAndTexture;
import net.minecraft.world.entity.variant.SpawnPrioritySelectors;
import net.minecraft.world.level.biome.Biome;

public class ZombieNautilusVariants {
	public static final ResourceKey<ZombieNautilusVariant> TEMPERATE = createKey(TemperatureVariants.TEMPERATE);
	public static final ResourceKey<ZombieNautilusVariant> WARM = createKey(TemperatureVariants.WARM);
	public static final ResourceKey<ZombieNautilusVariant> DEFAULT = TEMPERATE;

	private static ResourceKey<ZombieNautilusVariant> createKey(Identifier identifier) {
		return ResourceKey.create(Registries.ZOMBIE_NAUTILUS_VARIANT, identifier);
	}

	public static void bootstrap(BootstrapContext<ZombieNautilusVariant> bootstrapContext) {
		register(bootstrapContext, TEMPERATE, ZombieNautilusVariant.ModelType.NORMAL, "zombie_nautilus", SpawnPrioritySelectors.fallback(0));
		register(bootstrapContext, WARM, ZombieNautilusVariant.ModelType.WARM, "zombie_nautilus_coral", BiomeTags.SPAWNS_CORAL_VARIANT_ZOMBIE_NAUTILUS);
	}

	private static void register(
		BootstrapContext<ZombieNautilusVariant> bootstrapContext,
		ResourceKey<ZombieNautilusVariant> resourceKey,
		ZombieNautilusVariant.ModelType modelType,
		String string,
		TagKey<Biome> tagKey
	) {
		HolderSet<Biome> holderSet = bootstrapContext.lookup(Registries.BIOME).getOrThrow(tagKey);
		register(bootstrapContext, resourceKey, modelType, string, SpawnPrioritySelectors.single(new BiomeCheck(holderSet), 1));
	}

	private static void register(
		BootstrapContext<ZombieNautilusVariant> bootstrapContext,
		ResourceKey<ZombieNautilusVariant> resourceKey,
		ZombieNautilusVariant.ModelType modelType,
		String string,
		SpawnPrioritySelectors spawnPrioritySelectors
	) {
		Identifier identifier = Identifier.withDefaultNamespace("entity/nautilus/" + string);
		bootstrapContext.register(resourceKey, new ZombieNautilusVariant(new ModelAndTexture<>(modelType, identifier), spawnPrioritySelectors));
	}
}
