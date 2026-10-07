package net.minecraft.client.renderer.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState} injected by mod fabric-rendering-v1</p>
 */
@Environment(EnvType.CLIENT)
public record BlockOutlineRenderState(
	BlockPos pos,
	boolean isTranslucent,
	boolean highContrast,
	VoxelShape shape,
	@Nullable VoxelShape collisionShape,
	@Nullable VoxelShape occlusionShape,
	@Nullable VoxelShape interactionShape
) implements FabricRenderState {
	public BlockOutlineRenderState(BlockPos blockPos, boolean bl, boolean bl2, VoxelShape voxelShape) {
		this(blockPos, bl, bl2, voxelShape, null, null, null);
	}
}
