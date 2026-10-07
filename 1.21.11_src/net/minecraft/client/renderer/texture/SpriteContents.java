package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.metadata.animation.AnimationFrame;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class SpriteContents implements Stitcher.Entry, AutoCloseable {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final int UBO_SIZE = new Std140SizeCalculator().putMat4f().putMat4f().putFloat().putFloat().putInt().get();
	final Identifier name;
	final int width;
	final int height;
	private final NativeImage originalImage;
	NativeImage[] byMipLevel;
	private final SpriteContents.@Nullable AnimatedTexture animatedTexture;
	private final List<MetadataSectionType.WithValue<?>> additionalMetadata;
	private final MipmapStrategy mipmapStrategy;
	private final float alphaCutoffBias;

	public SpriteContents(Identifier identifier, FrameSize frameSize, NativeImage nativeImage) {
		this(identifier, frameSize, nativeImage, Optional.empty(), List.of(), Optional.empty());
	}

	public SpriteContents(
		Identifier identifier,
		FrameSize frameSize,
		NativeImage nativeImage,
		Optional<AnimationMetadataSection> optional,
		List<MetadataSectionType.WithValue<?>> list,
		Optional<TextureMetadataSection> optional2
	) {
		this.name = identifier;
		this.width = frameSize.width();
		this.height = frameSize.height();
		this.additionalMetadata = list;
		this.animatedTexture = optional.<SpriteContents.AnimatedTexture>map(
				animationMetadataSection -> this.createAnimatedTexture(frameSize, nativeImage.getWidth(), nativeImage.getHeight(), animationMetadataSection)
			)
			.orElse(null);
		this.originalImage = nativeImage;
		this.byMipLevel = new NativeImage[]{this.originalImage};
		this.mipmapStrategy = optional2.map(TextureMetadataSection::mipmapStrategy).orElse(MipmapStrategy.AUTO);
		this.alphaCutoffBias = optional2.map(TextureMetadataSection::alphaCutoffBias).orElse(0.0F);
	}

	public void increaseMipLevel(int i) {
		try {
			this.byMipLevel = MipmapGenerator.generateMipLevels(this.name, this.byMipLevel, i, this.mipmapStrategy, this.alphaCutoffBias);
		} catch (Throwable throwable) {
			CrashReport crashReport = CrashReport.forThrowable(throwable, "Generating mipmaps for frame");
			CrashReportCategory crashReportCategory = crashReport.addCategory("Frame being iterated");
			crashReportCategory.setDetail("Sprite name", this.name);
			crashReportCategory.setDetail("Sprite size", () -> this.width + " x " + this.height);
			crashReportCategory.setDetail("Sprite frames", () -> this.getFrameCount() + " frames");
			crashReportCategory.setDetail("Mipmap levels", i);
			crashReportCategory.setDetail("Original image size", () -> this.originalImage.getWidth() + "x" + this.originalImage.getHeight());
			throw new ReportedException(crashReport);
		}
	}

	private int getFrameCount() {
		return this.animatedTexture != null ? this.animatedTexture.frames.size() : 1;
	}

	public boolean isAnimated() {
		return this.getFrameCount() > 1;
	}

	private SpriteContents.@Nullable AnimatedTexture createAnimatedTexture(FrameSize frameSize, int i, int j, AnimationMetadataSection animationMetadataSection) {
		int k = i / frameSize.width();
		int l = j / frameSize.height();
		int m = k * l;
		int n = animationMetadataSection.defaultFrameTime();
		List<SpriteContents.FrameInfo> list;
		if (animationMetadataSection.frames().isEmpty()) {
			list = new ArrayList<>(m);

			for (int o = 0; o < m; o++) {
				list.add(new SpriteContents.FrameInfo(o, n));
			}
		} else {
			List<AnimationFrame> list2 = animationMetadataSection.frames().get();
			list = new ArrayList<>(list2.size());

			for (AnimationFrame animationFrame : list2) {
				list.add(new SpriteContents.FrameInfo(animationFrame.index(), animationFrame.timeOr(n)));
			}

			int p = 0;
			IntSet intSet = new IntOpenHashSet();

			for (Iterator<SpriteContents.FrameInfo> iterator = list.iterator(); iterator.hasNext(); p++) {
				SpriteContents.FrameInfo frameInfo = iterator.next();
				boolean bl = true;
				if (frameInfo.time <= 0) {
					LOGGER.warn("Invalid frame duration on sprite {} frame {}: {}", this.name, p, frameInfo.time);
					bl = false;
				}

				if (frameInfo.index < 0 || frameInfo.index >= m) {
					LOGGER.warn("Invalid frame index on sprite {} frame {}: {}", this.name, p, frameInfo.index);
					bl = false;
				}

				if (bl) {
					intSet.add(frameInfo.index);
				} else {
					iterator.remove();
				}
			}

			int[] is = IntStream.range(0, m).filter(ix -> !intSet.contains(ix)).toArray();
			if (is.length > 0) {
				LOGGER.warn("Unused frames in sprite {}: {}", this.name, Arrays.toString(is));
			}
		}

		return list.size() <= 1 ? null : new SpriteContents.AnimatedTexture(List.copyOf(list), k, animationMetadataSection.interpolatedFrames());
	}

	@Override
	public int width() {
		return this.width;
	}

	@Override
	public int height() {
		return this.height;
	}

	@Override
	public Identifier name() {
		return this.name;
	}

	public IntStream getUniqueFrames() {
		return this.animatedTexture != null ? this.animatedTexture.getUniqueFrames() : IntStream.of(1);
	}

	public SpriteContents.@Nullable AnimationState createAnimationState(GpuBufferSlice gpuBufferSlice, int i) {
		return this.animatedTexture != null ? this.animatedTexture.createAnimationState(gpuBufferSlice, i) : null;
	}

	public <T> Optional<T> getAdditionalMetadata(MetadataSectionType<T> metadataSectionType) {
		for (MetadataSectionType.WithValue<?> withValue : this.additionalMetadata) {
			Optional<T> optional = withValue.unwrapToType(metadataSectionType);
			if (optional.isPresent()) {
				return optional;
			}
		}

		return Optional.empty();
	}

	@Override
	public void close() {
		for (NativeImage nativeImage : this.byMipLevel) {
			nativeImage.close();
		}
	}

	@Override
	public String toString() {
		return "SpriteContents{name=" + this.name + ", frameCount=" + this.getFrameCount() + ", height=" + this.height + ", width=" + this.width + "}";
	}

	public boolean isTransparent(int i, int j, int k) {
		int l = j;
		int m = k;
		if (this.animatedTexture != null) {
			l += this.animatedTexture.getFrameX(i) * this.width;
			m += this.animatedTexture.getFrameY(i) * this.height;
		}

		return ARGB.alpha(this.originalImage.getPixel(l, m)) == 0;
	}

	public void uploadFirstFrame(GpuTexture gpuTexture, int i) {
		RenderSystem.getDevice().createCommandEncoder().writeToTexture(gpuTexture, this.byMipLevel[i], i, 0, 0, 0, this.width >> i, this.height >> i, 0, 0);
	}

	@Environment(EnvType.CLIENT)
	class AnimatedTexture {
		final List<SpriteContents.FrameInfo> frames;
		private final int frameRowSize;
		final boolean interpolateFrames;

		AnimatedTexture(final List<SpriteContents.FrameInfo> list, final int i, final boolean bl) {
			this.frames = list;
			this.frameRowSize = i;
			this.interpolateFrames = bl;
		}

		int getFrameX(int i) {
			return i % this.frameRowSize;
		}

		int getFrameY(int i) {
			return i / this.frameRowSize;
		}

		public SpriteContents.AnimationState createAnimationState(GpuBufferSlice gpuBufferSlice, int i) {
			GpuDevice gpuDevice = RenderSystem.getDevice();
			Int2ObjectMap<GpuTextureView> int2ObjectMap = new Int2ObjectOpenHashMap<>();
			GpuBufferSlice[] gpuBufferSlices = new GpuBufferSlice[SpriteContents.this.byMipLevel.length];

			for (int j : this.getUniqueFrames().toArray()) {
				GpuTexture gpuTexture = gpuDevice.createTexture(
					() -> SpriteContents.this.name + " animation frame " + j,
					5,
					TextureFormat.RGBA8,
					SpriteContents.this.width,
					SpriteContents.this.height,
					1,
					SpriteContents.this.byMipLevel.length + 1
				);
				int k = this.getFrameX(j) * SpriteContents.this.width;
				int l = this.getFrameY(j) * SpriteContents.this.height;

				for (int m = 0; m < SpriteContents.this.byMipLevel.length; m++) {
					RenderSystem.getDevice()
						.createCommandEncoder()
						.writeToTexture(
							gpuTexture, SpriteContents.this.byMipLevel[m], m, 0, 0, 0, SpriteContents.this.width >> m, SpriteContents.this.height >> m, k >> m, l >> m
						);
				}

				int2ObjectMap.put(j, RenderSystem.getDevice().createTextureView(gpuTexture));
			}

			for (int n = 0; n < SpriteContents.this.byMipLevel.length; n++) {
				gpuBufferSlices[n] = gpuBufferSlice.slice(n * i, i);
			}

			return SpriteContents.this.new AnimationState(this, int2ObjectMap, gpuBufferSlices);
		}

		public IntStream getUniqueFrames() {
			return this.frames.stream().mapToInt(frameInfo -> frameInfo.index).distinct();
		}
	}

	@Environment(EnvType.CLIENT)
	public class AnimationState implements AutoCloseable {
		private int frame;
		private int subFrame;
		private final SpriteContents.AnimatedTexture animationInfo;
		private final Int2ObjectMap<GpuTextureView> frameTexturesByIndex;
		private final GpuBufferSlice[] spriteUbosByMip;
		private boolean isDirty = true;

		AnimationState(
			final SpriteContents.AnimatedTexture animatedTexture, final Int2ObjectMap<GpuTextureView> int2ObjectMap, final GpuBufferSlice[] gpuBufferSlices
		) {
			this.animationInfo = animatedTexture;
			this.frameTexturesByIndex = int2ObjectMap;
			this.spriteUbosByMip = gpuBufferSlices;
		}

		public void tick() {
			this.subFrame++;
			this.isDirty = false;
			SpriteContents.FrameInfo frameInfo = this.animationInfo.frames.get(this.frame);
			if (this.subFrame >= frameInfo.time) {
				int i = frameInfo.index;
				this.frame = (this.frame + 1) % this.animationInfo.frames.size();
				this.subFrame = 0;
				int j = this.animationInfo.frames.get(this.frame).index;
				if (i != j) {
					this.isDirty = true;
				}
			}
		}

		public GpuBufferSlice getDrawUbo(int i) {
			return this.spriteUbosByMip[i];
		}

		public boolean needsToDraw() {
			return this.animationInfo.interpolateFrames || this.isDirty;
		}

		public void drawToAtlas(RenderPass renderPass, GpuBufferSlice gpuBufferSlice) {
			GpuSampler gpuSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, true);
			List<SpriteContents.FrameInfo> list = this.animationInfo.frames;
			int i = list.get(this.frame).index;
			float f = (float)this.subFrame / this.animationInfo.frames.get(this.frame).time;
			int j = (int)(f * 1000.0F);
			if (this.animationInfo.interpolateFrames) {
				int k = list.get((this.frame + 1) % list.size()).index;
				renderPass.setPipeline(RenderPipelines.ANIMATE_SPRITE_INTERPOLATE);
				renderPass.bindTexture("CurrentSprite", this.frameTexturesByIndex.get(i), gpuSampler);
				renderPass.bindTexture("NextSprite", this.frameTexturesByIndex.get(k), gpuSampler);
			} else if (this.isDirty) {
				renderPass.setPipeline(RenderPipelines.ANIMATE_SPRITE_BLIT);
				renderPass.bindTexture("Sprite", this.frameTexturesByIndex.get(i), gpuSampler);
			}

			renderPass.setUniform("SpriteAnimationInfo", gpuBufferSlice);
			renderPass.draw(j << 3, 6);
		}

		@Override
		public void close() {
			for (GpuTextureView gpuTextureView : this.frameTexturesByIndex.values()) {
				gpuTextureView.texture().close();
				gpuTextureView.close();
			}
		}
	}

	@Environment(EnvType.CLIENT)
	record FrameInfo(int index, int time) {
	}
}
