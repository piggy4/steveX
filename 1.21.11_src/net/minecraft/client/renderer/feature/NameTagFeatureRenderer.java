package net.minecraft.client.renderer.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class NameTagFeatureRenderer {
	public void render(SubmitNodeCollection submitNodeCollection, MultiBufferSource.BufferSource bufferSource, Font font) {
		NameTagFeatureRenderer.Storage storage = submitNodeCollection.getNameTagSubmits();
		storage.nameTagSubmitsSeethrough.sort(Comparator.comparing(SubmitNodeStorage.NameTagSubmit::distanceToCameraSq).reversed());

		for (SubmitNodeStorage.NameTagSubmit nameTagSubmit : storage.nameTagSubmitsSeethrough) {
			font.drawInBatch(
				nameTagSubmit.text(),
				nameTagSubmit.x(),
				nameTagSubmit.y(),
				nameTagSubmit.color(),
				false,
				nameTagSubmit.pose(),
				bufferSource,
				Font.DisplayMode.SEE_THROUGH,
				nameTagSubmit.backgroundColor(),
				nameTagSubmit.lightCoords()
			);
		}

		for (SubmitNodeStorage.NameTagSubmit nameTagSubmit : storage.nameTagSubmitsNormal) {
			font.drawInBatch(
				nameTagSubmit.text(),
				nameTagSubmit.x(),
				nameTagSubmit.y(),
				nameTagSubmit.color(),
				false,
				nameTagSubmit.pose(),
				bufferSource,
				Font.DisplayMode.NORMAL,
				nameTagSubmit.backgroundColor(),
				nameTagSubmit.lightCoords()
			);
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Storage {
		final List<SubmitNodeStorage.NameTagSubmit> nameTagSubmitsSeethrough = new ArrayList<>();
		final List<SubmitNodeStorage.NameTagSubmit> nameTagSubmitsNormal = new ArrayList<>();

		public void add(PoseStack poseStack, @Nullable Vec3 vec3, int i, Component component, boolean bl, int j, double d, CameraRenderState cameraRenderState) {
			if (vec3 != null) {
				Minecraft minecraft = Minecraft.getInstance();
				poseStack.pushPose();
				poseStack.translate(vec3.x, vec3.y + 0.5, vec3.z);
				poseStack.mulPose(cameraRenderState.orientation);
				poseStack.scale(0.025F, -0.025F, 0.025F);
				Matrix4f matrix4f = new Matrix4f(poseStack.last().pose());
				float f = -minecraft.font.width(component) / 2.0F;
				int k = (int)(minecraft.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
				if (bl) {
					this.nameTagSubmitsNormal.add(new SubmitNodeStorage.NameTagSubmit(matrix4f, f, i, component, LightTexture.lightCoordsWithEmission(j, 2), -1, 0, d));
					this.nameTagSubmitsSeethrough.add(new SubmitNodeStorage.NameTagSubmit(matrix4f, f, i, component, j, -2130706433, k, d));
				} else {
					this.nameTagSubmitsNormal.add(new SubmitNodeStorage.NameTagSubmit(matrix4f, f, i, component, j, -2130706433, k, d));
				}

				poseStack.popPose();
			}
		}

		public void clear() {
			this.nameTagSubmitsNormal.clear();
			this.nameTagSubmitsSeethrough.clear();
		}
	}
}
