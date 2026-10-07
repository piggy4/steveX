package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.shulker.ShulkerModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.ShulkerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ShulkerRenderer extends MobRenderer<Shulker, ShulkerRenderState, ShulkerModel> {
	private static final Identifier DEFAULT_TEXTURE_LOCATION = Sheets.DEFAULT_SHULKER_TEXTURE_LOCATION.texture().withPath(string -> "textures/" + string + ".png");
	private static final Identifier[] TEXTURE_LOCATION = Sheets.SHULKER_TEXTURE_LOCATION
		.stream()
		.map(material -> material.texture().withPath(string -> "textures/" + string + ".png"))
		.toArray(Identifier[]::new);

	public ShulkerRenderer(EntityRendererProvider.Context context) {
		super(context, new ShulkerModel(context.bakeLayer(ModelLayers.SHULKER)), 0.0F);
	}

	public Vec3 getRenderOffset(ShulkerRenderState shulkerRenderState) {
		return shulkerRenderState.renderOffset;
	}

	public boolean shouldRender(Shulker shulker, Frustum frustum, double d, double e, double f) {
		if (super.shouldRender(shulker, frustum, d, e, f)) {
			return true;
		}

		Vec3 vec3 = shulker.getRenderPosition(0.0F);
		if (vec3 == null) {
			return false;
		}

		EntityType<?> entityType = shulker.getType();
		float g = entityType.getHeight() / 2.0F;
		float h = entityType.getWidth() / 2.0F;
		Vec3 vec32 = Vec3.atBottomCenterOf(shulker.blockPosition());
		return frustum.isVisible(new AABB(vec3.x, vec3.y + g, vec3.z, vec32.x, vec32.y + g, vec32.z).inflate(h, g, h));
	}

	public Identifier getTextureLocation(ShulkerRenderState shulkerRenderState) {
		return getTextureLocation(shulkerRenderState.color);
	}

	public ShulkerRenderState createRenderState() {
		return new ShulkerRenderState();
	}

	public void extractRenderState(Shulker shulker, ShulkerRenderState shulkerRenderState, float f) {
		super.extractRenderState(shulker, shulkerRenderState, f);
		shulkerRenderState.renderOffset = Objects.requireNonNullElse(shulker.getRenderPosition(f), Vec3.ZERO);
		shulkerRenderState.color = shulker.getColor();
		shulkerRenderState.peekAmount = shulker.getClientPeekAmount(f);
		shulkerRenderState.yHeadRot = shulker.yHeadRot;
		shulkerRenderState.yBodyRot = shulker.yBodyRot;
		shulkerRenderState.attachFace = shulker.getAttachFace();
	}

	public static Identifier getTextureLocation(@Nullable DyeColor dyeColor) {
		return dyeColor == null ? DEFAULT_TEXTURE_LOCATION : TEXTURE_LOCATION[dyeColor.getId()];
	}

	protected void setupRotations(ShulkerRenderState shulkerRenderState, PoseStack poseStack, float f, float g) {
		super.setupRotations(shulkerRenderState, poseStack, f + 180.0F, g);
		poseStack.rotateAround(shulkerRenderState.attachFace.getOpposite().getRotation(), 0.0F, 0.5F, 0.0F);
	}
}
