package net.minecraft.world.level.biome;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Util;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class MobSpawnSettings {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final float DEFAULT_CREATURE_SPAWN_PROBABILITY = 0.1F;
	public static final WeightedList<MobSpawnSettings.SpawnerData> EMPTY_MOB_LIST = WeightedList.of();
	public static final MobSpawnSettings EMPTY = new MobSpawnSettings.Builder().build();
	public static final MapCodec<MobSpawnSettings> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				Codec.floatRange(0.0F, 0.9999999F)
					.optionalFieldOf("creature_spawn_probability", 0.1F)
					.forGetter(mobSpawnSettings -> mobSpawnSettings.creatureGenerationProbability),
				Codec.simpleMap(
						MobCategory.CODEC,
						WeightedList.codec(MobSpawnSettings.SpawnerData.CODEC).promotePartial(Util.prefix("Spawn data: ", LOGGER::error)),
						StringRepresentable.keys(MobCategory.values())
					)
					.fieldOf("spawners")
					.forGetter(mobSpawnSettings -> mobSpawnSettings.spawners),
				Codec.simpleMap(BuiltInRegistries.ENTITY_TYPE.byNameCodec(), MobSpawnSettings.MobSpawnCost.CODEC, BuiltInRegistries.ENTITY_TYPE)
					.fieldOf("spawn_costs")
					.forGetter(mobSpawnSettings -> mobSpawnSettings.mobSpawnCosts)
			)
			.apply(instance, MobSpawnSettings::new)
	);
	private final float creatureGenerationProbability;
	private final Map<MobCategory, WeightedList<MobSpawnSettings.SpawnerData>> spawners;
	private final Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> mobSpawnCosts;

	MobSpawnSettings(float f, Map<MobCategory, WeightedList<MobSpawnSettings.SpawnerData>> map, Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> map2) {
		this.creatureGenerationProbability = f;
		this.spawners = ImmutableMap.copyOf(map);
		this.mobSpawnCosts = ImmutableMap.copyOf(map2);
	}

	public WeightedList<MobSpawnSettings.SpawnerData> getMobs(MobCategory mobCategory) {
		return this.spawners.getOrDefault(mobCategory, EMPTY_MOB_LIST);
	}

	public MobSpawnSettings.@Nullable MobSpawnCost getMobSpawnCost(EntityType<?> entityType) {
		return this.mobSpawnCosts.get(entityType);
	}

	public float getCreatureProbability() {
		return this.creatureGenerationProbability;
	}

	public static class Builder {
		private final Map<MobCategory, WeightedList.Builder<MobSpawnSettings.SpawnerData>> spawners = Util.makeEnumMap(
			MobCategory.class, mobCategory -> WeightedList.builder()
		);
		private final Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> mobSpawnCosts = Maps.newLinkedHashMap();
		private float creatureGenerationProbability = 0.1F;

		public MobSpawnSettings.Builder addSpawn(MobCategory mobCategory, int i, MobSpawnSettings.SpawnerData spawnerData) {
			this.spawners.get(mobCategory).add(spawnerData, i);
			return this;
		}

		public MobSpawnSettings.Builder addMobCharge(EntityType<?> entityType, double d, double e) {
			this.mobSpawnCosts.put(entityType, new MobSpawnSettings.MobSpawnCost(e, d));
			return this;
		}

		public MobSpawnSettings.Builder creatureGenerationProbability(float f) {
			this.creatureGenerationProbability = f;
			return this;
		}

		public MobSpawnSettings build() {
			return new MobSpawnSettings(
				this.creatureGenerationProbability,
				this.spawners.entrySet().stream().collect(ImmutableMap.toImmutableMap(Entry::getKey, entry -> entry.getValue().build())),
				ImmutableMap.copyOf(this.mobSpawnCosts)
			);
		}
	}

	public record MobSpawnCost(double energyBudget, double charge) {
		public static final Codec<MobSpawnSettings.MobSpawnCost> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Codec.DOUBLE.fieldOf("energy_budget").forGetter(mobSpawnCost -> mobSpawnCost.energyBudget),
					Codec.DOUBLE.fieldOf("charge").forGetter(mobSpawnCost -> mobSpawnCost.charge)
				)
				.apply(instance, MobSpawnSettings.MobSpawnCost::new)
		);
	}

	public record SpawnerData(EntityType<?> type, int minCount, int maxCount) {
		public static final MapCodec<MobSpawnSettings.SpawnerData> CODEC = RecordCodecBuilder.<MobSpawnSettings.SpawnerData>mapCodec(
				instance -> instance.group(
						BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("type").forGetter(spawnerData -> spawnerData.type),
						ExtraCodecs.POSITIVE_INT.fieldOf("minCount").forGetter(spawnerData -> spawnerData.minCount),
						ExtraCodecs.POSITIVE_INT.fieldOf("maxCount").forGetter(spawnerData -> spawnerData.maxCount)
					)
					.apply(instance, MobSpawnSettings.SpawnerData::new)
			)
			.validate(
				spawnerData -> spawnerData.minCount > spawnerData.maxCount
					? DataResult.error(() -> "minCount needs to be smaller or equal to maxCount")
					: DataResult.success(spawnerData)
			);

		public SpawnerData {
			type = type.getCategory() == MobCategory.MISC ? EntityType.PIG : type;
		}

		@Override
		public String toString() {
			return EntityType.getKey(this.type) + "*(" + this.minCount + "-" + this.maxCount + ")";
		}
	}
}
