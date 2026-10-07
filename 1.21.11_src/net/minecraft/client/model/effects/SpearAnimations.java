package net.minecraft.client.model.effects;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Ease;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.KineticWeapon;

@Environment(EnvType.CLIENT)
public class SpearAnimations {
	static float progress(float f, float g, float h) {
		return Mth.clamp(Mth.inverseLerp(f, g, h), 0.0F, 1.0F);
	}

	public static <T extends HumanoidRenderState> void thirdPersonHandUse(
		ModelPart modelPart, ModelPart modelPart2, boolean bl, ItemStack itemStack, T humanoidRenderState
	) {
		int i = bl ? 1 : -1;
		modelPart.yRot = -0.1F * i + modelPart2.yRot;
		modelPart.xRot = (float) (-Math.PI / 2) + modelPart2.xRot + 0.8F;
		if (humanoidRenderState.isFallFlying || humanoidRenderState.swimAmount > 0.0F) {
			modelPart.xRot -= 0.9599311F;
		}

		modelPart.yRot = (float) (Math.PI / 180.0) * Math.clamp((180.0F / (float)Math.PI) * modelPart.yRot, -60.0F, 60.0F);
		modelPart.xRot = (float) (Math.PI / 180.0) * Math.clamp((180.0F / (float)Math.PI) * modelPart.xRot, -120.0F, 30.0F);
		if (!(humanoidRenderState.ticksUsingItem <= 0.0F)
			&& (!humanoidRenderState.isUsingItem || humanoidRenderState.useItemHand == (bl ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND))) {
			KineticWeapon kineticWeapon = itemStack.get(DataComponents.KINETIC_WEAPON);
			if (kineticWeapon != null) {
				SpearAnimations.UseParams useParams = SpearAnimations.UseParams.fromKineticWeapon(kineticWeapon, humanoidRenderState.ticksUsingItem);
				modelPart.yRot = modelPart.yRot + -i * useParams.swayScaleFast() * (float) (Math.PI / 180.0) * useParams.swayIntensity() * 1.0F;
				modelPart.zRot = modelPart.zRot + -i * useParams.swayScaleSlow() * (float) (Math.PI / 180.0) * useParams.swayIntensity() * 0.5F;
				modelPart.xRot = modelPart.xRot
					+ (float) (Math.PI / 180.0)
						* (
							-40.0F * useParams.raiseProgressStart()
								+ 30.0F * useParams.raiseProgressMiddle()
								+ -20.0F * useParams.raiseProgressEnd()
								+ 20.0F * useParams.lowerProgress()
								+ 10.0F * useParams.raiseBackProgress()
								+ 0.6F * useParams.swayScaleSlow() * useParams.swayIntensity()
						);
			}
		}
	}

	public static <S extends ArmedEntityRenderState> void thirdPersonUseItem(
		S armedEntityRenderState, PoseStack poseStack, float f, HumanoidArm humanoidArm, ItemStack itemStack
	) {
		KineticWeapon kineticWeapon = itemStack.get(DataComponents.KINETIC_WEAPON);
		if (kineticWeapon != null && f != 0.0F) {
			float g = Ease.inQuad(progress(armedEntityRenderState.attackTime, 0.05F, 0.2F));
			float h = Ease.inOutExpo(progress(armedEntityRenderState.attackTime, 0.4F, 1.0F));
			SpearAnimations.UseParams useParams = SpearAnimations.UseParams.fromKineticWeapon(kineticWeapon, f);
			int i = humanoidArm == HumanoidArm.RIGHT ? 1 : -1;
			float j = 1.0F - Ease.outBack(1.0F - useParams.raiseProgress());
			float k = 0.125F;
			float l = hitFeedbackAmount(armedEntityRenderState.ticksSinceKineticHitFeedback);
			poseStack.translate(0.0, -l * 0.4, -kineticWeapon.forwardMovement() * (j - useParams.raiseBackProgress()) + l);
			poseStack.rotateAround(
				Axis.XN.rotationDegrees(70.0F * (useParams.raiseProgress() - useParams.raiseBackProgress()) - 40.0F * (g - h)), 0.0F, -0.03125F, 0.125F
			);
			poseStack.rotateAround(Axis.YP.rotationDegrees(i * 90 * (useParams.raiseProgress() - useParams.swayProgress() + 3.0F * h + g)), 0.0F, 0.0F, 0.125F);
		}
	}

	public static <T extends HumanoidRenderState> void thirdPersonAttackHand(HumanoidModel<T> humanoidModel, T humanoidRenderState) {
		float f = humanoidRenderState.attackTime;
		HumanoidArm humanoidArm = humanoidRenderState.attackArm;
		humanoidModel.rightArm.yRot = humanoidModel.rightArm.yRot - humanoidModel.body.yRot;
		humanoidModel.leftArm.yRot = humanoidModel.leftArm.yRot - humanoidModel.body.yRot;
		humanoidModel.leftArm.xRot = humanoidModel.leftArm.xRot - humanoidModel.body.yRot;
		float g = Ease.inOutSine(progress(f, 0.0F, 0.05F));
		float h = Ease.inQuad(progress(f, 0.05F, 0.2F));
		float i = Ease.inOutExpo(progress(f, 0.4F, 1.0F));
		humanoidModel.getArm(humanoidArm).xRot += (90.0F * g - 120.0F * h + 30.0F * i) * (float) (Math.PI / 180.0);
	}

