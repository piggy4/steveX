package net.minecraft.world.level.block.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.fabricmc.fabric.api.block.v1.FabricBlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.block.v1.FabricBlockState} injected by mod fabric-block-api-v1</p>
 */
public class BlockState extends BlockBehaviour.BlockStateBase implements FabricBlockState {
	public static final Codec<BlockState> CODEC = codec(BuiltInRegistries.BLOCK.byNameCodec(), Block::defaultBlockState).stable();

	public BlockState(Block block, Reference2ObjectArrayMap<Property<?>, Comparable<?>> reference2ObjectArrayMap, MapCodec<BlockState> mapCodec) {
		super(block, reference2ObjectArrayMap, mapCodec);
	}

	@Override
	protected BlockState asState() {
		return this;
	}
}
