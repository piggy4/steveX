package net.minecraft.client.renderer;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ScreenEffectRenderer {
	private static final Identifier UNDERWATER_LOCATION = Identifier.withDefaultNamespace("textures/misc/underwater.png");
	private final Minecraft minecraft;
	private final MaterialSet materials;
	private final MultiBufferSource bufferSource;
	public static final int ITEM_ACTIVATION_ANIMATION_LENGTH = 40;
	private @Nullable ItemStack itemActivationItem;
	private int itemActivationTicks;
	private float itemActivationOffX;
	private float itemActivationOffY;

	public ScreenEffectRenderer(Minecraft minecraft, MaterialSet materialSet, MultiBufferSource multiBufferSource) {
		this.minecraft = minecraft;
		this.materials = materialSet;
		this.bufferSource = multiBufferSource;
	}

	public void tick() {
		if (this.itemActivationTicks > 0) {
			this.itemActivationTicks--;
			if (this.itemActivationTicks == 0) {
				this.itemActivationItem = null;
			}
		}
	}

	public void renderScreenEffect(boolean bl, float f, SubmitNodeCollector submitNodeCollector) {
		PoseStack poseStack = new PoseStack();
		Player player = this.minecraft.player;
		if (this.minecraft.options.getCameraType().isFirstPerson() && !bl) {
			if (!player.noPhysics) {
				BlockState blockState = getViewBlockingState(player);
				if (blockState != null) {
					renderTex(this.minecraft.getBlockRenderer().getBlockModelShaper().getParticleIcon(blockState), poseStack, this.bufferSource);
				}
			}

			if (!this.minecraft.player.isSpectator()) {
				if (this.minecraft.player.isEyeInFluid(FluidTags.WATER)) {
					renderWater(this.minecraft, poseStack, this.bufferSource);
				}

				if (this.minecraft.player.isOnFire()) {
					TextureAtlasSprite textureAtlasSprite = this.materials.get(ModelBakery.FIRE_1);
					renderFire(poseStack, this.bufferSource, textureAtlasSprite);
				}
			}
		}

		if (!this.minecraft.options.hideGui) {
			this.renderItemActivationAnimation(poseStack, f, submitNodeCollector);
		}
	}

	private void renderItemActivationAnimation(PoseStack poseStack, float f, SubmitNodeCollector submitNodeCollector) {
		if (this.itemActivationItem != null && this.itemActivationTicks > 0) {
			int i = 40 - this.itemActivationTicks;
			float g = (i + f) / 40.0F;
			float h = g * g;
			float j = g * h;
			float k = 10.25F * j * h - 24.95F * h * h + 25.5F * j - 13.8F * h + 4.0F * g;
			float l = k * (float) Math.PI;
			float m = (float)this.minecraft.getWindow().getWidth() / this.minecraft.getWindow().getHeight();
			float n = this.itemActivationOffX * 0.3F * m;
			float o = this.itemActivationOffY * 0.3F;
			poseStack.pushPose();
			poseStack.translate(n * Mth.abs(Mth.sin(l * 2.0F)), o * Mth.abs(Mth.sin(l * 2.0F)), -10.0F + 9.0F * Mth.sin(l));
			float p = 0.8F;
			poseStack.scale(0.8F, 0.8F, 0.8F);
			poseStack.mulPose(Axis.YP.rotationDegrees(900.0F * Mth.abs(Mth.sin(l))));
			poseStack.mulPose(Axis.XP.rotationDegrees(6.0F * Mth.cos(g * 8.0F)));
			poseStack.mulPose(Axis.ZP.rotationDegrees(6.0F * Mth.cos(g * 8.0F)));
			this.minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
			ItemStackRenderState itemStackRenderState = new ItemStackRenderState();
			this.minecraft
				.getItemModelResolver()
				.updateForTopItem(itemStackRenderState, this.itemActivationItem, ItemDisplayContext.FIXED, this.minecraft.level, null, 0);
			itemStackRenderState.submit(poseStack, submitNodeCollector, 15728880, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}

	public void resetItemActivation() {
		this.itemActivationItem = null;
	}

	public void displayItemActivation(ItemStack itemStack, RandomSource randomSource) {
		this.itemActivationItem = itemStack;
		this.itemActivationTicks = 40;
		this.itemActivationOffX = randomSource.nextFloat() * 2.0F - 1.0F;
		this.itemActivationOffY = randomSource.nextFloat() * 2.0F - 1.0F;
	}

	private static @Nullable BlockState getViewBlockingState(Player player) {
		BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

		for (int i = 0; i < 8; i++) {
			double d = player.getX() + ((i >> 0) % 2 - 0.5F) * player.getBbWidth() * 0.8F;
			double e = player.getEyeY() + ((i >> 1) % 2 - 0.5F) * 0.1F * player.getScale();
			double f = player.getZ() + ((i >> 2) % 2 - 0.5F) * player.getBbWidth() * 0.8F;
			mutableBlockPos.set(d, e, f);
			BlockState blockState = player.level().getBlockState(mutableBlockPos);
			if (blockState.getRenderShape() != RenderShape.INVISIBLE && blockState.isViewBlocking(player.level(), mutableBlockPos)) {
				return blockState;
			}
		}

		return null;
	}

	private static void renderTex(TextureAtlasSprite textureAtlasSprite, PoseStack poseStack, MultiBufferSource multiBufferSource) {
		float f = 0.1F;
		int i = ARGB.colorFromFloat(1.0F, 0.1F, 0.1F, 0.1F);
		float g = -1.0F;
		float h = 1.0F;
		float j = -1.0F;
		float k = 1.0F;
		float l = -0.5F;
		float m = textureAtlasSprite.getU0();
		float n = textureAtlasSprite.getU1();
		float o = textureAtlasSprite.getV0();
		float p = textureAtlasSprite.getV1();
		Matrix4f matrix4f = poseStack.last().pose();
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderTypes.blockScreenEffect(textureAtlasSprite.atlasLocation()));
		vertexConsumer.addVertex(matrix4f, -1.0F, -1.0F, -0.5F).setUv(n, p).setColor(i);
		vertexConsumer.addVertex(matrix4f, 1.0F, -1.0F, -0.5F).setUv(m, p).setColor(i);
		vertexConsumer.addVertex(matrix4f, 1.0F, 1.0F, -0.5F).setUv(m, o).setColor(i);
		vertexConsumer.addVertex(matrix4f, -1.0F, 1.0F, -0.5F).setUv(n, o).setColor(i);
	}

	private static void renderWater(Minecraft minecraft, PoseStack poseStack, MultiBufferSource multiBufferSource) {
		BlockPos blockPos = BlockPos.containing(minecraft.player.getX(), minecraft.player.getEyeY(), minecraft.player.getZ());
		float f = LightTexture.getBrightness(minecraft.player.level().dimensionType(), minecraft.player.level().getMaxLocalRawBrightness(blockPos));
		int i = ARGB.colorFromFloat(0.1F, f, f, f);
		float g = 4.0F;
		float h = -1.0F;
		float j = 1.0F;
		float k = -1.0F;
		float l = 1.0F;
		float m = -0.5F;
		float n = -minecraft.player.getYRot() / 64.0F;
		float o = minecraft.player.getXRot() / 64.0F;
		Matrix4f matrix4f = poseStack.last().pose();
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderTypes.blockScreenEffect(UNDERWATER_LOCATION));
		vertexConsumer.addVertex(matrix4f, -1.0F, -1.0F, -0.5F).setUv(4.0F + n, 4.0F + o).setColor(i);
		vertexConsumer.addVertex(matrix4f, 1.0F, -1.0F, -0.5F).setUv(0.0F + n, 4.0F + o).setColor(i);
		vertexConsumer.addVertex(matrix4f, 1.0F, 1.0F, -0.5F).setUv(0.0F + n, 0.0F + o).setColor(i);
		vertexConsumer.addVertex(matrix4f, -1.0F, 1.0F, -0.5F).setUv(4.0F + n, 0.0F + o).setColor(i);
	}

	private static void renderFire(PoseStack poseStack, MultiBufferSource multiBufferSource, TextureAtlasSprite textureAtlasSprite) {
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderTypes.fireScreenEffect(textureAtlasSprite.atlasLocation()));
		float f = textureAtlasSprite.getU0();
		float g = textureAtlasSprite.getU1();
		float h = textureAtlasSprite.getV0();
		float i = textureAtlasSprite.getV1();
		float j = 1.0F;

		for (int k = 0; k < 2; k++) {
			poseStack.pushPose();
			float l = -0.5F;
			float m = 0.5F;
			float n = -0.5F;
			float o = 0.5F;
			float p = -0.5F;
			poseStack.translate(-(k * 2 - 1) * 0.24F, -0.3F, 0.0F);
			poseStack.mulPose(Axis.YP.rotationDegrees((k * 2 - 1) * 10.0F));
			Matrix4f matrix4f = poseStack.last().pose();
			vertexConsumer.addVertex(matrix4f, -0.5F, -0.5F, -0.5F).setUv(g, i).setColor(1.0F, 1.0F, 1.0F, 0.9F);
			vertexConsumer.addVertex(matrix4f, 0.5F, -0.5F, -0.5F).setUv(f, i).setColor(1.0F, 1.0F, 1.0F, 0.9F);
			vertexConsumer.addVertex(matrix4f, 0.5F, 0.5F, -0.5F).setUv(f, h).setColor(1.0F, 1.0F, 1.0F, 0.9F);
			vertexConsumer.addVertex(matrix4f, -0.5F, 0.5F, -0.5F).setUv(g, h).setColor(1.0F, 1.0F, 1.0F, 0.9F);
			poseStack.popPose();
		}
	}
}
