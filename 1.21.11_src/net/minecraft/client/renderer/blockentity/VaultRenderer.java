package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.VaultRenderState;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultClientData;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class VaultRenderer implements BlockEntityRenderer<VaultBlockEntity, VaultRenderState> {
	private final ItemModelResolver itemModelResolver;
	private final RandomSource random = RandomSource.create();

	public VaultRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModelResolver = context.itemModelResolver();
	}

	public VaultRenderState createRenderState() {
		return new VaultRenderState();
	}

	public void extractRenderState(
		VaultBlockEntity vaultBlockEntity, VaultRenderState vaultRenderState, float f, Vec3 vec3, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer.super.extractRenderState(vaultBlockEntity, vaultRenderState, f, vec3, crumblingOverlay);
		ItemStack itemStack = vaultBlockEntity.getSharedData().getDisplayItem();
		if (VaultBlockEntity.Client.shouldDisplayActiveEffects(vaultBlockEntity.getSharedData()) && !itemStack.isEmpty() && vaultBlockEntity.getLevel() != null) {
			vaultRenderState.displayItem = new ItemClusterRenderState();
			this.itemModelResolver.updateForTopItem(vaultRenderState.displayItem.item, itemStack, ItemDisplayContext.GROUND, vaultBlockEntity.getLevel(), null, 0);
			vaultRenderState.displayItem.count = ItemClusterRenderState.getRenderedAmount(itemStack.getCount());
			vaultRenderState.displayItem.seed = ItemClusterRenderState.getSeedForItemStack(itemStack);
			VaultClientData vaultClientData = vaultBlockEntity.getClientData();
			vaultRenderState.spin = Mth.rotLerp(f, vaultClientData.previousSpin(), vaultClientData.currentSpin());
		}
	}

	public void submit(VaultRenderState vaultRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		if (vaultRenderState.displayItem != null) {
			poseStack.pushPose();
			poseStack.translate(0.5F, 0.4F, 0.5F);
			poseStack.mulPose(Axis.YP.rotationDegrees(vaultRenderState.spin));
			ItemEntityRenderer.renderMultipleFromCount(poseStack, submitNodeCollector, vaultRenderState.lightCoords, vaultRenderState.displayItem, this.random);
			poseStack.popPose();
		}
	}
}