	public static <S extends ArmedEntityRenderState> void thirdPersonAttackItem(S armedEntityRenderState, PoseStack poseStack) {
		if (!(armedEntityRenderState.attackTime <= 0.0F)) {
			KineticWeapon kineticWeapon = armedEntityRenderState.getMainHandItemStack().get(DataComponents.KINETIC_WEAPON);
			float f = kineticWeapon != null ? kineticWeapon.forwardMovement() : 0.0F;
			float g = 0.125F;
			float h = armedEntityRenderState.attackTime;
			float i = Ease.inQuad(progress(h, 0.05F, 0.2F));
			float j = Ease.inOutExpo(progress(h, 0.4F, 1.0F));
			poseStack.rotateAround(Axis.XN.rotationDegrees(70.0F * (i - j)), 0.0F, -0.125F, 0.125F);
			poseStack.translate(0.0F, f * (i - j), 0.0F);
		}
	}

	private static float hitFeedbackAmount(float f) {
		return 0.4F * (Ease.outQuart(progress(f, 1.0F, 3.0F)) - Ease.inOutSine(progress(f, 3.0F, 10.0F)));
	}

	public static void firstPersonUse(float f, PoseStack poseStack, float g, HumanoidArm humanoidArm, ItemStack itemStack) {
		KineticWeapon kineticWeapon = itemStack.get(DataComponents.KINETIC_WEAPON);
		if (kineticWeapon != null) {
			SpearAnimations.UseParams useParams = SpearAnimations.UseParams.fromKineticWeapon(kineticWeapon, g);
			int i = humanoidArm == HumanoidArm.RIGHT ? 1 : -1;
			poseStack.translate(
				i * (useParams.raiseProgress() * 0.15F + useParams.raiseProgressEnd() * -0.05F + useParams.swayProgress() * -0.1F + useParams.swayScaleSlow() * 0.005F),
				useParams.raiseProgress() * -0.075F + useParams.raiseProgressMiddle() * 0.075F + useParams.swayScaleFast() * 0.01F,
				useParams.raiseProgressStart() * 0.05 + useParams.raiseProgressEnd() * -0.05 + useParams.swayScaleSlow() * 0.005F
			);
			poseStack.rotateAround(
				Axis.XP
					.rotationDegrees(
						-65.0F * Ease.inOutBack(useParams.raiseProgress())
							- 35.0F * useParams.lowerProgress()
							+ 100.0F * useParams.raiseBackProgress()
							+ -0.5F * useParams.swayScaleFast()
					),
				0.0F,
				0.1F,
				0.0F
			);
			poseStack.rotateAround(
				Axis.YN
					.rotationDegrees(i * (-90.0F * progress(useParams.raiseProgress(), 0.5F, 0.55F) + 90.0F * useParams.swayProgress() + 2.0F * useParams.swayScaleSlow())),
				i * 0.15F,
				0.0F,
				0.0F
			);
			poseStack.translate(0.0F, -hitFeedbackAmount(f), 0.0F);
		}
	}

	public static void firstPersonAttack(float f, PoseStack poseStack, int i, HumanoidArm humanoidArm) {
		float g = Ease.inOutSine(progress(f, 0.0F, 0.05F));
		float h = Ease.outBack(progress(f, 0.05F, 0.2F));
		float j = Ease.inOutExpo(progress(f, 0.4F, 1.0F));
		poseStack.translate(i * 0.1F * (g - h), -0.075F * (g - j), 0.65F * (g - h));
		poseStack.mulPose(Axis.XP.rotationDegrees(-70.0F * (g - j)));
		poseStack.translate(0.0, 0.0, -0.25 * (j - h));
	}

	@Environment(EnvType.CLIENT)
	record UseParams(
		float raiseProgress,
		float raiseProgressStart,
		float raiseProgressMiddle,
		float raiseProgressEnd,
		float swayProgress,
		float lowerProgress,
		float raiseBackProgress,
		float swayIntensity,
		float swayScaleSlow,
		float swayScaleFast
	) {
		public static SpearAnimations.UseParams fromKineticWeapon(KineticWeapon kineticWeapon, float f) {
			int i = kineticWeapon.delayTicks();
			int j = kineticWeapon.dismountConditions().map(KineticWeapon.Condition::maxDurationTicks).orElse(0) + i;
			int k = j - 20;
			int l = kineticWeapon.knockbackConditions().map(KineticWeapon.Condition::maxDurationTicks).orElse(0) + i;
			int m = l - 40;
			int n = kineticWeapon.damageConditions().map(KineticWeapon.Condition::maxDurationTicks).orElse(0) + i;
			float g = SpearAnimations.progress(f, 0.0F, i);
			float h = SpearAnimations.progress(g, 0.0F, 0.5F);
			float o = SpearAnimations.progress(g, 0.5F, 0.8F);
			float p = SpearAnimations.progress(g, 0.8F, 1.0F);
			float q = SpearAnimations.progress(f, k, m);
			float r = Ease.outCubic(Ease.inOutElastic(SpearAnimations.progress(f - 20.0F, m, l)));
			float s = SpearAnimations.progress(f, n - 5, n);
			float t = 2.0F * Ease.outCirc(q) - 2.0F * Ease.inCirc(s);
			float u = Mth.sin(f * 19.0F * (float) (Math.PI / 180.0)) * t;
			float v = Mth.sin(f * 30.0F * (float) (Math.PI / 180.0)) * t;
			return new SpearAnimations.UseParams(g, h, o, p, q, r, s, t, u, v);
		}
	}
}
