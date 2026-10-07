package net.minecraft.world.level.chunk;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public record PalettedContainerFactory(
	Strategy<BlockState> blockStatesStrategy,
	BlockState defaultBlockState,
	Codec<PalettedContainer<BlockState>> blockStatesContainerCodec,
	Strategy<Holder<Biome>> biomeStrategy,
	Holder<Biome> defaultBiome,
	Codec<PalettedContainerRO<Holder<Biome>>> biomeContainerCodec
) {
	public static PalettedContainerFactory create(RegistryAccess registryAccess) {
		Strategy<BlockState> strategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
		BlockState blockState = Blocks.AIR.defaultBlockState();
		Registry<Biome> registry = registryAccess.lookupOrThrow(Registries.BIOME);
		Strategy<Holder<Biome>> strategy2 = Strategy.createForBiomes(registry.asHolderIdMap());
		Holder.Reference<Biome> reference = registry.getOrThrow(Biomes.PLAINS);
		return new PalettedContainerFactory(
			strategy,
			blockState,
			PalettedContainer.codecRW(BlockState.CODEC, strategy, blockState),
			strategy2,
			reference,
			PalettedContainer.codecRO(registry.holderByNameCodec(), strategy2, reference)
		);
	}

	public PalettedContainer<BlockState> createForBlockStates() {
		return new PalettedContainer<>(this.defaultBlockState, this.blockStatesStrategy);
	}

	public PalettedContainer<Holder<Biome>> createForBiomes() {
		return new PalettedContainer<>(this.defaultBiome, this.biomeStrategy);
	}
}
