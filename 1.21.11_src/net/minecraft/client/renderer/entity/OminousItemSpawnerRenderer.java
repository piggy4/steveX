package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.OminousItemSpawner;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public class OminousItemSpawnerRenderer extends EntityRenderer<OminousItemSpawner, ItemClusterRenderState> {
	private static final float ROTATION_SPEED = 40.0F;
	private static final int TICKS_SCALING = 50;
	private final ItemModelResolver itemModelResolver;
	private final RandomSource random = RandomSource.create();

	protected OminousItemSpawnerRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
	}

	public ItemClusterRenderState createRenderState() {
		return new ItemClusterRenderState();
	}

	public void extractRenderState(OminousItemSpawner ominousItemSpawner, ItemClusterRenderState itemClusterRenderState, float f) {
		super.extractRenderState(ominousItemSpawner, itemClusterRenderState, f);
		ItemStack itemStack = ominousItemSpawner.getItem();
		itemClusterRenderState.extractItemGroupRenderState(ominousItemSpawner, itemStack, this.itemModelResolver);
	}

	public void submit(
		ItemClusterRenderState itemClusterRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		if (!itemClusterRenderState.item.isEmpty()) {
			poseStack.pushPose();
			if (itemClusterRenderState.ageInTicks <= 50.0F) {
				float f = Math.min(itemClusterRenderState.ageInTicks, 50.0F) / 50.0F;
				poseStack.scale(f, f, f);
			}

			float f = Mth.wrapDegrees(itemClusterRenderState.ageInTicks * 40.0F);
			poseStack.mulPose(Axis.YP.rotationDegrees(f));
			ItemEntityRenderer.submitMultipleFromCount(poseStack, submitNodeCollector, 15728880, itemClusterRenderState, this.random);
			poseStack.popPose();
		}
	}
}
