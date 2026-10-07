package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
public abstract class AbstractSkeletonRenderer<T extends AbstractSkeleton, S extends SkeletonRenderState> extends HumanoidMobRenderer<T, S, SkeletonModel<S>> {
	public AbstractSkeletonRenderer(EntityRendererProvider.Context context, ModelLayerLocation modelLayerLocation, ArmorModelSet<ModelLayerLocation> armorModelSet) {
		this(context, armorModelSet, new SkeletonModel<>(context.bakeLayer(modelLayerLocation)));
	}

	public AbstractSkeletonRenderer(EntityRendererProvider.Context context, ArmorModelSet<ModelLayerLocation> armorModelSet, SkeletonModel<S> skeletonModel) {
		super(context, skeletonModel, 0.5F);
		this.addLayer(new HumanoidArmorLayer<>(this, ArmorModelSet.bake(armorModelSet, context.getModelSet(), SkeletonModel::new), context.getEquipmentRenderer()));
	}

	public void extractRenderState(T abstractSkeleton, S skeletonRenderState, float f) {
		super.extractRenderState(abstractSkeleton, skeletonRenderState, f);
		skeletonRenderState.isAggressive = abstractSkeleton.isAggressive();
		skeletonRenderState.isShaking = abstractSkeleton.isShaking();
		skeletonRenderState.isHoldingBow = abstractSkeleton.getMainHandItem().is(Items.BOW);
	}

	protected boolean isShaking(S skeletonRenderState) {
		return skeletonRenderState.isShaking;
	}

	protected HumanoidModel.ArmPose getArmPose(T abstractSkeleton, HumanoidArm humanoidArm) {
		return abstractSkeleton.getMainArm() == humanoidArm && abstractSkeleton.isAggressive() && abstractSkeleton.getMainHandItem().is(Items.BOW)
			? HumanoidModel.ArmPose.BOW_AND_ARROW
			: super.getArmPose(abstractSkeleton, humanoidArm);
	}
}
