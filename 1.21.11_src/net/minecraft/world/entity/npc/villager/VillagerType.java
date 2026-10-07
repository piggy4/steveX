package net.minecraft.world.entity.npc.villager;

import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

public final class VillagerType {
	public static final ResourceKey<VillagerType> DESERT = createKey("desert");
	public static final ResourceKey<VillagerType> JUNGLE = createKey("jungle");
	public static final ResourceKey<VillagerType> PLAINS = createKey("plains");
	public static final ResourceKey<VillagerType> SAVANNA = createKey("savanna");
	public static final ResourceKey<VillagerType> SNOW = createKey("snow");
	public static final ResourceKey<VillagerType> SWAMP = createKey("swamp");
	public static final ResourceKey<VillagerType> TAIGA = createKey("taiga");
	public static final Codec<Holder<VillagerType>> CODEC = RegistryFixedCodec.create(Registries.VILLAGER_TYPE);
	public static final StreamCodec<RegistryFriendlyByteBuf, Holder<VillagerType>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.VILLAGER_TYPE);
	public static final Map<ResourceKey<Biome>, ResourceKey<VillagerType>> BY_BIOME = Util.make(Maps.newHashMap(), hashMap -> {
		hashMap.put(Biomes.BADLANDS, DESERT);
		hashMap.put(Biomes.DESERT, DESERT);
		hashMap.put(Biomes.ERODED_BADLANDS, DESERT);
		hashMap.put(Biomes.WOODED_BADLANDS, DESERT);
		hashMap.put(Biomes.BAMBOO_JUNGLE, JUNGLE);
		hashMap.put(Biomes.JUNGLE, JUNGLE);
		hashMap.put(Biomes.SPARSE_JUNGLE, JUNGLE);
		hashMap.put(Biomes.SAVANNA_PLATEAU, SAVANNA);
		hashMap.put(Biomes.SAVANNA, SAVANNA);
		hashMap.put(Biomes.WINDSWEPT_SAVANNA, SAVANNA);
		hashMap.put(Biomes.DEEP_FROZEN_OCEAN, SNOW);
		hashMap.put(Biomes.FROZEN_OCEAN, SNOW);
		hashMap.put(Biomes.FROZEN_RIVER, SNOW);
		hashMap.put(Biomes.ICE_SPIKES, SNOW);
		hashMap.put(Biomes.SNOWY_BEACH, SNOW);
		hashMap.put(Biomes.SNOWY_TAIGA, SNOW);
		hashMap.put(Biomes.SNOWY_PLAINS, SNOW);
		hashMap.put(Biomes.GROVE, SNOW);
		hashMap.put(Biomes.SNOWY_SLOPES, SNOW);
		hashMap.put(Biomes.FROZEN_PEAKS, SNOW);
		hashMap.put(Biomes.JAGGED_PEAKS, SNOW);
		hashMap.put(Biomes.SWAMP, SWAMP);
		hashMap.put(Biomes.MANGROVE_SWAMP, SWAMP);
		hashMap.put(Biomes.OLD_GROWTH_SPRUCE_TAIGA, TAIGA);
		hashMap.put(Biomes.OLD_GROWTH_PINE_TAIGA, TAIGA);
		hashMap.put(Biomes.WINDSWEPT_GRAVELLY_HILLS, TAIGA);
		hashMap.put(Biomes.WINDSWEPT_HILLS, TAIGA);
		hashMap.put(Biomes.TAIGA, TAIGA);
		hashMap.put(Biomes.WINDSWEPT_FOREST, TAIGA);
	});

	private static ResourceKey<VillagerType> createKey(String string) {
		return ResourceKey.create(Registries.VILLAGER_TYPE, Identifier.withDefaultNamespace(string));
	}

	private static VillagerType register(Registry<VillagerType> registry, ResourceKey<VillagerType> resourceKey) {
		return Registry.register(registry, resourceKey, new VillagerType());
	}

	public static VillagerType bootstrap(Registry<VillagerType> registry) {
		register(registry, DESERT);
		register(registry, JUNGLE);
		register(registry, PLAINS);
		register(registry, SAVANNA);
		register(registry, SNOW);
		register(registry, SWAMP);
		return register(registry, TAIGA);
	}

	public static ResourceKey<VillagerType> byBiome(Holder<Biome> holder) {
		return holder.unwrapKey().map(BY_BIOME::get).orElse(PLAINS);
	}
}
