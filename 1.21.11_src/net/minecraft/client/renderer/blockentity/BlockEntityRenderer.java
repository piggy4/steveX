package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface BlockEntityRenderer<T extends BlockEntity, S extends BlockEntityRenderState> {
	S createRenderState();

	default void extractRenderState(T blockEntity, S blockEntityRenderState, float f, Vec3 vec3, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
		BlockEntityRenderState.extractBase(blockEntity, blockEntityRenderState, crumblingOverlay);
	}

	void submit(S blockEntityRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState);

	default boolean shouldRenderOffScreen() {
		return false;
	}

	default int getViewDistance() {
		return 64;
	}

	default boolean shouldRender(T blockEntity, Vec3 vec3) {
		return Vec3.atCenterOf(blockEntity.getBlockPos()).closerThan(vec3, this.getViewDistance());
	}
}
