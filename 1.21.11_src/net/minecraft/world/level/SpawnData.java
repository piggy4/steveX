package net.minecraft.world.level;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.InclusiveRange;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EquipmentTable;

public record SpawnData(CompoundTag entityToSpawn, Optional<SpawnData.CustomSpawnRules> customSpawnRules, Optional<EquipmentTable> equipment) {
	public static final String ENTITY_TAG = "entity";
	public static final Codec<SpawnData> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				CompoundTag.CODEC.fieldOf("entity").forGetter(spawnData -> spawnData.entityToSpawn),
				SpawnData.CustomSpawnRules.CODEC.optionalFieldOf("custom_spawn_rules").forGetter(spawnData -> spawnData.customSpawnRules),
				EquipmentTable.CODEC.optionalFieldOf("equipment").forGetter(spawnData -> spawnData.equipment)
			)
			.apply(instance, SpawnData::new)
	);
	public static final Codec<WeightedList<SpawnData>> LIST_CODEC = WeightedList.codec(CODEC);

	public SpawnData() {
		this(new CompoundTag(), Optional.empty(), Optional.empty());
	}

	public SpawnData {
		Optional<Identifier> optional3 = entityToSpawn.read("id", Identifier.CODEC);
		if (optional3.isPresent()) {
			entityToSpawn.store("id", Identifier.CODEC, optional3.get());
		} else {
			entityToSpawn.remove("id");
		}
	}

	public CompoundTag getEntityToSpawn() {
		return this.entityToSpawn;
	}

	public Optional<SpawnData.CustomSpawnRules> getCustomSpawnRules() {
		return this.customSpawnRules;
	}

	public Optional<EquipmentTable> getEquipment() {
		return this.equipment;
	}

	public record CustomSpawnRules(InclusiveRange<Integer> blockLightLimit, InclusiveRange<Integer> skyLightLimit) {
		private static final InclusiveRange<Integer> LIGHT_RANGE = new InclusiveRange<>(0, 15);
		public static final Codec<SpawnData.CustomSpawnRules> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					lightLimit("block_light_limit").forGetter(customSpawnRules -> customSpawnRules.blockLightLimit),
					lightLimit("sky_light_limit").forGetter(customSpawnRules -> customSpawnRules.skyLightLimit)
				)
				.apply(instance, SpawnData.CustomSpawnRules::new)
		);

		private static DataResult<InclusiveRange<Integer>> checkLightBoundaries(InclusiveRange<Integer> inclusiveRange) {
			return !LIGHT_RANGE.contains(inclusiveRange)
				? DataResult.error(() -> "Light values must be withing range " + LIGHT_RANGE)
				: DataResult.success(inclusiveRange);
		}

		private static MapCodec<InclusiveRange<Integer>> lightLimit(String string) {
			return InclusiveRange.INT.lenientOptionalFieldOf(string, LIGHT_RANGE).validate(SpawnData.CustomSpawnRules::checkLightBoundaries);
		}

		public boolean isValidPosition(BlockPos blockPos, ServerLevel serverLevel) {
			return this.blockLightLimit.isValueInRange(serverLevel.getBrightness(LightLayer.BLOCK, blockPos))
				&& this.skyLightLimit.isValueInRange(serverLevel.getBrightness(LightLayer.SKY, blockPos));
		}
	}
}
