package net.minecraft.client.renderer.block;

import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockModels;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.world.level.block.state.BlockState;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.renderer.v1.model.FabricBlockModels} injected by mod fabric-renderer-api-v1</p>
 */
@Environment(EnvType.CLIENT)
public class BlockModelShaper implements FabricBlockModels {
	private Map<BlockState, BlockStateModel> modelByStateCache = Map.of();
	private final ModelManager modelManager;

	public BlockModelShaper(ModelManager modelManager) {
		this.modelManager = modelManager;
	}

	public TextureAtlasSprite getParticleIcon(BlockState blockState) {
		return this.getBlockModel(blockState).particleIcon();
	}

	public BlockStateModel getBlockModel(BlockState blockState) {
		BlockStateModel blockStateModel = this.modelByStateCache.get(blockState);
		if (blockStateModel == null) {
			blockStateModel = this.modelManager.getMissingBlockStateModel();
		}

		return blockStateModel;
	}

	public ModelManager getModelManager() {
		return this.modelManager;
	}

	public void replaceCache(Map<BlockState, BlockStateModel> map) {
		this.modelByStateCache = map;
	}
}
