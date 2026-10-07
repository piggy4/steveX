package net.minecraft.world.entity.variant;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.attribute.EnvironmentAttributeReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;

public record SpawnContext(BlockPos pos, ServerLevelAccessor level, EnvironmentAttributeReader environmentAttributes, Holder<Biome> biome) {
	public static SpawnContext create(ServerLevelAccessor serverLevelAccessor, BlockPos blockPos) {
		Holder<Biome> holder = serverLevelAccessor.getBiome(blockPos);
		return new SpawnContext(blockPos, serverLevelAccessor, serverLevelAccessor.environmentAttributes(), holder);
	}
}
