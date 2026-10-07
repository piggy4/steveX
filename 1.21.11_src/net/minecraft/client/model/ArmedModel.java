package net.minecraft.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.HumanoidArm;

@Environment(EnvType.CLIENT)
public interface ArmedModel<T extends EntityRenderState> {
	void translateToHand(T entityRenderState, HumanoidArm humanoidArm, PoseStack poseStack);
}
