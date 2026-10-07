package net.minecraft.world.level.block.entity.trialspawner;

import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentTable;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

public class TrialSpawnerConfigs {
	private static final TrialSpawnerConfigs.Keys TRIAL_CHAMBER_BREEZE = TrialSpawnerConfigs.Keys.of("trial_chamber/breeze");
	private static final TrialSpawnerConfigs.Keys TRIAL_CHAMBER_MELEE_HUSK = TrialSpawnerConfigs.Keys.of("trial_chamber/melee/husk");
	private static final TrialSpawnerConfigs.Keys TRIAL_CHAMBER_MELEE_SPIDER = TrialSpawnerConfigs.Keys.of("trial_chamber/melee/spider");
	private static final TrialSpawnerConfigs.Keys TRIAL_CHAMBER_MELEE_ZOMBIE = TrialSpawnerConfigs.Keys.of("trial_chamber/melee/zombie");
	private static final TrialSpawnerConfigs.Keys TRIAL_CHAMBER_RANGED_POISON_SKELETON = TrialSpawnerConfigs.Keys.of("trial_chamber/ranged/poison_skeleton");
	private static final TrialSpawnerConfigs.Keys TRIAL_CHAMBER_RANGED_SKELETON = TrialSpawnerConfigs.Keys.of("trial_chamber/ranged/skeleton");
	private static final TrialSpawnerConfigs.Keys TRIAL_CHAMBER_RANGED_STRAY = TrialSpawnerConfigs.Keys.of("trial_chamber/ranged/stray");
	private static final TrialSpawnerConfigs.Keys TRIAL_CHAMBER_SLOW_RANGED_POISON_SKELETON = TrialSpawnerConfigs.Keys.of(
		"trial_chamber/slow_ranged/poison_skeleton"
	);
	private static final TrialSpawnerConfigs.Keys TRIAL_CHAMBER_SLOW_RANGED_SKELETON = TrialSpawnerConfigs.Keys.of("trial_chamber/slow_ranged/skeleton");
	private static final TrialSpawnerConfigs.Keys TRIAL_CHAMBER_SLOW_RANGED_STRAY = TrialSpawnerConfigs.Keys.of("trial_chamber/slow_ranged/stray");
	private static final TrialSpawnerConfigs.Keys TRIAL_CHAMBER_SMALL_MELEE_BABY_ZOMBIE = TrialSpawnerConfigs.Keys.of("trial_chamber/small_melee/baby_zombie");
	private static final TrialSpawnerConfigs.Keys TRIAL_CHAMBER_SMALL_MELEE_CAVE_SPIDER = TrialSpawnerConfigs.Keys.of("trial_chamber/small_melee/cave_spider");
	private static final TrialSpawnerConfigs.Keys TRIAL_CHAMBER_SMALL_MELEE_SILVERFISH = TrialSpawnerConfigs.Keys.of("trial_chamber/small_melee/silverfish");
	private static final TrialSpawnerConfigs.Keys TRIAL_CHAMBER_SMALL_MELEE_SLIME = TrialSpawnerConfigs.Keys.of("trial_chamber/small_melee/slime");

