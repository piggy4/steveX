package net.minecraft.client.gui.render;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.OversizedItemRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.render.state.GlyphRenderState;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.pip.OversizedItemRenderState;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class GuiRenderer implements AutoCloseable {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final float MAX_GUI_Z = 10000.0F;
	public static final float MIN_GUI_Z = 0.0F;
	private static final float GUI_Z_NEAR = 1000.0F;
	public static final int GUI_3D_Z_FAR = 1000;
	public static final int GUI_3D_Z_NEAR = -1000;
	public static final int DEFAULT_ITEM_SIZE = 16;
	private static final int MINIMUM_ITEM_ATLAS_SIZE = 512;
	private static final int MAXIMUM_ITEM_ATLAS_SIZE = RenderSystem.getDevice().getMaxTextureSize();
	public static final int CLEAR_COLOR = 0;
	private static final Comparator<ScreenRectangle> SCISSOR_COMPARATOR = Comparator.nullsFirst(
		Comparator.comparing(ScreenRectangle::top).thenComparing(ScreenRectangle::bottom).thenComparing(ScreenRectangle::left).thenComparing(ScreenRectangle::right)
	);
	private static final Comparator<TextureSetup> TEXTURE_COMPARATOR = Comparator.nullsFirst(Comparator.comparing(TextureSetup::getSortKey));
	private static final Comparator<GuiElementRenderState> ELEMENT_SORT_COMPARATOR = Comparator.comparing(GuiElementRenderState::scissorArea, SCISSOR_COMPARATOR)
		.thenComparing(GuiElementRenderState::pipeline, Comparator.comparing(RenderPipeline::getSortKey))
		.thenComparing(GuiElementRenderState::textureSetup, TEXTURE_COMPARATOR);
	private final Map<Object, GuiRenderer.AtlasPosition> atlasPositions = new Object2ObjectOpenHashMap<>();
	private final Map<Object, OversizedItemRenderer> oversizedItemRenderers = new Object2ObjectOpenHashMap<>();
	final GuiRenderState renderState;
	private final List<GuiRenderer.Draw> draws = new ArrayList<>();
	private final List<GuiRenderer.MeshToDraw> meshesToDraw = new ArrayList<>();
	private final ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(786432);
	private final Map<VertexFormat, MappableRingBuffer> vertexBuffers = new Object2ObjectOpenHashMap<>();
	private int firstDrawIndexAfterBlur = Integer.MAX_VALUE;
	private final CachedOrthoProjectionMatrixBuffer guiProjectionMatrixBuffer = new CachedOrthoProjectionMatrixBuffer("gui", 1000.0F, 11000.0F, true);
	private final CachedOrthoProjectionMatrixBuffer itemsProjectionMatrixBuffer = new CachedOrthoProjectionMatrixBuffer("items", -1000.0F, 1000.0F, true);
	private final MultiBufferSource.BufferSource bufferSource;
	private final SubmitNodeCollector submitNodeCollector;
	private final FeatureRenderDispatcher featureRenderDispatcher;
	private final Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> pictureInPictureRenderers;
	private @Nullable GpuTexture itemsAtlas;
	private @Nullable GpuTextureView itemsAtlasView;
	private @Nullable GpuTexture itemsAtlasDepth;
	private @Nullable GpuTextureView itemsAtlasDepthView;
	private int itemAtlasX;
	private int itemAtlasY;
	private int cachedGuiScale;
	private int frameNumber;
	private @Nullable ScreenRectangle previousScissorArea = null;
	private @Nullable RenderPipeline previousPipeline = null;
	private @Nullable TextureSetup previousTextureSetup = null;
	private @Nullable BufferBuilder bufferBuilder = null;

	public GuiRenderer(
		GuiRenderState guiRenderState,
		MultiBufferSource.BufferSource bufferSource,
		SubmitNodeCollector submitNodeCollector,
		FeatureRenderDispatcher featureRenderDispatcher,
		List<PictureInPictureRenderer<?>> list
	) {
		this.renderState = guiRenderState;
		this.bufferSource = bufferSource;
		this.submitNodeCollector = submitNodeCollector;
		this.featureRenderDispatcher = featureRenderDispatcher;
		Builder<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> builder = ImmutableMap.builder();

		for (PictureInPictureRenderer<?> pictureInPictureRenderer : list) {
			builder.put((Class<? extends PictureInPictureRenderState>)pictureInPictureRenderer.getRenderStateClass(), pictureInPictureRenderer);
		}

		this.pictureInPictureRenderers = builder.buildOrThrow();
	}

	public void incrementFrameNumber() {
		this.frameNumber++;
	}

	public void render(GpuBufferSlice gpuBufferSlice) {
		this.prepare();
		this.draw(gpuBufferSlice);

		for (MappableRingBuffer mappableRingBuffer : this.vertexBuffers.values()) {
			mappableRingBuffer.rotate();
		}

		this.draws.clear();
		this.meshesToDraw.clear();
		this.renderState.reset();
		this.firstDrawIndexAfterBlur = Integer.MAX_VALUE;
		this.clearUnusedOversizedItemRenderers();
		if (SharedConstants.DEBUG_SHUFFLE_UI_RENDERING_ORDER) {
			RenderPipeline.updateSortKeySeed();
			TextureSetup.updateSortKeySeed();
		}
	}

	private void clearUnusedOversizedItemRenderers() {
		Iterator<Entry<Object, OversizedItemRenderer>> iterator = this.oversizedItemRenderers.entrySet().iterator();

		while (iterator.hasNext()) {
			Entry<Object, OversizedItemRenderer> entry = iterator.next();
			OversizedItemRenderer oversizedItemRenderer = entry.getValue();
			if (!oversizedItemRenderer.usedOnThisFrame()) {
				oversizedItemRenderer.close();
				iterator.remove();
			} else {
				oversizedItemRenderer.resetUsedOnThisFrame();
			}
		}
	}

	private void prepare() {
		this.bufferSource.endBatch();
		this.preparePictureInPicture();
		this.prepareItemElements();
		this.prepareText();
		this.renderState.sortElements(ELEMENT_SORT_COMPARATOR);
		this.addElementsToMeshes(GuiRenderState.TraverseRange.BEFORE_BLUR);
		this.firstDrawIndexAfterBlur = this.meshesToDraw.size();
		this.addElementsToMeshes(GuiRenderState.TraverseRange.AFTER_BLUR);
		this.recordDraws();
	}

	private void addElementsToMeshes(GuiRenderState.TraverseRange traverseRange) {
		this.previousScissorArea = null;
		this.previousPipeline = null;
		this.previousTextureSetup = null;
		this.bufferBuilder = null;
		this.renderState.forEachElement(this::addElementToMesh, traverseRange);
		if (this.bufferBuilder != null) {
			this.recordMesh(this.bufferBuilder, this.previousPipeline, this.previousTextureSetup, this.previousScissorArea);
		}
	}

	private void draw(GpuBufferSlice gpuBufferSlice) {
		if (!this.draws.isEmpty()) {
			Minecraft minecraft = Minecraft.getInstance();
			Window window = minecraft.getWindow();
			RenderSystem.setProjectionMatrix(
				this.guiProjectionMatrixBuffer.getBuffer((float)window.getWidth() / window.getGuiScale(), (float)window.getHeight() / window.getGuiScale()),
				ProjectionType.ORTHOGRAPHIC
			);
			RenderTarget renderTarget = minecraft.getMainRenderTarget();
			int i = 0;

			for (GuiRenderer.Draw draw : this.draws) {
				if (draw.indexCount > i) {
					i = draw.indexCount;
				}
			}

			RenderSystem.AutoStorageIndexBuffer autoStorageIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
			GpuBuffer gpuBuffer = autoStorageIndexBuffer.getBuffer(i);
			VertexFormat.IndexType indexType = autoStorageIndexBuffer.type();
			GpuBufferSlice gpuBufferSlice2 = RenderSystem.getDynamicUniforms()
				.writeTransform(new Matrix4f().setTranslation(0.0F, 0.0F, -11000.0F), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f());
			if (this.firstDrawIndexAfterBlur > 0) {
				this.executeDrawRange(
					() -> "GUI before blur", renderTarget, gpuBufferSlice, gpuBufferSlice2, gpuBuffer, indexType, 0, Math.min(this.firstDrawIndexAfterBlur, this.draws.size())
				);
			}

			if (this.draws.size() > this.firstDrawIndexAfterBlur) {
				RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(renderTarget.getDepthTexture(), 1.0);
				minecraft.gameRenderer.processBlurEffect();
				this.executeDrawRange(
					() -> "GUI after blur", renderTarget, gpuBufferSlice, gpuBufferSlice2, gpuBuffer, indexType, this.firstDrawIndexAfterBlur, this.draws.size()
				);
			}
		}
	}

	private void executeDrawRange(
		Supplier<String> supplier,
		RenderTarget renderTarget,
		GpuBufferSlice gpuBufferSlice,
		GpuBufferSlice gpuBufferSlice2,
		GpuBuffer gpuBuffer,
		VertexFormat.IndexType indexType,
		int i,
		int j
	) {
		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(
					supplier,
					renderTarget.getColorTextureView(),
					OptionalInt.empty(),
					renderTarget.useDepth ? renderTarget.getDepthTextureView() : null,
					OptionalDouble.empty()
				)) {
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("Fog", gpuBufferSlice);
			renderPass.setUniform("DynamicTransforms", gpuBufferSlice2);

			for (int k = i; k < j; k++) {
				GuiRenderer.Draw draw = this.draws.get(k);
				this.executeDraw(draw, renderPass, gpuBuffer, indexType);
			}
		}
	}

	private void addElementToMesh(GuiElementRenderState guiElementRenderState) {
		RenderPipeline renderPipeline = guiElementRenderState.pipeline();
		TextureSetup textureSetup = guiElementRenderState.textureSetup();
		ScreenRectangle screenRectangle = guiElementRenderState.scissorArea();
		if (renderPipeline != this.previousPipeline
			|| this.scissorChanged(screenRectangle, this.previousScissorArea)
			|| !textureSetup.equals(this.previousTextureSetup)) {
			if (this.bufferBuilder != null) {
				this.recordMesh(this.bufferBuilder, this.previousPipeline, this.previousTextureSetup, this.previousScissorArea);
			}

			this.bufferBuilder = this.getBufferBuilder(renderPipeline);
			this.previousPipeline = renderPipeline;
			this.previousTextureSetup = textureSetup;
			this.previousScissorArea = screenRectangle;
		}

		guiElementRenderState.buildVertices(this.bufferBuilder);
	}

	private void prepareText() {
		this.renderState.forEachText(guiTextRenderState -> {
			final Matrix3x2fc matrix3x2fc = guiTextRenderState.pose;
			final ScreenRectangle screenRectangle = guiTextRenderState.scissor;
			guiTextRenderState.ensurePrepared().visit(new Font.GlyphVisitor() {
				@Override
				public void acceptGlyph(TextRenderable.Styled styled) {
					this.accept(styled);
				}

				@Override
				public void acceptEffect(TextRenderable textRenderable) {
					this.accept(textRenderable);
				}

				private void accept(TextRenderable textRenderable) {
					GuiRenderer.this.renderState.submitGlyphToCurrentLayer(new GlyphRenderState(matrix3x2fc, textRenderable, screenRectangle));
				}
			});
		});
	}

	private void prepareItemElements() {
		if (!this.renderState.getItemModelIdentities().isEmpty()) {
			int i = this.getGuiScaleInvalidatingItemAtlasIfChanged();
			int j = 16 * i;
			int k = this.calculateAtlasSizeInPixels(j);
			if (this.itemsAtlas == null) {
				this.createAtlasTextures(k);
			}

			RenderSystem.outputColorTextureOverride = this.itemsAtlasView;
			RenderSystem.outputDepthTextureOverride = this.itemsAtlasDepthView;
			RenderSystem.setProjectionMatrix(this.itemsProjectionMatrixBuffer.getBuffer(k, k), ProjectionType.ORTHOGRAPHIC);
			Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
			PoseStack poseStack = new PoseStack();
			MutableBoolean mutableBoolean = new MutableBoolean(false);
			MutableBoolean mutableBoolean2 = new MutableBoolean(false);
			this.renderState
				.forEachItem(
					guiItemRenderState -> {
						if (guiItemRenderState.oversizedItemBounds() != null) {
							mutableBoolean2.setTrue();
						} else {
							TrackingItemStackRenderState trackingItemStackRenderState = guiItemRenderState.itemStackRenderState();
							GuiRenderer.AtlasPosition atlasPosition = this.atlasPositions.get(trackingItemStackRenderState.getModelIdentity());
							if (atlasPosition == null || trackingItemStackRenderState.isAnimated() && atlasPosition.lastAnimatedOnFrame != this.frameNumber) {
								if (this.itemAtlasX + j > k) {
									this.itemAtlasX = 0;
									this.itemAtlasY += j;
								}

								boolean bl = trackingItemStackRenderState.isAnimated() && atlasPosition != null;
								if (!bl && this.itemAtlasY + j > k) {
									if (mutableBoolean.isFalse()) {
										LOGGER.warn("Trying to render too many items in GUI at the same time. Skipping some of them.");
										mutableBoolean.setTrue();
									}
								} else {
									int kx = bl ? atlasPosition.x : this.itemAtlasX;
									int l = bl ? atlasPosition.y : this.itemAtlasY;
									if (bl) {
										RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(this.itemsAtlas, 0, this.itemsAtlasDepth, 1.0, kx, k - l - j, j, j);
									}

									this.renderItemToAtlas(trackingItemStackRenderState, poseStack, kx, l, j);
									float f = (float)kx / k;
									float g = (float)(k - l) / k;
									this.submitBlitFromItemAtlas(guiItemRenderState, f, g, j, k);
									if (bl) {
										atlasPosition.lastAnimatedOnFrame = this.frameNumber;
									} else {
										this.atlasPositions
											.put(
												guiItemRenderState.itemStackRenderState().getModelIdentity(),
												new GuiRenderer.AtlasPosition(this.itemAtlasX, this.itemAtlasY, f, g, this.frameNumber)
											);
										this.itemAtlasX += j;
									}
								}
							} else {
								this.submitBlitFromItemAtlas(guiItemRenderState, atlasPosition.u, atlasPosition.v, j, k);
							}
						}
					}
				);
			RenderSystem.outputColorTextureOverride = null;
			RenderSystem.outputDepthTextureOverride = null;
			if (mutableBoolean2.booleanValue()) {
				this.renderState
					.forEachItem(
						guiItemRenderState -> {
							if (guiItemRenderState.oversizedItemBounds() != null) {
								TrackingItemStackRenderState trackingItemStackRenderState = guiItemRenderState.itemStackRenderState();
								OversizedItemRenderer oversizedItemRenderer = this.oversizedItemRenderers
									.computeIfAbsent(trackingItemStackRenderState.getModelIdentity(), object -> new OversizedItemRenderer(this.bufferSource));
								ScreenRectangle screenRectangle = guiItemRenderState.oversizedItemBounds();
								OversizedItemRenderState oversizedItemRenderState = new OversizedItemRenderState(
									guiItemRenderState, screenRectangle.left(), screenRectangle.top(), screenRectangle.right(), screenRectangle.bottom()
								);
								oversizedItemRenderer.prepare(oversizedItemRenderState, this.renderState, i);
							}
						}
					);
			}
		}
	}

	private void preparePictureInPicture() {
		int i = Minecraft.getInstance().getWindow().getGuiScale();
		this.renderState.forEachPictureInPicture(pictureInPictureRenderState -> this.preparePictureInPictureState(pictureInPictureRenderState, i));
	}

	private <T extends PictureInPictureRenderState> void preparePictureInPictureState(T pictureInPictureRenderState, int i) {
		PictureInPictureRenderer<T> pictureInPictureRenderer = (PictureInPictureRenderer<T>)this.pictureInPictureRenderers
			.get(pictureInPictureRenderState.getClass());
		if (pictureInPictureRenderer != null) {
			pictureInPictureRenderer.prepare(pictureInPictureRenderState, this.renderState, i);
		}
	}

	private void renderItemToAtlas(TrackingItemStackRenderState trackingItemStackRenderState, PoseStack poseStack, int i, int j, int k) {
		poseStack.pushPose();
		poseStack.translate(i + k / 2.0F, j + k / 2.0F, 0.0F);
		poseStack.scale(k, -k, k);
		boolean bl = !trackingItemStackRenderState.usesBlockLight();
		if (bl) {
			Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_FLAT);
		} else {
			Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
		}

		RenderSystem.enableScissorForRenderTypeDraws(i, this.itemsAtlas.getHeight(0) - j - k, k, k);
		trackingItemStackRenderState.submit(poseStack, this.submitNodeCollector, 15728880, OverlayTexture.NO_OVERLAY, 0);
		this.featureRenderDispatcher.renderAllFeatures();
		this.bufferSource.endBatch();
		RenderSystem.disableScissorForRenderTypeDraws();
		poseStack.popPose();
	}

	private void submitBlitFromItemAtlas(GuiItemRenderState guiItemRenderState, float f, float g, int i, int j) {
		float h = f + (float)i / j;
		float k = g + (float)(-i) / j;
		this.renderState
			.submitBlitToCurrentLayer(
				new BlitRenderState(
					RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
					TextureSetup.singleTexture(this.itemsAtlasView, RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
					guiItemRenderState.pose(),
					guiItemRenderState.x(),
					guiItemRenderState.y(),
					guiItemRenderState.x() + 16,
					guiItemRenderState.y() + 16,
					f,
					h,
					g,
					k,
					-1,
					guiItemRenderState.scissorArea(),
					null
				)
			);
	}

	private void createAtlasTextures(int i) {
		GpuDevice gpuDevice = RenderSystem.getDevice();
		this.itemsAtlas = gpuDevice.createTexture("UI items atlas", 12, TextureFormat.RGBA8, i, i, 1, 1);
		this.itemsAtlasView = gpuDevice.createTextureView(this.itemsAtlas);
		this.itemsAtlasDepth = gpuDevice.createTexture("UI items atlas depth", 8, TextureFormat.DEPTH32, i, i, 1, 1);
		this.itemsAtlasDepthView = gpuDevice.createTextureView(this.itemsAtlasDepth);
		gpuDevice.createCommandEncoder().clearColorAndDepthTextures(this.itemsAtlas, 0, this.itemsAtlasDepth, 1.0);
	}

	private int calculateAtlasSizeInPixels(int i) {
		Set<Object> set = this.renderState.getItemModelIdentities();
		int j;
		if (this.atlasPositions.isEmpty()) {
			j = set.size();
		} else {
			j = this.atlasPositions.size();

			for (Object object : set) {
				if (!this.atlasPositions.containsKey(object)) {
					j++;
				}
			}
		}

		if (this.itemsAtlas != null) {
			int k = this.itemsAtlas.getWidth(0) / i;
			int l = k * k;
			if (j < l) {
				return this.itemsAtlas.getWidth(0);
			}

			this.invalidateItemAtlas();
		}

		int k = set.size();
		int l = Mth.smallestSquareSide(k + k / 2);
		return Math.clamp(Mth.smallestEncompassingPowerOfTwo(l * i), 512, MAXIMUM_ITEM_ATLAS_SIZE);
	}

	private int getGuiScaleInvalidatingItemAtlasIfChanged() {
		int i = Minecraft.getInstance().getWindow().getGuiScale();
		if (i != this.cachedGuiScale) {
			this.invalidateItemAtlas();

			for (OversizedItemRenderer oversizedItemRenderer : this.oversizedItemRenderers.values()) {
				oversizedItemRenderer.invalidateTexture();
			}

			this.cachedGuiScale = i;
		}

		return i;
	}

	private void invalidateItemAtlas() {
		this.itemAtlasX = 0;
		this.itemAtlasY = 0;
		this.atlasPositions.clear();
		if (this.itemsAtlas != null) {
			this.itemsAtlas.close();
			this.itemsAtlas = null;
		}

		if (this.itemsAtlasView != null) {
			this.itemsAtlasView.close();
			this.itemsAtlasView = null;
		}

		if (this.itemsAtlasDepth != null) {
			this.itemsAtlasDepth.close();
			this.itemsAtlasDepth = null;
		}

		if (this.itemsAtlasDepthView != null) {
			this.itemsAtlasDepthView.close();
			this.itemsAtlasDepthView = null;
		}
	}

	private void recordMesh(BufferBuilder bufferBuilder, RenderPipeline renderPipeline, TextureSetup textureSetup, @Nullable ScreenRectangle screenRectangle) {
		MeshData meshData = bufferBuilder.build();
		if (meshData != null) {
			this.meshesToDraw.add(new GuiRenderer.MeshToDraw(meshData, renderPipeline, textureSetup, screenRectangle));
		}
	}

	private void recordDraws() {
		this.ensureVertexBufferSizes();
		CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
		Object2IntMap<VertexFormat> object2IntMap = new Object2IntOpenHashMap<>();

		for (GuiRenderer.MeshToDraw meshToDraw : this.meshesToDraw) {
			MeshData meshData = meshToDraw.mesh;
			MeshData.DrawState drawState = meshData.drawState();
			VertexFormat vertexFormat = drawState.format();
			MappableRingBuffer mappableRingBuffer = this.vertexBuffers.get(vertexFormat);
			if (!object2IntMap.containsKey(vertexFormat)) {
				object2IntMap.put(vertexFormat, 0);
			}

			ByteBuffer byteBuffer = meshData.vertexBuffer();
			int i = byteBuffer.remaining();
			int j = object2IntMap.getInt(vertexFormat);

			try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(mappableRingBuffer.currentBuffer().slice(j, i), false, true)) {
				MemoryUtil.memCopy(byteBuffer, mappedView.data());
			}

			object2IntMap.put(vertexFormat, j + i);
			this.draws
				.add(
					new GuiRenderer.Draw(
						mappableRingBuffer.currentBuffer(),
						j / vertexFormat.getVertexSize(),
						drawState.mode(),
						drawState.indexCount(),
						meshToDraw.pipeline,
						meshToDraw.textureSetup,
						meshToDraw.scissorArea
					)
				);
			meshToDraw.close();
		}
	}

	private void ensureVertexBufferSizes() {
		Object2IntMap<VertexFormat> object2IntMap = this.calculatedRequiredVertexBufferSizes();

		for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<VertexFormat> entry : object2IntMap.object2IntEntrySet()) {
			VertexFormat vertexFormat = entry.getKey();
			int i = entry.getIntValue();
			MappableRingBuffer mappableRingBuffer = this.vertexBuffers.get(vertexFormat);
			if (mappableRingBuffer == null || mappableRingBuffer.size() < i) {
				if (mappableRingBuffer != null) {
					mappableRingBuffer.close();
				}

				this.vertexBuffers.put(vertexFormat, new MappableRingBuffer(() -> "GUI vertex buffer for " + vertexFormat, 34, i));
			}
		}
	}

	private Object2IntMap<VertexFormat> calculatedRequiredVertexBufferSizes() {
		Object2IntMap<VertexFormat> object2IntMap = new Object2IntOpenHashMap<>();

		for (GuiRenderer.MeshToDraw meshToDraw : this.meshesToDraw) {
			MeshData.DrawState drawState = meshToDraw.mesh.drawState();
			VertexFormat vertexFormat = drawState.format();
			if (!object2IntMap.containsKey(vertexFormat)) {
				object2IntMap.put(vertexFormat, 0);
			}

			object2IntMap.put(vertexFormat, object2IntMap.getInt(vertexFormat) + drawState.vertexCount() * vertexFormat.getVertexSize());
		}

		return object2IntMap;
	}

	private void executeDraw(GuiRenderer.Draw draw, RenderPass renderPass, GpuBuffer gpuBuffer, VertexFormat.IndexType indexType) {
		RenderPipeline renderPipeline = draw.pipeline();
		renderPass.setPipeline(renderPipeline);
		renderPass.setVertexBuffer(0, draw.vertexBuffer);
		ScreenRectangle screenRectangle = draw.scissorArea();
		if (screenRectangle != null) {
			this.enableScissor(screenRectangle, renderPass);
		} else {
			renderPass.disableScissor();
		}

		if (draw.textureSetup.texure0() != null) {
			renderPass.bindTexture("Sampler0", draw.textureSetup.texure0(), draw.textureSetup.sampler0());
		}

		if (draw.textureSetup.texure1() != null) {
			renderPass.bindTexture("Sampler1", draw.textureSetup.texure1(), draw.textureSetup.sampler1());
		}

		if (draw.textureSetup.texure2() != null) {
			renderPass.bindTexture("Sampler2", draw.textureSetup.texure2(), draw.textureSetup.sampler2());
		}

		renderPass.setIndexBuffer(gpuBuffer, indexType);
		renderPass.drawIndexed(draw.baseVertex, 0, draw.indexCount, 1);
	}

	private BufferBuilder getBufferBuilder(RenderPipeline renderPipeline) {
		return new BufferBuilder(this.byteBufferBuilder, renderPipeline.getVertexFormatMode(), renderPipeline.getVertexFormat());
	}

	private boolean scissorChanged(@Nullable ScreenRectangle screenRectangle, @Nullable ScreenRectangle screenRectangle2) {
		if (screenRectangle == screenRectangle2) {
			return false;
		} else {
			return screenRectangle != null ? !screenRectangle.equals(screenRectangle2) : true;
		}
	}

	private void enableScissor(ScreenRectangle screenRectangle, RenderPass renderPass) {
		Window window = Minecraft.getInstance().getWindow();
		int i = window.getHeight();
		int j = window.getGuiScale();
		double d = screenRectangle.left() * j;
		double e = i - screenRectangle.bottom() * j;
		double f = screenRectangle.width() * j;
		double g = screenRectangle.height() * j;
		renderPass.enableScissor((int)d, (int)e, Math.max(0, (int)f), Math.max(0, (int)g));
	}

	@Override
	public void close() {
		this.byteBufferBuilder.close();
		if (this.itemsAtlas != null) {
			this.itemsAtlas.close();
		}

		if (this.itemsAtlasView != null) {
			this.itemsAtlasView.close();
		}

		if (this.itemsAtlasDepth != null) {
			this.itemsAtlasDepth.close();
		}

		if (this.itemsAtlasDepthView != null) {
			this.itemsAtlasDepthView.close();
		}

		this.pictureInPictureRenderers.values().forEach(PictureInPictureRenderer::close);
		this.guiProjectionMatrixBuffer.close();
		this.itemsProjectionMatrixBuffer.close();

		for (MappableRingBuffer mappableRingBuffer : this.vertexBuffers.values()) {
			mappableRingBuffer.close();
		}

		this.oversizedItemRenderers.values().forEach(PictureInPictureRenderer::close);
	}

	@Environment(EnvType.CLIENT)
	static final class AtlasPosition {
		final int x;
		final int y;
		final float u;
		final float v;
		int lastAnimatedOnFrame;

		AtlasPosition(int i, int j, float f, float g, int k) {
			this.x = i;
			this.y = j;
			this.u = f;
			this.v = g;
			this.lastAnimatedOnFrame = k;
		}
	}

	@Environment(EnvType.CLIENT)
	record Draw(
		GpuBuffer vertexBuffer,
		int baseVertex,
		VertexFormat.Mode mode,
		int indexCount,
		RenderPipeline pipeline,
		TextureSetup textureSetup,
		@Nullable ScreenRectangle scissorArea
	) {
	}

	@Environment(EnvType.CLIENT)
	record MeshToDraw(MeshData mesh, RenderPipeline pipeline, TextureSetup textureSetup, @Nullable ScreenRectangle scissorArea) implements AutoCloseable {

		@Override
		public void close() {
			this.mesh.close();
		}
	}
}
