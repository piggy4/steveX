package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.MinecartTntRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.level.block.state.BlockState;

@Environment(EnvType.CLIENT)
public class TntMinecartRenderer extends AbstractMinecartRenderer<MinecartTNT, MinecartTntRenderState> {
	public TntMinecartRenderer(EntityRendererProvider.Context context) {
		super(context, ModelLayers.TNT_MINECART);
	}

	protected void submitMinecartContents(
		MinecartTntRenderState minecartTntRenderState, BlockState blockState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i
	) {
		float f = minecartTntRenderState.fuseRemainingInTicks;
		if (f > -1.0F && f < 10.0F) {
			float g = 1.0F - f / 10.0F;
			g = Mth.clamp(g, 0.0F, 1.0F);
			g *= g;
			g *= g;
			float h = 1.0F + g * 0.3F;
			poseStack.scale(h, h, h);
		}

		submitWhiteSolidBlock(blockState, poseStack, submitNodeCollector, i, f > -1.0F && (int)f / 5 % 2 == 0, minecartTntRenderState.outlineColor);
	}

	public static void submitWhiteSolidBlock(BlockState blockState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, boolean bl, int j) {
		int k;
		if (bl) {
			k = OverlayTexture.pack(OverlayTexture.u(1.0F), 10);
		} else {
			k = OverlayTexture.NO_OVERLAY;
		}

		submitNodeCollector.submitBlock(poseStack, blockState, i, k, j);
	}

	public MinecartTntRenderState createRenderState() {
		return new MinecartTntRenderState();
	}

	public void extractRenderState(MinecartTNT minecartTNT, MinecartTntRenderState minecartTntRenderState, float f) {
		super.extractRenderState(minecartTNT, minecartTntRenderState, f);
		minecartTntRenderState.fuseRemainingInTicks = minecartTNT.getFuse() > -1 ? minecartTNT.getFuse() - f + 1.0F : -1.0F;
	}
}