	public static void bootstrap(BootstrapContext<TrialSpawnerConfig> bootstrapContext) {
		register(
			bootstrapContext,
			TRIAL_CHAMBER_BREEZE,
			TrialSpawnerConfig.builder()
				.simultaneousMobs(1.0F)
				.simultaneousMobsAddedPerPlayer(0.5F)
				.ticksBetweenSpawn(20)
				.totalMobs(2.0F)
				.totalMobsAddedPerPlayer(1.0F)
				.spawnPotentialsDefinition(WeightedList.of(spawnData(EntityType.BREEZE)))
				.build(),
			TrialSpawnerConfig.builder()
				.simultaneousMobsAddedPerPlayer(0.5F)
				.ticksBetweenSpawn(20)
				.totalMobs(4.0F)
				.totalMobsAddedPerPlayer(1.0F)
				.spawnPotentialsDefinition(WeightedList.of(spawnData(EntityType.BREEZE)))
				.lootTablesToEject(
					WeightedList.<ResourceKey<LootTable>>builder()
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_KEY, 3)
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_CONSUMABLES, 7)
						.build()
				)
				.build()
		);
		register(
			bootstrapContext,
			TRIAL_CHAMBER_MELEE_HUSK,
			trialChamberBase().spawnPotentialsDefinition(WeightedList.of(spawnData(EntityType.HUSK))).build(),
			trialChamberBase()
				.spawnPotentialsDefinition(WeightedList.of(spawnDataWithEquipment(EntityType.HUSK, BuiltInLootTables.EQUIPMENT_TRIAL_CHAMBER_MELEE)))
				.lootTablesToEject(
					WeightedList.<ResourceKey<LootTable>>builder()
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_KEY, 3)
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_CONSUMABLES, 7)
						.build()
				)
				.build()
		);
		register(
			bootstrapContext,
			TRIAL_CHAMBER_MELEE_SPIDER,
			trialChamberBase().spawnPotentialsDefinition(WeightedList.of(spawnData(EntityType.SPIDER))).build(),
			trialChamberMeleeOminous()
				.spawnPotentialsDefinition(WeightedList.of(spawnData(EntityType.SPIDER)))
				.lootTablesToEject(
					WeightedList.<ResourceKey<LootTable>>builder()
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_KEY, 3)
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_CONSUMABLES, 7)
						.build()
				)
				.build()
		);
		register(
			bootstrapContext,
			TRIAL_CHAMBER_MELEE_ZOMBIE,
			trialChamberBase().spawnPotentialsDefinition(WeightedList.of(spawnData(EntityType.ZOMBIE))).build(),
			trialChamberBase()
				.lootTablesToEject(
					WeightedList.<ResourceKey<LootTable>>builder()
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_KEY, 3)
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_CONSUMABLES, 7)
						.build()
				)
				.spawnPotentialsDefinition(WeightedList.of(spawnDataWithEquipment(EntityType.ZOMBIE, BuiltInLootTables.EQUIPMENT_TRIAL_CHAMBER_MELEE)))
				.build()
		);
		register(
			bootstrapContext,
			TRIAL_CHAMBER_RANGED_POISON_SKELETON,
			trialChamberBase().spawnPotentialsDefinition(WeightedList.of(spawnData(EntityType.BOGGED))).build(),
			trialChamberBase()
				.lootTablesToEject(
					WeightedList.<ResourceKey<LootTable>>builder()
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_KEY, 3)
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_CONSUMABLES, 7)
						.build()
				)
				.spawnPotentialsDefinition(WeightedList.of(spawnDataWithEquipment(EntityType.BOGGED, BuiltInLootTables.EQUIPMENT_TRIAL_CHAMBER_RANGED)))
				.build()
		);
		register(
			bootstrapContext,
			TRIAL_CHAMBER_RANGED_SKELETON,
			trialChamberBase().spawnPotentialsDefinition(WeightedList.of(spawnData(EntityType.SKELETON))).build(),
			trialChamberBase()
				.lootTablesToEject(
					WeightedList.<ResourceKey<LootTable>>builder()
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_KEY, 3)
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_CONSUMABLES, 7)
						.build()
				)
				.spawnPotentialsDefinition(WeightedList.of(spawnDataWithEquipment(EntityType.SKELETON, BuiltInLootTables.EQUIPMENT_TRIAL_CHAMBER_RANGED)))
				.build()
		);
		register(
			bootstrapContext,
			TRIAL_CHAMBER_RANGED_STRAY,
			trialChamberBase().spawnPotentialsDefinition(WeightedList.of(spawnData(EntityType.STRAY))).build(),
			trialChamberBase()
				.lootTablesToEject(
					WeightedList.<ResourceKey<LootTable>>builder()
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_KEY, 3)
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_CONSUMABLES, 7)
						.build()
				)
				.spawnPotentialsDefinition(WeightedList.of(spawnDataWithEquipment(EntityType.STRAY, BuiltInLootTables.EQUIPMENT_TRIAL_CHAMBER_RANGED)))
				.build()
		);
		register(
			bootstrapContext,
			TRIAL_CHAMBER_SLOW_RANGED_POISON_SKELETON,
			trialChamberSlowRanged().spawnPotentialsDefinition(WeightedList.of(spawnData(EntityType.BOGGED))).build(),
			trialChamberSlowRanged()
				.lootTablesToEject(
					WeightedList.<ResourceKey<LootTable>>builder()
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_KEY, 3)
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_CONSUMABLES, 7)
						.build()
				)
				.spawnPotentialsDefinition(WeightedList.of(spawnDataWithEquipment(EntityType.BOGGED, BuiltInLootTables.EQUIPMENT_TRIAL_CHAMBER_RANGED)))
				.build()
		);
		register(
			bootstrapContext,
			TRIAL_CHAMBER_SLOW_RANGED_SKELETON,
			trialChamberSlowRanged().spawnPotentialsDefinition(WeightedList.of(spawnData(EntityType.SKELETON))).build(),
			trialChamberSlowRanged()
				.lootTablesToEject(
					WeightedList.<ResourceKey<LootTable>>builder()
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_KEY, 3)
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_CONSUMABLES, 7)
						.build()
				)
				.spawnPotentialsDefinition(WeightedList.of(spawnDataWithEquipment(EntityType.SKELETON, BuiltInLootTables.EQUIPMENT_TRIAL_CHAMBER_RANGED)))
				.build()
		);
		register(
			bootstrapContext,
			TRIAL_CHAMBER_SLOW_RANGED_STRAY,
			trialChamberSlowRanged().spawnPotentialsDefinition(WeightedList.of(spawnData(EntityType.STRAY))).build(),
			trialChamberSlowRanged()
				.lootTablesToEject(
					WeightedList.<ResourceKey<LootTable>>builder()
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_KEY, 3)
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_CONSUMABLES, 7)
						.build()
				)
				.spawnPotentialsDefinition(WeightedList.of(spawnDataWithEquipment(EntityType.STRAY, BuiltInLootTables.EQUIPMENT_TRIAL_CHAMBER_RANGED)))
				.build()
		);
		register(
			bootstrapContext,
			TRIAL_CHAMBER_SMALL_MELEE_BABY_ZOMBIE,
			TrialSpawnerConfig.builder()
				.simultaneousMobsAddedPerPlayer(0.5F)
				.ticksBetweenSpawn(20)
				.spawnPotentialsDefinition(WeightedList.of(customSpawnDataWithEquipment(EntityType.ZOMBIE, compoundTag -> compoundTag.putBoolean("IsBaby", true), null)))
				.build(),
			TrialSpawnerConfig.builder()
				.simultaneousMobsAddedPerPlayer(0.5F)
				.ticksBetweenSpawn(20)
				.lootTablesToEject(
					WeightedList.<ResourceKey<LootTable>>builder()
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_KEY, 3)
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_CONSUMABLES, 7)
						.build()
				)
				.spawnPotentialsDefinition(
					WeightedList.of(
						customSpawnDataWithEquipment(EntityType.ZOMBIE, compoundTag -> compoundTag.putBoolean("IsBaby", true), BuiltInLootTables.EQUIPMENT_TRIAL_CHAMBER_MELEE)
					)
				)
				.build()
		);
		register(
			bootstrapContext,
			TRIAL_CHAMBER_SMALL_MELEE_CAVE_SPIDER,
			trialChamberBase().spawnPotentialsDefinition(WeightedList.of(spawnData(EntityType.CAVE_SPIDER))).build(),
			trialChamberMeleeOminous()
				.lootTablesToEject(
					WeightedList.<ResourceKey<LootTable>>builder()
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_KEY, 3)
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_CONSUMABLES, 7)
						.build()
				)
				.spawnPotentialsDefinition(WeightedList.of(spawnData(EntityType.CAVE_SPIDER)))
				.build()
		);
		register(
			bootstrapContext,
			TRIAL_CHAMBER_SMALL_MELEE_SILVERFISH,
			trialChamberBase().spawnPotentialsDefinition(WeightedList.of(spawnData(EntityType.SILVERFISH))).build(),
			trialChamberMeleeOminous()
				.lootTablesToEject(
					WeightedList.<ResourceKey<LootTable>>builder()
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_KEY, 3)
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_CONSUMABLES, 7)
						.build()
				)
				.spawnPotentialsDefinition(WeightedList.of(spawnData(EntityType.SILVERFISH)))
				.build()
		);
		register(
			bootstrapContext,
			TRIAL_CHAMBER_SMALL_MELEE_SLIME,
			trialChamberBase()
				.spawnPotentialsDefinition(
					WeightedList.<SpawnData>builder()
						.add(customSpawnData(EntityType.SLIME, compoundTag -> compoundTag.putByte("Size", (byte)1)), 3)
						.add(customSpawnData(EntityType.SLIME, compoundTag -> compoundTag.putByte("Size", (byte)2)), 1)
						.build()
				)
				.build(),
			trialChamberMeleeOminous()
				.lootTablesToEject(
					WeightedList.<ResourceKey<LootTable>>builder()
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_KEY, 3)
						.add(BuiltInLootTables.SPAWNER_OMINOUS_TRIAL_CHAMBER_CONSUMABLES, 7)
						.build()
				)
				.spawnPotentialsDefinition(
					WeightedList.<SpawnData>builder()
						.add(customSpawnData(EntityType.SLIME, compoundTag -> compoundTag.putByte("Size", (byte)1)), 3)
						.add(customSpawnData(EntityType.SLIME, compoundTag -> compoundTag.putByte("Size", (byte)2)), 1)
						.build()
				)
				.build()
		);
	}

	private static <T extends Entity> SpawnData spawnData(EntityType<T> entityType) {
		return customSpawnDataWithEquipment(entityType, compoundTag -> {}, null);
	}

	private static <T extends Entity> SpawnData customSpawnData(EntityType<T> entityType, Consumer<CompoundTag> consumer) {
		return customSpawnDataWithEquipment(entityType, consumer, null);
	}

	private static <T extends Entity> SpawnData spawnDataWithEquipment(EntityType<T> entityType, ResourceKey<LootTable> resourceKey) {
		return customSpawnDataWithEquipment(entityType, compoundTag -> {}, resourceKey);
	}

	private static <T extends Entity> SpawnData customSpawnDataWithEquipment(
		EntityType<T> entityType, Consumer<CompoundTag> consumer, @Nullable ResourceKey<LootTable> resourceKey
	) {
		CompoundTag compoundTag = new CompoundTag();
		compoundTag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString());
		consumer.accept(compoundTag);
		Optional<EquipmentTable> optional = Optional.ofNullable(resourceKey).map(resourceKeyx -> new EquipmentTable(resourceKeyx, 0.0F));
		return new SpawnData(compoundTag, Optional.empty(), optional);
	}

	private static void register(
		BootstrapContext<TrialSpawnerConfig> bootstrapContext,
		TrialSpawnerConfigs.Keys keys,
		TrialSpawnerConfig trialSpawnerConfig,
		TrialSpawnerConfig trialSpawnerConfig2
	) {
		bootstrapContext.register(keys.normal, trialSpawnerConfig);
		bootstrapContext.register(keys.ominous, trialSpawnerConfig2);
	}

	static ResourceKey<TrialSpawnerConfig> registryKey(String string) {
		return ResourceKey.create(Registries.TRIAL_SPAWNER_CONFIG, Identifier.withDefaultNamespace(string));
	}

	private static TrialSpawnerConfig.Builder trialChamberMeleeOminous() {
		return TrialSpawnerConfig.builder().simultaneousMobs(4.0F).simultaneousMobsAddedPerPlayer(0.5F).ticksBetweenSpawn(20).totalMobs(12.0F);
	}

	private static TrialSpawnerConfig.Builder trialChamberSlowRanged() {
		return TrialSpawnerConfig.builder().simultaneousMobs(4.0F).simultaneousMobsAddedPerPlayer(2.0F).ticksBetweenSpawn(160);
	}

	private static TrialSpawnerConfig.Builder trialChamberBase() {
		return TrialSpawnerConfig.builder().simultaneousMobs(3.0F).simultaneousMobsAddedPerPlayer(0.5F).ticksBetweenSpawn(20);
	}

	record Keys(ResourceKey<TrialSpawnerConfig> normal, ResourceKey<TrialSpawnerConfig> ominous) {

		public static TrialSpawnerConfigs.Keys of(String string) {
			return new TrialSpawnerConfigs.Keys(TrialSpawnerConfigs.registryKey(string + "/normal"), TrialSpawnerConfigs.registryKey(string + "/ominous"));
		}
	}
}
