package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;

@Environment(EnvType.CLIENT)
public class WitherSkeletonRenderer extends AbstractSkeletonRenderer<WitherSkeleton, SkeletonRenderState> {
	private static final Identifier WITHER_SKELETON_LOCATION = Identifier.withDefaultNamespace("textures/entity/skeleton/wither_skeleton.png");

	public WitherSkeletonRenderer(EntityRendererProvider.Context context) {
		super(context, ModelLayers.WITHER_SKELETON, ModelLayers.WITHER_SKELETON_ARMOR);
	}

	public Identifier getTextureLocation(SkeletonRenderState skeletonRenderState) {
		return WITHER_SKELETON_LOCATION;
	}

	public SkeletonRenderState createRenderState() {
		return new SkeletonRenderState();
	}
}
