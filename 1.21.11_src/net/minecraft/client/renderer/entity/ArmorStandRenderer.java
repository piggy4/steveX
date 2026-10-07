package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.armorstand.ArmorStandArmorModel;
import net.minecraft.client.model.object.armorstand.ArmorStandModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ArmorStandRenderer extends LivingEntityRenderer<ArmorStand, ArmorStandRenderState, ArmorStandArmorModel> {
	public static final Identifier DEFAULT_SKIN_LOCATION = Identifier.withDefaultNamespace("textures/entity/armorstand/wood.png");
	private final ArmorStandArmorModel bigModel = this.getModel();
	private final ArmorStandArmorModel smallModel;

	public ArmorStandRenderer(EntityRendererProvider.Context context) {
		super(context, new ArmorStandModel(context.bakeLayer(ModelLayers.ARMOR_STAND)), 0.0F);
		this.smallModel = new ArmorStandModel(context.bakeLayer(ModelLayers.ARMOR_STAND_SMALL));
		this.addLayer(
			new HumanoidArmorLayer<>(
				this,
				ArmorModelSet.bake(ModelLayers.ARMOR_STAND_ARMOR, context.getModelSet(), ArmorStandArmorModel::new),
				ArmorModelSet.bake(ModelLayers.ARMOR_STAND_SMALL_ARMOR, context.getModelSet(), ArmorStandArmorModel::new),
				context.getEquipmentRenderer()
			)
		);
		this.addLayer(new ItemInHandLayer<>(this));
		this.addLayer(new WingsLayer<>(this, context.getModelSet(), context.getEquipmentRenderer()));
		this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getPlayerSkinRenderCache()));
	}

	public Identifier getTextureLocation(ArmorStandRenderState armorStandRenderState) {
		return DEFAULT_SKIN_LOCATION;
	}

	public ArmorStandRenderState createRenderState() {
		return new ArmorStandRenderState();
	}

	public void extractRenderState(ArmorStand armorStand, ArmorStandRenderState armorStandRenderState, float f) {
		super.extractRenderState(armorStand, armorStandRenderState, f);
		HumanoidMobRenderer.extractHumanoidRenderState(armorStand, armorStandRenderState, f, this.itemModelResolver);
		armorStandRenderState.yRot = Mth.rotLerp(f, armorStand.yRotO, armorStand.getYRot());
		armorStandRenderState.isMarker = armorStand.isMarker();
		armorStandRenderState.isSmall = armorStand.isSmall();
		armorStandRenderState.showArms = armorStand.showArms();
		armorStandRenderState.showBasePlate = armorStand.showBasePlate();
		armorStandRenderState.bodyPose = armorStand.getBodyPose();
		armorStandRenderState.headPose = armorStand.getHeadPose();
		armorStandRenderState.leftArmPose = armorStand.getLeftArmPose();
		armorStandRenderState.rightArmPose = armorStand.getRightArmPose();
		armorStandRenderState.leftLegPose = armorStand.getLeftLegPose();
		armorStandRenderState.rightLegPose = armorStand.getRightLegPose();
		armorStandRenderState.wiggle = (float)(armorStand.level().getGameTime() - armorStand.lastHit) + f;
	}

	public void submit(
		ArmorStandRenderState armorStandRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		this.model = armorStandRenderState.isSmall ? this.smallModel : this.bigModel;
		super.submit(armorStandRenderState, poseStack, submitNodeCollector, cameraRenderState);
	}

	protected void setupRotations(ArmorStandRenderState armorStandRenderState, PoseStack poseStack, float f, float g) {
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - f));
		if (armorStandRenderState.wiggle < 5.0F) {
			poseStack.mulPose(Axis.YP.rotationDegrees(Mth.sin(armorStandRenderState.wiggle / 1.5F * (float) Math.PI) * 3.0F));
		}
	}

	protected boolean shouldShowName(ArmorStand armorStand, double d) {
		return armorStand.isCustomNameVisible();
	}

	protected @Nullable RenderType getRenderType(ArmorStandRenderState armorStandRenderState, boolean bl, boolean bl2, boolean bl3) {
		if (!armorStandRenderState.isMarker) {
			return super.getRenderType(armorStandRenderState, bl, bl2, bl3);
		} else {
			Identifier identifier = this.getTextureLocation(armorStandRenderState);
			if (bl2) {
				return RenderTypes.entityTranslucent(identifier, false);
			} else {
				return bl ? RenderTypes.entityCutoutNoCull(identifier, false) : null;
			}
		}
	}
}
