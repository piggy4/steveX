package net.minecraft.world.level.levelgen.structure.pools.alias;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public interface PoolAliasBinding {
	Codec<PoolAliasBinding> CODEC = BuiltInRegistries.POOL_ALIAS_BINDING_TYPE.byNameCodec().dispatch(PoolAliasBinding::codec, Function.identity());

	void forEachResolved(RandomSource randomSource, BiConsumer<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> biConsumer);

	Stream<ResourceKey<StructureTemplatePool>> allTargets();

	static DirectPoolAlias direct(String string, String string2) {
		return direct(Pools.createKey(string), Pools.createKey(string2));
	}

	static DirectPoolAlias direct(ResourceKey<StructureTemplatePool> resourceKey, ResourceKey<StructureTemplatePool> resourceKey2) {
		return new DirectPoolAlias(resourceKey, resourceKey2);
	}

	static RandomPoolAlias random(String string, WeightedList<String> weightedList) {
		WeightedList.Builder<ResourceKey<StructureTemplatePool>> builder = WeightedList.builder();
		weightedList.unwrap().forEach(weighted -> builder.add(Pools.createKey(weighted.value()), weighted.weight()));
		return random(Pools.createKey(string), builder.build());
	}

	static RandomPoolAlias random(ResourceKey<StructureTemplatePool> resourceKey, WeightedList<ResourceKey<StructureTemplatePool>> weightedList) {
		return new RandomPoolAlias(resourceKey, weightedList);
	}

	static RandomGroupPoolAlias randomGroup(WeightedList<List<PoolAliasBinding>> weightedList) {
		return new RandomGroupPoolAlias(weightedList);
	}

	MapCodec<? extends PoolAliasBinding> codec();
}
