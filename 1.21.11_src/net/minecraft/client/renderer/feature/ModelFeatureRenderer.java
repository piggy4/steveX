package net.minecraft.client.renderer.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.ModelBakery;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class ModelFeatureRenderer {
	private final PoseStack poseStack = new PoseStack();

	public void render(
		SubmitNodeCollection submitNodeCollection,
		MultiBufferSource.BufferSource bufferSource,
		OutlineBufferSource outlineBufferSource,
		MultiBufferSource.BufferSource bufferSource2
	) {
		ModelFeatureRenderer.Storage storage = submitNodeCollection.getModelSubmits();
		this.renderBatch(bufferSource, outlineBufferSource, storage.opaqueModelSubmits, bufferSource2);
		storage.translucentModelSubmits.sort(Comparator.comparingDouble(translucentModelSubmit -> -translucentModelSubmit.position().lengthSquared()));
		this.renderTranslucents(bufferSource, outlineBufferSource, storage.translucentModelSubmits, bufferSource2);
	}

	private void renderTranslucents(
		MultiBufferSource.BufferSource bufferSource,
		OutlineBufferSource outlineBufferSource,
		List<SubmitNodeStorage.TranslucentModelSubmit<?>> list,
		MultiBufferSource.BufferSource bufferSource2
	) {
		for (SubmitNodeStorage.TranslucentModelSubmit<?> translucentModelSubmit : list) {
			this.renderModel(
				translucentModelSubmit.modelSubmit(),
				translucentModelSubmit.renderType(),
				bufferSource.getBuffer(translucentModelSubmit.renderType()),
				outlineBufferSource,
				bufferSource2
			);
		}
	}

	private void renderBatch(
		MultiBufferSource.BufferSource bufferSource,
		OutlineBufferSource outlineBufferSource,
		Map<RenderType, List<SubmitNodeStorage.ModelSubmit<?>>> map,
		MultiBufferSource.BufferSource bufferSource2
	) {
		Iterable<Entry<RenderType, List<SubmitNodeStorage.ModelSubmit<?>>>> iterable;
		if (SharedConstants.DEBUG_SHUFFLE_MODELS) {
			List<Entry<RenderType, List<SubmitNodeStorage.ModelSubmit<?>>>> list = new ArrayList<>(map.entrySet());
			Collections.shuffle(list);
			iterable = list;
		} else {
			iterable = map.entrySet();
		}

		for (Entry<RenderType, List<SubmitNodeStorage.ModelSubmit<?>>> entry : iterable) {
			VertexConsumer vertexConsumer = bufferSource.getBuffer(entry.getKey());

			for (SubmitNodeStorage.ModelSubmit<?> modelSubmit : entry.getValue()) {
				this.renderModel(modelSubmit, entry.getKey(), vertexConsumer, outlineBufferSource, bufferSource2);
			}
		}
	}

	private <S> void renderModel(
		SubmitNodeStorage.ModelSubmit<S> modelSubmit,
		RenderType renderType,
		VertexConsumer vertexConsumer,
		OutlineBufferSource outlineBufferSource,
		MultiBufferSource.BufferSource bufferSource
	) {
		this.poseStack.pushPose();
		this.poseStack.last().set(modelSubmit.pose());
		Model<? super S> model = modelSubmit.model();
		VertexConsumer vertexConsumer2 = modelSubmit.sprite() == null ? vertexConsumer : modelSubmit.sprite().wrap(vertexConsumer);
		model.setupAnim(modelSubmit.state());
		model.renderToBuffer(this.poseStack, vertexConsumer2, modelSubmit.lightCoords(), modelSubmit.overlayCoords(), modelSubmit.tintedColor());
		if (modelSubmit.outlineColor() != 0 && (renderType.outline().isPresent() || renderType.isOutline())) {
			outlineBufferSource.setColor(modelSubmit.outlineColor());
			VertexConsumer vertexConsumer3 = outlineBufferSource.getBuffer(renderType);
			model.renderToBuffer(
				this.poseStack,
				modelSubmit.sprite() == null ? vertexConsumer3 : modelSubmit.sprite().wrap(vertexConsumer3),
				modelSubmit.lightCoords(),
				modelSubmit.overlayCoords(),
				modelSubmit.tintedColor()
			);
		}

		if (modelSubmit.crumblingOverlay() != null && renderType.affectsCrumbling()) {
			VertexConsumer vertexConsumer3 = new SheetedDecalTextureGenerator(
				bufferSource.getBuffer(ModelBakery.DESTROY_TYPES.get(modelSubmit.crumblingOverlay().progress())), modelSubmit.crumblingOverlay().cameraPose(), 1.0F
			);
			model.renderToBuffer(
				this.poseStack,
				modelSubmit.sprite() == null ? vertexConsumer3 : modelSubmit.sprite().wrap(vertexConsumer3),
				modelSubmit.lightCoords(),
				modelSubmit.overlayCoords(),
				modelSubmit.tintedColor()
			);
		}

		this.poseStack.popPose();
	}

	@Environment(EnvType.CLIENT)
	public record CrumblingOverlay(int progress, PoseStack.Pose cameraPose) {
	}

	@Environment(EnvType.CLIENT)
	public static class Storage {
		final Map<RenderType, List<SubmitNodeStorage.ModelSubmit<?>>> opaqueModelSubmits = new HashMap<>();
		final List<SubmitNodeStorage.TranslucentModelSubmit<?>> translucentModelSubmits = new ArrayList<>();
		private final Set<RenderType> usedModelSubmitBuckets = new ObjectOpenHashSet<>();

		public void add(RenderType renderType, SubmitNodeStorage.ModelSubmit<?> modelSubmit) {
			if (renderType.pipeline().getBlendFunction().isEmpty()) {
				this.opaqueModelSubmits.computeIfAbsent(renderType, renderTypex -> new ArrayList<>()).add(modelSubmit);
			} else {
				Vector3f vector3f = modelSubmit.pose().pose().transformPosition(new Vector3f());
				this.translucentModelSubmits.add(new SubmitNodeStorage.TranslucentModelSubmit<>(modelSubmit, renderType, vector3f));
			}
		}

		public void clear() {
			this.translucentModelSubmits.clear();

			for (Entry<RenderType, List<SubmitNodeStorage.ModelSubmit<?>>> entry : this.opaqueModelSubmits.entrySet()) {
				List<SubmitNodeStorage.ModelSubmit<?>> list = entry.getValue();
				if (!list.isEmpty()) {
					this.usedModelSubmitBuckets.add(entry.getKey());
					list.clear();
				}
			}
		}

		public void endFrame() {
			this.opaqueModelSubmits.keySet().removeIf(renderType -> !this.usedModelSubmitBuckets.contains(renderType));
			this.usedModelSubmitBuckets.clear();
		}
	}
}
