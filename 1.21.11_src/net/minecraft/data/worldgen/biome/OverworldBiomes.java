package net.minecraft.data.worldgen.biome;

import net.minecraft.core.HolderGetter;
import net.minecraft.data.worldgen.BiomeDefaultFeatures;
import net.minecraft.data.worldgen.Carvers;
import net.minecraft.data.worldgen.placement.AquaticPlacements;
import net.minecraft.data.worldgen.placement.MiscOverworldPlacements;
import net.minecraft.data.worldgen.placement.VegetationPlacements;
import net.minecraft.sounds.Musics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.BackgroundMusic;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.modifier.FloatModifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class OverworldBiomes {
	protected static final int NORMAL_WATER_COLOR = 4159204;
	private static final int DARK_DRY_FOLIAGE_COLOR = 8082228;
	public static final int SWAMP_SKELETON_WEIGHT = 70;

	public static int calculateSkyColor(float f) {
		float g = f;
		g /= 3.0F;
		g = Mth.clamp(g, -1.0F, 1.0F);
		return ARGB.opaque(Mth.hsvToRgb(0.62222224F - g * 0.05F, 0.5F + g * 0.1F, 1.0F));
	}

	public static Biome.BiomeBuilder baseBiome(float f, float g) {
		return new Biome.BiomeBuilder()
			.hasPrecipitation(true)
			.temperature(f)
			.downfall(g)
			.setAttribute(EnvironmentAttributes.SKY_COLOR, calculateSkyColor(f))
			.specialEffects(new BiomeSpecialEffects.Builder().waterColor(4159204).build());
	}

	public static void globalOverworldGeneration(BiomeGenerationSettings.Builder builder) {
		BiomeDefaultFeatures.addDefaultCarversAndLakes(builder);
		BiomeDefaultFeatures.addDefaultCrystalFormations(builder);
		BiomeDefaultFeatures.addDefaultMonsterRoom(builder);
		BiomeDefaultFeatures.addDefaultUndergroundVariety(builder);
		BiomeDefaultFeatures.addDefaultSprings(builder);
		BiomeDefaultFeatures.addSurfaceFreezing(builder);
	}

	public static Biome oldGrowthTaiga(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2, boolean bl) {
		MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
		BiomeDefaultFeatures.farmAnimals(builder);
		builder.addSpawn(MobCategory.CREATURE, 8, new MobSpawnSettings.SpawnerData(EntityType.WOLF, 4, 4));
		builder.addSpawn(MobCategory.CREATURE, 4, new MobSpawnSettings.SpawnerData(EntityType.RABBIT, 2, 3));
		builder.addSpawn(MobCategory.CREATURE, 8, new MobSpawnSettings.SpawnerData(EntityType.FOX, 2, 4));
		if (bl) {
			BiomeDefaultFeatures.commonSpawns(builder);
		} else {
			BiomeDefaultFeatures.caveSpawns(builder);
			BiomeDefaultFeatures.monsters(builder, 100, 25, 0, 100, false);
		}

		BiomeGenerationSettings.Builder builder2 = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		globalOverworldGeneration(builder2);
		BiomeDefaultFeatures.addMossyStoneBlock(builder2);
		BiomeDefaultFeatures.addFerns(builder2);
		BiomeDefaultFeatures.addDefaultOres(builder2);
		BiomeDefaultFeatures.addDefaultSoftDisks(builder2);
		builder2.addFeature(
			GenerationStep.Decoration.VEGETAL_DECORATION, bl ? VegetationPlacements.TREES_OLD_GROWTH_SPRUCE_TAIGA : VegetationPlacements.TREES_OLD_GROWTH_PINE_TAIGA
		);
		BiomeDefaultFeatures.addDefaultFlowers(builder2);
		BiomeDefaultFeatures.addGiantTaigaVegetation(builder2);
		BiomeDefaultFeatures.addDefaultMushrooms(builder2);
		BiomeDefaultFeatures.addDefaultExtraVegetation(builder2, true);
		BiomeDefaultFeatures.addCommonBerryBushes(builder2);
		return baseBiome(bl ? 0.25F : 0.3F, 0.8F)
			.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_OLD_GROWTH_TAIGA))
			.mobSpawnSettings(builder.build())
			.generationSettings(builder2.build())
			.build();
	}

	public static Biome sparseJungle(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2) {
		MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
		BiomeDefaultFeatures.baseJungleSpawns(builder);
		builder.addSpawn(MobCategory.CREATURE, 8, new MobSpawnSettings.SpawnerData(EntityType.WOLF, 2, 4));
		return baseJungle(holderGetter, holderGetter2, 0.8F, false, true, false)
			.mobSpawnSettings(builder.build())
			.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_SPARSE_JUNGLE))
			.build();
	}

	public static Biome jungle(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2) {
		MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
		BiomeDefaultFeatures.baseJungleSpawns(builder);
		builder.addSpawn(MobCategory.CREATURE, 40, new MobSpawnSettings.SpawnerData(EntityType.PARROT, 1, 2))
			.addSpawn(MobCategory.MONSTER, 2, new MobSpawnSettings.SpawnerData(EntityType.OCELOT, 1, 3))
			.addSpawn(MobCategory.CREATURE, 1, new MobSpawnSettings.SpawnerData(EntityType.PANDA, 1, 2));
		return baseJungle(holderGetter, holderGetter2, 0.9F, false, false, true)
			.mobSpawnSettings(builder.build())
			.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_JUNGLE))
			.setAttribute(EnvironmentAttributes.INCREASED_FIRE_BURNOUT, true)
			.build();
	}

	public static Biome bambooJungle(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2) {
		MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
		BiomeDefaultFeatures.baseJungleSpawns(builder);
		builder.addSpawn(MobCategory.CREATURE, 40, new MobSpawnSettings.SpawnerData(EntityType.PARROT, 1, 2))
			.addSpawn(MobCategory.CREATURE, 80, new MobSpawnSettings.SpawnerData(EntityType.PANDA, 1, 2))
			.addSpawn(MobCategory.MONSTER, 2, new MobSpawnSettings.SpawnerData(EntityType.OCELOT, 1, 1));
		return baseJungle(holderGetter, holderGetter2, 0.9F, true, false, true)
			.mobSpawnSettings(builder.build())
			.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_BAMBOO_JUNGLE))
			.setAttribute(EnvironmentAttributes.INCREASED_FIRE_BURNOUT, true)
			.build();
	}

	public static Biome.BiomeBuilder baseJungle(
		HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2, float f, boolean bl, boolean bl2, boolean bl3
	) {
		BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		globalOverworldGeneration(builder);
		BiomeDefaultFeatures.addDefaultOres(builder);
		BiomeDefaultFeatures.addDefaultSoftDisks(builder);
		if (bl) {
			BiomeDefaultFeatures.addBambooVegetation(builder);
		} else {
			if (bl3) {
				BiomeDefaultFeatures.addLightBambooVegetation(builder);
			}

			if (bl2) {
				BiomeDefaultFeatures.addSparseJungleTrees(builder);
			} else {
				BiomeDefaultFeatures.addJungleTrees(builder);
			}
		}

		BiomeDefaultFeatures.addWarmFlowers(builder);
		BiomeDefaultFeatures.addJungleGrass(builder);
		BiomeDefaultFeatures.addDefaultMushrooms(builder);
		BiomeDefaultFeatures.addDefaultExtraVegetation(builder, true);
		BiomeDefaultFeatures.addJungleVines(builder);
		if (bl2) {
			BiomeDefaultFeatures.addSparseJungleMelons(builder);
		} else {
			BiomeDefaultFeatures.addJungleMelons(builder);
		}

		return baseBiome(0.95F, f).generationSettings(builder.build());
	}

	public static Biome windsweptHills(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2, boolean bl) {
		MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
		BiomeDefaultFeatures.farmAnimals(builder);
		builder.addSpawn(MobCategory.CREATURE, 5, new MobSpawnSettings.SpawnerData(EntityType.LLAMA, 4, 6));
		BiomeDefaultFeatures.commonSpawns(builder);
		BiomeGenerationSettings.Builder builder2 = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		globalOverworldGeneration(builder2);
		BiomeDefaultFeatures.addDefaultOres(builder2);
		BiomeDefaultFeatures.addDefaultSoftDisks(builder2);
		if (bl) {
			BiomeDefaultFeatures.addMountainForestTrees(builder2);
		} else {
			BiomeDefaultFeatures.addMountainTrees(builder2);
		}

		BiomeDefaultFeatures.addBushes(builder2);
		BiomeDefaultFeatures.addDefaultFlowers(builder2);
		BiomeDefaultFeatures.addDefaultGrass(builder2);
		BiomeDefaultFeatures.addDefaultMushrooms(builder2);
		BiomeDefaultFeatures.addDefaultExtraVegetation(builder2, true);
		BiomeDefaultFeatures.addExtraEmeralds(builder2);
		BiomeDefaultFeatures.addInfestedStone(builder2);
		return baseBiome(0.2F, 0.3F).mobSpawnSettings(builder.build()).generationSettings(builder2.build()).build();
	}

	public static Biome desert(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2) {
		MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
		BiomeDefaultFeatures.desertSpawns(builder);
		BiomeGenerationSettings.Builder builder2 = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		BiomeDefaultFeatures.addFossilDecoration(builder2);
		globalOverworldGeneration(builder2);
		BiomeDefaultFeatures.addDefaultOres(builder2);
		BiomeDefaultFeatures.addDefaultSoftDisks(builder2);
		BiomeDefaultFeatures.addDefaultFlowers(builder2);
		BiomeDefaultFeatures.addDefaultGrass(builder2);
		BiomeDefaultFeatures.addDesertVegetation(builder2);
		BiomeDefaultFeatures.addDefaultMushrooms(builder2);
		BiomeDefaultFeatures.addDesertExtraVegetation(builder2);
		BiomeDefaultFeatures.addDesertExtraDecoration(builder2);
		return baseBiome(2.0F, 0.0F)
			.hasPrecipitation(false)
			.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_DESERT))
			.setAttribute(EnvironmentAttributes.SNOW_GOLEM_MELTS, true)
			.mobSpawnSettings(builder.build())
			.generationSettings(builder2.build())
			.build();
	}

	public static Biome plains(
		HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2, boolean bl, boolean bl2, boolean bl3
	) {
		MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
		BiomeGenerationSettings.Builder builder2 = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		globalOverworldGeneration(builder2);
		if (bl2) {
			builder.creatureGenerationProbability(0.07F);
			BiomeDefaultFeatures.snowySpawns(builder, !bl3);
			if (bl3) {
				builder2.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, MiscOverworldPlacements.ICE_SPIKE);
				builder2.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, MiscOverworldPlacements.ICE_PATCH);
			}
		} else {
			BiomeDefaultFeatures.plainsSpawns(builder);
			BiomeDefaultFeatures.addPlainGrass(builder2);
			if (bl) {
				builder2.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.PATCH_SUNFLOWER);
			} else {
				BiomeDefaultFeatures.addBushes(builder2);
			}
		}

		BiomeDefaultFeatures.addDefaultOres(builder2);
		BiomeDefaultFeatures.addDefaultSoftDisks(builder2);
		if (bl2) {
			BiomeDefaultFeatures.addSnowyTrees(builder2);
			BiomeDefaultFeatures.addDefaultFlowers(builder2);
			BiomeDefaultFeatures.addDefaultGrass(builder2);
		} else {
			BiomeDefaultFeatures.addPlainVegetation(builder2);
		}

		BiomeDefaultFeatures.addDefaultMushrooms(builder2);
		BiomeDefaultFeatures.addDefaultExtraVegetation(builder2, true);
		return baseBiome(bl2 ? 0.0F : 0.8F, bl2 ? 0.5F : 0.4F).mobSpawnSettings(builder.build()).generationSettings(builder2.build()).build();
	}

	public static Biome mushroomFields(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2) {
		MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
		BiomeDefaultFeatures.mooshroomSpawns(builder);
		BiomeGenerationSettings.Builder builder2 = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		globalOverworldGeneration(builder2);
		BiomeDefaultFeatures.addDefaultOres(builder2);
		BiomeDefaultFeatures.addDefaultSoftDisks(builder2);
		BiomeDefaultFeatures.addMushroomFieldVegetation(builder2);
		BiomeDefaultFeatures.addNearWaterVegetation(builder2);
		return baseBiome(0.9F, 1.0F)
			.setAttribute(EnvironmentAttributes.INCREASED_FIRE_BURNOUT, true)
			.setAttribute(EnvironmentAttributes.CAN_PILLAGER_PATROL_SPAWN, false)
			.mobSpawnSettings(builder.build())
			.generationSettings(builder2.build())
			.build();
	}

	public static Biome savanna(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2, boolean bl, boolean bl2) {
		BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		globalOverworldGeneration(builder);
		if (!bl) {
			BiomeDefaultFeatures.addSavannaGrass(builder);
		}

		BiomeDefaultFeatures.addDefaultOres(builder);
		BiomeDefaultFeatures.addDefaultSoftDisks(builder);
		if (bl) {
			BiomeDefaultFeatures.addShatteredSavannaTrees(builder);
			BiomeDefaultFeatures.addDefaultFlowers(builder);
			BiomeDefaultFeatures.addShatteredSavannaGrass(builder);
		} else {
			BiomeDefaultFeatures.addSavannaTrees(builder);
			BiomeDefaultFeatures.addWarmFlowers(builder);
			BiomeDefaultFeatures.addSavannaExtraGrass(builder);
		}

		BiomeDefaultFeatures.addDefaultMushrooms(builder);
		BiomeDefaultFeatures.addDefaultExtraVegetation(builder, true);
		MobSpawnSettings.Builder builder2 = new MobSpawnSettings.Builder();
		BiomeDefaultFeatures.farmAnimals(builder2);
		builder2.addSpawn(MobCategory.CREATURE, 1, new MobSpawnSettings.SpawnerData(EntityType.HORSE, 2, 6))
			.addSpawn(MobCategory.CREATURE, 1, new MobSpawnSettings.SpawnerData(EntityType.DONKEY, 1, 1))
			.addSpawn(MobCategory.CREATURE, 10, new MobSpawnSettings.SpawnerData(EntityType.ARMADILLO, 2, 3));
		BiomeDefaultFeatures.commonSpawnWithZombieHorse(builder2);
		if (bl2) {
			builder2.addSpawn(MobCategory.CREATURE, 8, new MobSpawnSettings.SpawnerData(EntityType.LLAMA, 4, 4));
			builder2.addSpawn(MobCategory.CREATURE, 8, new MobSpawnSettings.SpawnerData(EntityType.WOLF, 4, 8));
		}

		return baseBiome(2.0F, 0.0F)
			.hasPrecipitation(false)
			.setAttribute(EnvironmentAttributes.SNOW_GOLEM_MELTS, true)
			.mobSpawnSettings(builder2.build())
			.generationSettings(builder.build())
			.build();
	}

	public static Biome badlands(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2, boolean bl) {
		MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
		BiomeDefaultFeatures.farmAnimals(builder);
		BiomeDefaultFeatures.commonSpawns(builder);
		builder.addSpawn(MobCategory.CREATURE, 6, new MobSpawnSettings.SpawnerData(EntityType.ARMADILLO, 1, 2));
		builder.creatureGenerationProbability(0.03F);
		if (bl) {
			builder.addSpawn(MobCategory.CREATURE, 2, new MobSpawnSettings.SpawnerData(EntityType.WOLF, 4, 8));
			builder.creatureGenerationProbability(0.04F);
		}

		BiomeGenerationSettings.Builder builder2 = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		globalOverworldGeneration(builder2);
		BiomeDefaultFeatures.addDefaultOres(builder2);
		BiomeDefaultFeatures.addExtraGold(builder2);
		BiomeDefaultFeatures.addDefaultSoftDisks(builder2);
		if (bl) {
			BiomeDefaultFeatures.addBadlandsTrees(builder2);
		}

		BiomeDefaultFeatures.addBadlandGrass(builder2);
		BiomeDefaultFeatures.addDefaultMushrooms(builder2);
		BiomeDefaultFeatures.addBadlandExtraVegetation(builder2);
		return baseBiome(2.0F, 0.0F)
			.hasPrecipitation(false)
			.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_BADLANDS))
			.setAttribute(EnvironmentAttributes.SNOW_GOLEM_MELTS, true)
			.specialEffects(new BiomeSpecialEffects.Builder().waterColor(4159204).foliageColorOverride(10387789).grassColorOverride(9470285).build())
			.mobSpawnSettings(builder.build())
			.generationSettings(builder2.build())
			.build();
	}

	public static Biome.BiomeBuilder baseOcean() {
		return baseBiome(0.5F, 0.5F).setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, BackgroundMusic.OVERWORLD.withUnderwater(Musics.UNDER_WATER));
	}

	public static BiomeGenerationSettings.Builder baseOceanGeneration(
		HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2
	) {
		BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		globalOverworldGeneration(builder);
		BiomeDefaultFeatures.addDefaultOres(builder);
		BiomeDefaultFeatures.addDefaultSoftDisks(builder);
		BiomeDefaultFeatures.addWaterTrees(builder);
		BiomeDefaultFeatures.addDefaultFlowers(builder);
		BiomeDefaultFeatures.addDefaultGrass(builder);
		BiomeDefaultFeatures.addDefaultMushrooms(builder);
		BiomeDefaultFeatures.addDefaultExtraVegetation(builder, true);
		return builder;
	}

	public static Biome coldOcean(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2, boolean bl) {
		MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
		BiomeDefaultFeatures.oceanSpawns(builder, 3, 4, 15);
		builder.addSpawn(MobCategory.WATER_AMBIENT, 15, new MobSpawnSettings.SpawnerData(EntityType.SALMON, 1, 5));
		builder.addSpawn(MobCategory.WATER_CREATURE, 2, new MobSpawnSettings.SpawnerData(EntityType.NAUTILUS, 1, 1));
		BiomeGenerationSettings.Builder builder2 = baseOceanGeneration(holderGetter, holderGetter2);
		builder2.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, bl ? AquaticPlacements.SEAGRASS_DEEP_COLD : AquaticPlacements.SEAGRASS_COLD);
		BiomeDefaultFeatures.addColdOceanExtraVegetation(builder2);
		return baseOcean()
			.specialEffects(new BiomeSpecialEffects.Builder().waterColor(4020182).build())
			.mobSpawnSettings(builder.build())
			.generationSettings(builder2.build())
			.build();
	}

	public static Biome ocean(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2, boolean bl) {
		MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
		BiomeDefaultFeatures.oceanSpawns(builder, 1, 4, 10);
		builder.addSpawn(MobCategory.WATER_CREATURE, 1, new MobSpawnSettings.SpawnerData(EntityType.DOLPHIN, 1, 2))
			.addSpawn(MobCategory.WATER_CREATURE, 5, new MobSpawnSettings.SpawnerData(EntityType.NAUTILUS, 1, 1));
		BiomeGenerationSettings.Builder builder2 = baseOceanGeneration(holderGetter, holderGetter2);
		builder2.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, bl ? AquaticPlacements.SEAGRASS_DEEP : AquaticPlacements.SEAGRASS_NORMAL);
		BiomeDefaultFeatures.addColdOceanExtraVegetation(builder2);
		return baseOcean().mobSpawnSettings(builder.build()).generationSettings(builder2.build()).build();
	}

	public static Biome lukeWarmOcean(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2, boolean bl) {
		MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
		if (bl) {
			BiomeDefaultFeatures.oceanSpawns(builder, 8, 4, 8);
		} else {
			BiomeDefaultFeatures.oceanSpawns(builder, 10, 2, 15);
		}

		builder.addSpawn(MobCategory.WATER_AMBIENT, 5, new MobSpawnSettings.SpawnerData(EntityType.PUFFERFISH, 1, 3))
			.addSpawn(MobCategory.WATER_AMBIENT, 25, new MobSpawnSettings.SpawnerData(EntityType.TROPICAL_FISH, 8, 8))
			.addSpawn(MobCategory.WATER_CREATURE, 2, new MobSpawnSettings.SpawnerData(EntityType.DOLPHIN, 1, 2))
			.addSpawn(MobCategory.WATER_CREATURE, 5, new MobSpawnSettings.SpawnerData(EntityType.NAUTILUS, 1, 1));
		BiomeGenerationSettings.Builder builder2 = baseOceanGeneration(holderGetter, holderGetter2);
		builder2.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, bl ? AquaticPlacements.SEAGRASS_DEEP_WARM : AquaticPlacements.SEAGRASS_WARM);
		BiomeDefaultFeatures.addLukeWarmKelp(builder2);
		return baseOcean()
			.setAttribute(EnvironmentAttributes.WATER_FOG_COLOR, -16509389)
			.specialEffects(new BiomeSpecialEffects.Builder().waterColor(4566514).build())
			.mobSpawnSettings(builder.build())
			.generationSettings(builder2.build())
			.build();
	}

	public static Biome warmOcean(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2) {
		MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder()
			.addSpawn(MobCategory.WATER_AMBIENT, 15, new MobSpawnSettings.SpawnerData(EntityType.PUFFERFISH, 1, 3))
			.addSpawn(MobCategory.WATER_CREATURE, 5, new MobSpawnSettings.SpawnerData(EntityType.NAUTILUS, 1, 1));
		BiomeDefaultFeatures.warmOceanSpawns(builder, 10, 4);
		BiomeGenerationSettings.Builder builder2 = baseOceanGeneration(holderGetter, holderGetter2)
			.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, AquaticPlacements.WARM_OCEAN_VEGETATION)
			.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, AquaticPlacements.SEAGRASS_WARM)
			.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, AquaticPlacements.SEA_PICKLE);
		return baseOcean()
			.setAttribute(EnvironmentAttributes.WATER_FOG_COLOR, -16507085)
			.specialEffects(new BiomeSpecialEffects.Builder().waterColor(4445678).build())
			.mobSpawnSettings(builder.build())
			.generationSettings(builder2.build())
			.build();
	}

	public static Biome frozenOcean(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2, boolean bl) {
		MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder()
			.addSpawn(MobCategory.WATER_CREATURE, 1, new MobSpawnSettings.SpawnerData(EntityType.SQUID, 1, 4))
			.addSpawn(MobCategory.WATER_AMBIENT, 15, new MobSpawnSettings.SpawnerData(EntityType.SALMON, 1, 5))
			.addSpawn(MobCategory.CREATURE, 1, new MobSpawnSettings.SpawnerData(EntityType.POLAR_BEAR, 1, 2))
			.addSpawn(MobCategory.WATER_CREATURE, 2, new MobSpawnSettings.SpawnerData(EntityType.NAUTILUS, 1, 1));
		BiomeDefaultFeatures.commonSpawns(builder);
		builder.addSpawn(MobCategory.MONSTER, 5, new MobSpawnSettings.SpawnerData(EntityType.DROWNED, 1, 1));
		float f = bl ? 0.5F : 0.0F;
		BiomeGenerationSettings.Builder builder2 = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		BiomeDefaultFeatures.addIcebergs(builder2);
		globalOverworldGeneration(builder2);
		BiomeDefaultFeatures.addBlueIce(builder2);
		BiomeDefaultFeatures.addDefaultOres(builder2);
		BiomeDefaultFeatures.addDefaultSoftDisks(builder2);
		BiomeDefaultFeatures.addWaterTrees(builder2);
		BiomeDefaultFeatures.addDefaultFlowers(builder2);
		BiomeDefaultFeatures.addDefaultGrass(builder2);
		BiomeDefaultFeatures.addDefaultMushrooms(builder2);
		BiomeDefaultFeatures.addDefaultExtraVegetation(builder2, true);
		return baseBiome(f, 0.5F)
			.temperatureAdjustment(Biome.TemperatureModifier.FROZEN)
			.specialEffects(new BiomeSpecialEffects.Builder().waterColor(3750089).build())
			.mobSpawnSettings(builder.build())
			.generationSettings(builder2.build())
			.build();
	}

	public static Biome forest(
		HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2, boolean bl, boolean bl2, boolean bl3
	) {
		BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		globalOverworldGeneration(builder);
		BackgroundMusic backgroundMusic;
		if (bl3) {
			backgroundMusic = new BackgroundMusic(SoundEvents.MUSIC_BIOME_FLOWER_FOREST);
			builder.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.FLOWER_FOREST_FLOWERS);
		} else {
			backgroundMusic = new BackgroundMusic(SoundEvents.MUSIC_BIOME_FOREST);
			BiomeDefaultFeatures.addForestFlowers(builder);
		}

		BiomeDefaultFeatures.addDefaultOres(builder);
		BiomeDefaultFeatures.addDefaultSoftDisks(builder);
		if (bl3) {
			builder.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.TREES_FLOWER_FOREST);
			builder.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.FLOWER_FLOWER_FOREST);
			BiomeDefaultFeatures.addDefaultGrass(builder);
		} else {
			if (bl) {
				BiomeDefaultFeatures.addBirchForestFlowers(builder);
				if (bl2) {
					BiomeDefaultFeatures.addTallBirchTrees(builder);
				} else {
					BiomeDefaultFeatures.addBirchTrees(builder);
				}
			} else {
				BiomeDefaultFeatures.addOtherBirchTrees(builder);
			}

			BiomeDefaultFeatures.addBushes(builder);
			BiomeDefaultFeatures.addDefaultFlowers(builder);
			BiomeDefaultFeatures.addForestGrass(builder);
		}

		BiomeDefaultFeatures.addDefaultMushrooms(builder);
		BiomeDefaultFeatures.addDefaultExtraVegetation(builder, true);
		MobSpawnSettings.Builder builder2 = new MobSpawnSettings.Builder();
		BiomeDefaultFeatures.farmAnimals(builder2);
		BiomeDefaultFeatures.commonSpawns(builder2);
		if (bl3) {
			builder2.addSpawn(MobCategory.CREATURE, 4, new MobSpawnSettings.SpawnerData(EntityType.RABBIT, 2, 3));
		} else if (!bl) {
			builder2.addSpawn(MobCategory.CREATURE, 5, new MobSpawnSettings.SpawnerData(EntityType.WOLF, 4, 4));
		}

		return baseBiome(bl ? 0.6F : 0.7F, bl ? 0.6F : 0.8F)
			.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, backgroundMusic)
			.mobSpawnSettings(builder2.build())
			.generationSettings(builder.build())
			.build();
	}

	public static Biome taiga(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2, boolean bl) {
		MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
		BiomeDefaultFeatures.farmAnimals(builder);
		builder.addSpawn(MobCategory.CREATURE, 8, new MobSpawnSettings.SpawnerData(EntityType.WOLF, 4, 4))
			.addSpawn(MobCategory.CREATURE, 4, new MobSpawnSettings.SpawnerData(EntityType.RABBIT, 2, 3))
			.addSpawn(MobCategory.CREATURE, 8, new MobSpawnSettings.SpawnerData(EntityType.FOX, 2, 4));
		BiomeDefaultFeatures.commonSpawns(builder);
		BiomeGenerationSettings.Builder builder2 = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		globalOverworldGeneration(builder2);
		BiomeDefaultFeatures.addFerns(builder2);
		BiomeDefaultFeatures.addDefaultOres(builder2);
		BiomeDefaultFeatures.addDefaultSoftDisks(builder2);
		BiomeDefaultFeatures.addTaigaTrees(builder2);
		BiomeDefaultFeatures.addDefaultFlowers(builder2);
		BiomeDefaultFeatures.addTaigaGrass(builder2);
		BiomeDefaultFeatures.addDefaultExtraVegetation(builder2, true);
		if (bl) {
			BiomeDefaultFeatures.addRareBerryBushes(builder2);
		} else {
			BiomeDefaultFeatures.addCommonBerryBushes(builder2);
		}

		int i = bl ? 4020182 : 4159204;
		return baseBiome(bl ? -0.5F : 0.25F, bl ? 0.4F : 0.8F)
			.specialEffects(new BiomeSpecialEffects.Builder().waterColor(i).build())
			.mobSpawnSettings(builder.build())
			.generationSettings(builder2.build())
			.build();
	}

	public static Biome darkForest(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2, boolean bl) {
		MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
		if (!bl) {
			BiomeDefaultFeatures.farmAnimals(builder);
		}

		BiomeDefaultFeatures.commonSpawns(builder);
		BiomeGenerationSettings.Builder builder2 = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		globalOverworldGeneration(builder2);
		builder2.addFeature(
			GenerationStep.Decoration.VEGETAL_DECORATION, bl ? VegetationPlacements.PALE_GARDEN_VEGETATION : VegetationPlacements.DARK_FOREST_VEGETATION
		);
		if (!bl) {
			BiomeDefaultFeatures.addForestFlowers(builder2);
		} else {
			builder2.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.PALE_MOSS_PATCH);
			builder2.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.PALE_GARDEN_FLOWERS);
		}

		BiomeDefaultFeatures.addDefaultOres(builder2);
		BiomeDefaultFeatures.addDefaultSoftDisks(builder2);
		if (!bl) {
			BiomeDefaultFeatures.addDefaultFlowers(builder2);
		} else {
			builder2.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.FLOWER_PALE_GARDEN);
		}

		BiomeDefaultFeatures.addForestGrass(builder2);
		if (!bl) {
			BiomeDefaultFeatures.addDefaultMushrooms(builder2);
			BiomeDefaultFeatures.addLeafLitterPatch(builder2);
		}

		BiomeDefaultFeatures.addDefaultExtraVegetation(builder2, true);
		EnvironmentAttributeMap environmentAttributeMap = EnvironmentAttributeMap.builder()
			.set(EnvironmentAttributes.SKY_COLOR, -4605511)
			.set(EnvironmentAttributes.FOG_COLOR, -8292496)
			.set(EnvironmentAttributes.WATER_FOG_COLOR, -11179648)
			.set(EnvironmentAttributes.BACKGROUND_MUSIC, BackgroundMusic.EMPTY)
			.set(EnvironmentAttributes.MUSIC_VOLUME, 0.0F)
			.build();
		EnvironmentAttributeMap environmentAttributeMap2 = EnvironmentAttributeMap.builder()
			.set(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_FOREST))
			.build();
		return baseBiome(0.7F, 0.8F)
			.putAttributes(bl ? environmentAttributeMap : environmentAttributeMap2)
			.specialEffects(
				bl
					? new BiomeSpecialEffects.Builder()
						.waterColor(7768221)
						.grassColorOverride(7832178)
						.foliageColorOverride(8883574)
						.dryFoliageColorOverride(10528412)
						.build()
					: new BiomeSpecialEffects.Builder()
						.waterColor(4159204)
						.dryFoliageColorOverride(8082228)
						.grassColorModifier(BiomeSpecialEffects.GrassColorModifier.DARK_FOREST)
						.build()
			)
			.mobSpawnSettings(builder.build())
			.generationSettings(builder2.build())
			.build();
	}

	public static Biome swamp(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2) {
		MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
		BiomeDefaultFeatures.farmAnimals(builder);
		BiomeDefaultFeatures.swampSpawns(builder, 70);
		BiomeGenerationSettings.Builder builder2 = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		BiomeDefaultFeatures.addFossilDecoration(builder2);
		globalOverworldGeneration(builder2);
		BiomeDefaultFeatures.addDefaultOres(builder2);
		BiomeDefaultFeatures.addSwampClayDisk(builder2);
		BiomeDefaultFeatures.addSwampVegetation(builder2);
		BiomeDefaultFeatures.addDefaultMushrooms(builder2);
		BiomeDefaultFeatures.addSwampExtraVegetation(builder2);
		builder2.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, AquaticPlacements.SEAGRASS_SWAMP);
		return baseBiome(0.8F, 0.9F)
			.setAttribute(EnvironmentAttributes.WATER_FOG_COLOR, -14474473)
			.modifyAttribute(EnvironmentAttributes.WATER_FOG_END_DISTANCE, FloatModifier.MULTIPLY, 0.85F)
			.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_SWAMP))
			.setAttribute(EnvironmentAttributes.INCREASED_FIRE_BURNOUT, true)
			.specialEffects(
				new BiomeSpecialEffects.Builder()
					.waterColor(6388580)
					.foliageColorOverride(6975545)
					.dryFoliageColorOverride(8082228)
					.grassColorModifier(BiomeSpecialEffects.GrassColorModifier.SWAMP)
					.build()
			)
			.mobSpawnSettings(builder.build())
			.generationSettings(builder2.build())
			.build();
	}

	public static Biome mangroveSwamp(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2) {
		MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
		BiomeDefaultFeatures.swampSpawns(builder, 70);
		builder.addSpawn(MobCategory.WATER_AMBIENT, 25, new MobSpawnSettings.SpawnerData(EntityType.TROPICAL_FISH, 8, 8));
		BiomeGenerationSettings.Builder builder2 = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		BiomeDefaultFeatures.addFossilDecoration(builder2);
		globalOverworldGeneration(builder2);
		BiomeDefaultFeatures.addDefaultOres(builder2);
		BiomeDefaultFeatures.addMangroveSwampDisks(builder2);
		BiomeDefaultFeatures.addMangroveSwampVegetation(builder2);
		BiomeDefaultFeatures.addMangroveSwampExtraVegetation(builder2);
		return baseBiome(0.8F, 0.9F)
			.setAttribute(EnvironmentAttributes.FOG_COLOR, -4138753)
			.setAttribute(EnvironmentAttributes.WATER_FOG_COLOR, -11699616)
			.modifyAttribute(EnvironmentAttributes.WATER_FOG_END_DISTANCE, FloatModifier.MULTIPLY, 0.85F)
			.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_SWAMP))
			.setAttribute(EnvironmentAttributes.INCREASED_FIRE_BURNOUT, true)
			.specialEffects(
				new BiomeSpecialEffects.Builder()
					.waterColor(3832426)
					.foliageColorOverride(9285927)
					.dryFoliageColorOverride(8082228)
					.grassColorModifier(BiomeSpecialEffects.GrassColorModifier.SWAMP)
					.build()
			)
			.mobSpawnSettings(builder.build())
			.generationSettings(builder2.build())
			.build();
	}

	public static Biome river(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2, boolean bl) {
		MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder()
			.addSpawn(MobCategory.WATER_CREATURE, 2, new MobSpawnSettings.SpawnerData(EntityType.SQUID, 1, 4))
			.addSpawn(MobCategory.WATER_AMBIENT, 5, new MobSpawnSettings.SpawnerData(EntityType.SALMON, 1, 5));
		BiomeDefaultFeatures.commonSpawns(builder);
		builder.addSpawn(MobCategory.MONSTER, bl ? 1 : 100, new MobSpawnSettings.SpawnerData(EntityType.DROWNED, 1, 1));
		BiomeGenerationSettings.Builder builder2 = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		globalOverworldGeneration(builder2);
		BiomeDefaultFeatures.addDefaultOres(builder2);
		BiomeDefaultFeatures.addDefaultSoftDisks(builder2);
		BiomeDefaultFeatures.addWaterTrees(builder2);
		BiomeDefaultFeatures.addBushes(builder2);
		BiomeDefaultFeatures.addDefaultFlowers(builder2);
		BiomeDefaultFeatures.addDefaultGrass(builder2);
		BiomeDefaultFeatures.addDefaultMushrooms(builder2);
		BiomeDefaultFeatures.addDefaultExtraVegetation(builder2, true);
		if (!bl) {
			builder2.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, AquaticPlacements.SEAGRASS_RIVER);
		}

		return baseBiome(bl ? 0.0F : 0.5F, 0.5F)
			.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, BackgroundMusic.OVERWORLD.withUnderwater(Musics.UNDER_WATER))
			.specialEffects(new BiomeSpecialEffects.Builder().waterColor(bl ? 3750089 : 4159204).build())
			.mobSpawnSettings(builder.build())
			.generationSettings(builder2.build())
			.build();
	}

	public static Biome beach(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2, boolean bl, boolean bl2) {
		MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
		boolean bl3 = !bl2 && !bl;
		if (bl3) {
			builder.addSpawn(MobCategory.CREATURE, 5, new MobSpawnSettings.SpawnerData(EntityType.TURTLE, 2, 5));
		}

		BiomeDefaultFeatures.commonSpawns(builder);
		BiomeGenerationSettings.Builder builder2 = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		globalOverworldGeneration(builder2);
		BiomeDefaultFeatures.addDefaultOres(builder2);
		BiomeDefaultFeatures.addDefaultSoftDisks(builder2);
		BiomeDefaultFeatures.addDefaultFlowers(builder2);
		BiomeDefaultFeatures.addDefaultGrass(builder2);
		BiomeDefaultFeatures.addDefaultMushrooms(builder2);
		BiomeDefaultFeatures.addDefaultExtraVegetation(builder2, true);
		float f;
		if (bl) {
			f = 0.05F;
		} else if (bl2) {
			f = 0.2F;
		} else {
			f = 0.8F;
		}

		int i = bl ? 4020182 : 4159204;
		return baseBiome(f, bl3 ? 0.4F : 0.3F)
			.specialEffects(new BiomeSpecialEffects.Builder().waterColor(i).build())
			.mobSpawnSettings(builder.build())
			.generationSettings(builder2.build())
			.build();
	}

	public static Biome theVoid(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2) {
		BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		builder.addFeature(GenerationStep.Decoration.TOP_LAYER_MODIFICATION, MiscOverworldPlacements.VOID_START_PLATFORM);
		return baseBiome(0.5F, 0.5F).hasPrecipitation(false).mobSpawnSettings(new MobSpawnSettings.Builder().build()).generationSettings(builder.build()).build();
	}

	public static Biome meadowOrCherryGrove(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2, boolean bl) {
		BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		MobSpawnSettings.Builder builder2 = new MobSpawnSettings.Builder();
		builder2.addSpawn(MobCategory.CREATURE, 1, new MobSpawnSettings.SpawnerData(bl ? EntityType.PIG : EntityType.DONKEY, 1, 2))
			.addSpawn(MobCategory.CREATURE, 2, new MobSpawnSettings.SpawnerData(EntityType.RABBIT, 2, 6))
			.addSpawn(MobCategory.CREATURE, 2, new MobSpawnSettings.SpawnerData(EntityType.SHEEP, 2, 4));
		BiomeDefaultFeatures.commonSpawns(builder2);
		globalOverworldGeneration(builder);
		BiomeDefaultFeatures.addPlainGrass(builder);
		BiomeDefaultFeatures.addDefaultOres(builder);
		BiomeDefaultFeatures.addDefaultSoftDisks(builder);
		if (bl) {
			BiomeDefaultFeatures.addCherryGroveVegetation(builder);
		} else {
			BiomeDefaultFeatures.addMeadowVegetation(builder);
		}

		BiomeDefaultFeatures.addExtraEmeralds(builder);
		BiomeDefaultFeatures.addInfestedStone(builder);
		if (bl) {
			BiomeSpecialEffects.Builder builder3 = new BiomeSpecialEffects.Builder().waterColor(6141935).grassColorOverride(11983713).foliageColorOverride(11983713);
			return baseBiome(0.5F, 0.8F)
				.setAttribute(EnvironmentAttributes.WATER_FOG_COLOR, -10635281)
				.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_CHERRY_GROVE))
				.specialEffects(builder3.build())
				.mobSpawnSettings(builder2.build())
				.generationSettings(builder.build())
				.build();
		} else {
			return baseBiome(0.5F, 0.8F)
				.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_MEADOW))
				.specialEffects(new BiomeSpecialEffects.Builder().waterColor(937679).build())
				.mobSpawnSettings(builder2.build())
				.generationSettings(builder.build())
				.build();
		}
	}

	private static Biome.BiomeBuilder basePeaks(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2) {
		BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		MobSpawnSettings.Builder builder2 = new MobSpawnSettings.Builder();
		builder2.addSpawn(MobCategory.CREATURE, 5, new MobSpawnSettings.SpawnerData(EntityType.GOAT, 1, 3));
		BiomeDefaultFeatures.commonSpawns(builder2);
		globalOverworldGeneration(builder);
		BiomeDefaultFeatures.addFrozenSprings(builder);
		BiomeDefaultFeatures.addDefaultOres(builder);
		BiomeDefaultFeatures.addDefaultSoftDisks(builder);
		BiomeDefaultFeatures.addExtraEmeralds(builder);
		BiomeDefaultFeatures.addInfestedStone(builder);
		return baseBiome(-0.7F, 0.9F)
			.setAttribute(EnvironmentAttributes.INCREASED_FIRE_BURNOUT, true)
			.mobSpawnSettings(builder2.build())
			.generationSettings(builder.build());
	}

	public static Biome frozenPeaks(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2) {
		return basePeaks(holderGetter, holderGetter2)
			.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_FROZEN_PEAKS))
			.build();
	}

	public static Biome jaggedPeaks(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2) {
		return basePeaks(holderGetter, holderGetter2)
			.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_JAGGED_PEAKS))
			.build();
	}

	public static Biome stonyPeaks(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2) {
		BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		MobSpawnSettings.Builder builder2 = new MobSpawnSettings.Builder();
		BiomeDefaultFeatures.commonSpawns(builder2);
		globalOverworldGeneration(builder);
		BiomeDefaultFeatures.addDefaultOres(builder);
		BiomeDefaultFeatures.addDefaultSoftDisks(builder);
		BiomeDefaultFeatures.addExtraEmeralds(builder);
		BiomeDefaultFeatures.addInfestedStone(builder);
		return baseBiome(1.0F, 0.3F)
			.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_STONY_PEAKS))
			.mobSpawnSettings(builder2.build())
			.generationSettings(builder.build())
			.build();
	}

	public static Biome snowySlopes(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2) {
		BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		MobSpawnSettings.Builder builder2 = new MobSpawnSettings.Builder();
		builder2.addSpawn(MobCategory.CREATURE, 4, new MobSpawnSettings.SpawnerData(EntityType.RABBIT, 2, 3))
			.addSpawn(MobCategory.CREATURE, 5, new MobSpawnSettings.SpawnerData(EntityType.GOAT, 1, 3));
		BiomeDefaultFeatures.commonSpawns(builder2);
		globalOverworldGeneration(builder);
		BiomeDefaultFeatures.addFrozenSprings(builder);
		BiomeDefaultFeatures.addDefaultOres(builder);
		BiomeDefaultFeatures.addDefaultSoftDisks(builder);
		BiomeDefaultFeatures.addDefaultExtraVegetation(builder, false);
		BiomeDefaultFeatures.addExtraEmeralds(builder);
		BiomeDefaultFeatures.addInfestedStone(builder);
		return baseBiome(-0.3F, 0.9F)
			.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_SNOWY_SLOPES))
			.setAttribute(EnvironmentAttributes.INCREASED_FIRE_BURNOUT, true)
			.mobSpawnSettings(builder2.build())
			.generationSettings(builder.build())
			.build();
	}

	public static Biome grove(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2) {
		BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		MobSpawnSettings.Builder builder2 = new MobSpawnSettings.Builder();
		builder2.addSpawn(MobCategory.CREATURE, 1, new MobSpawnSettings.SpawnerData(EntityType.WOLF, 1, 1))
			.addSpawn(MobCategory.CREATURE, 8, new MobSpawnSettings.SpawnerData(EntityType.RABBIT, 2, 3))
			.addSpawn(MobCategory.CREATURE, 4, new MobSpawnSettings.SpawnerData(EntityType.FOX, 2, 4));
		BiomeDefaultFeatures.commonSpawns(builder2);
		globalOverworldGeneration(builder);
		BiomeDefaultFeatures.addFrozenSprings(builder);
		BiomeDefaultFeatures.addDefaultOres(builder);
		BiomeDefaultFeatures.addDefaultSoftDisks(builder);
		BiomeDefaultFeatures.addGroveTrees(builder);
		BiomeDefaultFeatures.addDefaultExtraVegetation(builder, false);
		BiomeDefaultFeatures.addExtraEmeralds(builder);
		BiomeDefaultFeatures.addInfestedStone(builder);
		return baseBiome(-0.2F, 0.8F)
			.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_GROVE))
			.mobSpawnSettings(builder2.build())
			.generationSettings(builder.build())
			.build();
	}

	public static Biome lushCaves(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2) {
		MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
		builder.addSpawn(MobCategory.AXOLOTLS, 10, new MobSpawnSettings.SpawnerData(EntityType.AXOLOTL, 4, 6));
		builder.addSpawn(MobCategory.WATER_AMBIENT, 25, new MobSpawnSettings.SpawnerData(EntityType.TROPICAL_FISH, 8, 8));
		BiomeDefaultFeatures.commonSpawns(builder);
		BiomeGenerationSettings.Builder builder2 = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		globalOverworldGeneration(builder2);
		BiomeDefaultFeatures.addPlainGrass(builder2);
		BiomeDefaultFeatures.addDefaultOres(builder2);
		BiomeDefaultFeatures.addLushCavesSpecialOres(builder2);
		BiomeDefaultFeatures.addDefaultSoftDisks(builder2);
		BiomeDefaultFeatures.addLushCavesVegetationFeatures(builder2);
		return baseBiome(0.5F, 0.5F)
			.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_LUSH_CAVES))
			.mobSpawnSettings(builder.build())
			.generationSettings(builder2.build())
			.build();
	}

	public static Biome dripstoneCaves(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2) {
		MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
		BiomeDefaultFeatures.dripstoneCavesSpawns(builder);
		BiomeGenerationSettings.Builder builder2 = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		globalOverworldGeneration(builder2);
		BiomeDefaultFeatures.addPlainGrass(builder2);
		BiomeDefaultFeatures.addDefaultOres(builder2, true);
		BiomeDefaultFeatures.addDefaultSoftDisks(builder2);
		BiomeDefaultFeatures.addPlainVegetation(builder2);
		BiomeDefaultFeatures.addDefaultMushrooms(builder2);
		BiomeDefaultFeatures.addDefaultExtraVegetation(builder2, false);
		BiomeDefaultFeatures.addDripstone(builder2);
		return baseBiome(0.8F, 0.4F)
			.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_DRIPSTONE_CAVES))
			.mobSpawnSettings(builder.build())
			.generationSettings(builder2.build())
			.build();
	}

	public static Biome deepDark(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2) {
		MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
		BiomeGenerationSettings.Builder builder2 = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
		builder2.addCarver(Carvers.CAVE);
		builder2.addCarver(Carvers.CAVE_EXTRA_UNDERGROUND);
		builder2.addCarver(Carvers.CANYON);
		BiomeDefaultFeatures.addDefaultCrystalFormations(builder2);
		BiomeDefaultFeatures.addDefaultMonsterRoom(builder2);
		BiomeDefaultFeatures.addDefaultUndergroundVariety(builder2);
		BiomeDefaultFeatures.addSurfaceFreezing(builder2);
		BiomeDefaultFeatures.addPlainGrass(builder2);
		BiomeDefaultFeatures.addDefaultOres(builder2);
		BiomeDefaultFeatures.addDefaultSoftDisks(builder2);
		BiomeDefaultFeatures.addPlainVegetation(builder2);
		BiomeDefaultFeatures.addDefaultMushrooms(builder2);
		BiomeDefaultFeatures.addDefaultExtraVegetation(builder2, false);
		BiomeDefaultFeatures.addSculk(builder2);
		return baseBiome(0.8F, 0.4F)
			.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_DEEP_DARK))
			.mobSpawnSettings(builder.build())
			.generationSettings(builder2.build())
			.build();
	}
}
