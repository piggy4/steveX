package net.minecraft.client.renderer.chunk;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class SectionCompiler {
	private final BlockRenderDispatcher blockRenderer;
	private final BlockEntityRenderDispatcher blockEntityRenderer;

	public SectionCompiler(BlockRenderDispatcher blockRenderDispatcher, BlockEntityRenderDispatcher blockEntityRenderDispatcher) {
		this.blockRenderer = blockRenderDispatcher;
		this.blockEntityRenderer = blockEntityRenderDispatcher;
	}

	public SectionCompiler.Results compile(
		SectionPos sectionPos, RenderSectionRegion renderSectionRegion, VertexSorting vertexSorting, SectionBufferBuilderPack sectionBufferBuilderPack
	) {
		SectionCompiler.Results results = new SectionCompiler.Results();
		BlockPos blockPos = sectionPos.origin();
		BlockPos blockPos2 = blockPos.offset(15, 15, 15);
		VisGraph visGraph = new VisGraph();
		PoseStack poseStack = new PoseStack();
		ModelBlockRenderer.enableCaching();
		Map<ChunkSectionLayer, BufferBuilder> map = new EnumMap<>(ChunkSectionLayer.class);
		RandomSource randomSource = RandomSource.create();
		List<BlockModelPart> list = new ObjectArrayList<>();

		for (BlockPos blockPos3 : BlockPos.betweenClosed(blockPos, blockPos2)) {
			BlockState blockState = renderSectionRegion.getBlockState(blockPos3);
			if (blockState.isSolidRender()) {
				visGraph.setOpaque(blockPos3);
			}

			if (blockState.hasBlockEntity()) {
				BlockEntity blockEntity = renderSectionRegion.getBlockEntity(blockPos3);
				if (blockEntity != null) {
					this.handleBlockEntity(results, blockEntity);
				}
			}

			FluidState fluidState = blockState.getFluidState();
			if (!fluidState.isEmpty()) {
				ChunkSectionLayer chunkSectionLayer = ItemBlockRenderTypes.getRenderLayer(fluidState);
				BufferBuilder bufferBuilder = this.getOrBeginLayer(map, sectionBufferBuilderPack, chunkSectionLayer);
				this.blockRenderer.renderLiquid(blockPos3, renderSectionRegion, bufferBuilder, blockState, fluidState);
			}

			if (blockState.getRenderShape() == RenderShape.MODEL) {
				ChunkSectionLayer chunkSectionLayer = ItemBlockRenderTypes.getChunkRenderType(blockState);
				BufferBuilder bufferBuilder = this.getOrBeginLayer(map, sectionBufferBuilderPack, chunkSectionLayer);
				randomSource.setSeed(blockState.getSeed(blockPos3));
				this.blockRenderer.getBlockModel(blockState).collectParts(randomSource, list);
				poseStack.pushPose();
				poseStack.translate(
					SectionPos.sectionRelative(blockPos3.getX()), SectionPos.sectionRelative(blockPos3.getY()), SectionPos.sectionRelative(blockPos3.getZ())
				);
				this.blockRenderer.renderBatched(blockState, blockPos3, renderSectionRegion, poseStack, bufferBuilder, true, list);
				poseStack.popPose();
				list.clear();
			}
		}

		for (Entry<ChunkSectionLayer, BufferBuilder> entry : map.entrySet()) {
			ChunkSectionLayer chunkSectionLayer2 = entry.getKey();
			MeshData meshData = entry.getValue().build();
			if (meshData != null) {
				if (chunkSectionLayer2 == ChunkSectionLayer.TRANSLUCENT) {
					results.transparencyState = meshData.sortQuads(sectionBufferBuilderPack.buffer(chunkSectionLayer2), vertexSorting);
				}

				results.renderedLayers.put(chunkSectionLayer2, meshData);
			}
		}

		ModelBlockRenderer.clearCache();
		results.visibilitySet = visGraph.resolve();
		return results;
	}

	private BufferBuilder getOrBeginLayer(
		Map<ChunkSectionLayer, BufferBuilder> map, SectionBufferBuilderPack sectionBufferBuilderPack, ChunkSectionLayer chunkSectionLayer
	) {
		BufferBuilder bufferBuilder = map.get(chunkSectionLayer);
		if (bufferBuilder == null) {
			ByteBufferBuilder byteBufferBuilder = sectionBufferBuilderPack.buffer(chunkSectionLayer);
			bufferBuilder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
			map.put(chunkSectionLayer, bufferBuilder);
		}

		return bufferBuilder;
	}

	private <E extends BlockEntity> void handleBlockEntity(SectionCompiler.Results results, E blockEntity) {
		BlockEntityRenderer<E, ?> blockEntityRenderer = this.blockEntityRenderer.getRenderer(blockEntity);
		if (blockEntityRenderer != null && !blockEntityRenderer.shouldRenderOffScreen()) {
			results.blockEntities.add(blockEntity);
		}
	}

	@Environment(EnvType.CLIENT)
	public static final class Results {
		public final List<BlockEntity> blockEntities = new ArrayList<>();
		public final Map<ChunkSectionLayer, MeshData> renderedLayers = new EnumMap<>(ChunkSectionLayer.class);
		public VisibilitySet visibilitySet = new VisibilitySet();
		public MeshData.@Nullable SortState transparencyState;

		public void release() {
			this.renderedLayers.values().forEach(MeshData::close);
		}
	}
}
