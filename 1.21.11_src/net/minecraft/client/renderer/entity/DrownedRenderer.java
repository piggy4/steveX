package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.zombie.DrownedModel;
import net.minecraft.client.renderer.entity.layers.DrownedOuterLayer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
public class DrownedRenderer extends AbstractZombieRenderer<Drowned, ZombieRenderState, DrownedModel> {
	private static final Identifier DROWNED_LOCATION = Identifier.withDefaultNamespace("textures/entity/zombie/drowned.png");

	public DrownedRenderer(EntityRendererProvider.Context context) {
		super(
			context,
			new DrownedModel(context.bakeLayer(ModelLayers.DROWNED)),
			new DrownedModel(context.bakeLayer(ModelLayers.DROWNED_BABY)),
			ArmorModelSet.bake(ModelLayers.DROWNED_ARMOR, context.getModelSet(), DrownedModel::new),
			ArmorModelSet.bake(ModelLayers.DROWNED_BABY_ARMOR, context.getModelSet(), DrownedModel::new)
		);
		this.addLayer(new DrownedOuterLayer(this, context.getModelSet()));
	}

	public ZombieRenderState createRenderState() {
		return new ZombieRenderState();
	}

	@Override
	public Identifier getTextureLocation(ZombieRenderState zombieRenderState) {
		return DROWNED_LOCATION;
	}

	protected void setupRotations(ZombieRenderState zombieRenderState, PoseStack poseStack, float f, float g) {
		super.setupRotations(zombieRenderState, poseStack, f, g);
		float h = zombieRenderState.swimAmount;
		if (h > 0.0F) {
			float i = -10.0F - zombieRenderState.xRot;
			float j = Mth.lerp(h, 0.0F, i);
			poseStack.rotateAround(Axis.XP.rotationDegrees(j), 0.0F, zombieRenderState.boundingBoxHeight / 2.0F / g, 0.0F);
		}
	}

	protected HumanoidModel.ArmPose getArmPose(Drowned drowned, HumanoidArm humanoidArm) {
		ItemStack itemStack = drowned.getItemHeldByArm(humanoidArm);
		return drowned.getMainArm() == humanoidArm && drowned.isAggressive() && itemStack.is(Items.TRIDENT)
			? HumanoidModel.ArmPose.THROW_TRIDENT
			: super.getArmPose(drowned, humanoidArm);
	}
}
