package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.renderer.v1.sprite.FabricSpriteAtlasTexture;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.renderer.v1.sprite.FabricSpriteAtlasTexture} injected by mod fabric-renderer-api-v1</p>
 */
@Environment(EnvType.CLIENT)
public class TextureAtlas extends AbstractTexture implements Dumpable, TickableTexture, FabricSpriteAtlasTexture {
	private static final Logger LOGGER = LogUtils.getLogger();
	@Deprecated
	public static final Identifier LOCATION_BLOCKS = Identifier.withDefaultNamespace("textures/atlas/blocks.png");
	@Deprecated
	public static final Identifier LOCATION_ITEMS = Identifier.withDefaultNamespace("textures/atlas/items.png");
	@Deprecated
	public static final Identifier LOCATION_PARTICLES = Identifier.withDefaultNamespace("textures/atlas/particles.png");
	private List<TextureAtlasSprite> sprites = List.of();
	private List<SpriteContents.AnimationState> animatedTexturesStates = List.of();
	private Map<Identifier, TextureAtlasSprite> texturesByName = Map.of();
	private @Nullable TextureAtlasSprite missingSprite;
	private final Identifier location;
	private final int maxSupportedTextureSize;
	private int width;
	private int height;
	private int maxMipLevel;
	private int mipLevelCount;
	private GpuTextureView[] mipViews = new GpuTextureView[0];
	private @Nullable GpuBuffer spriteUbos;

	public TextureAtlas(Identifier identifier) {
		this.location = identifier;
		this.maxSupportedTextureSize = RenderSystem.getDevice().getMaxTextureSize();
	}

	private void createTexture(int i, int j, int k) {
		LOGGER.info("Created: {}x{}x{} {}-atlas", i, j, k, this.location);
		GpuDevice gpuDevice = RenderSystem.getDevice();
		this.close();
		this.texture = gpuDevice.createTexture(this.location::toString, 15, TextureFormat.RGBA8, i, j, 1, k + 1);
		this.textureView = gpuDevice.createTextureView(this.texture);
		this.width = i;
		this.height = j;
		this.maxMipLevel = k;
		this.mipLevelCount = k + 1;
		this.mipViews = new GpuTextureView[this.mipLevelCount];

		for (int l = 0; l <= this.maxMipLevel; l++) {
			this.mipViews[l] = gpuDevice.createTextureView(this.texture, l, 1);
		}
	}

	public void upload(SpriteLoader.Preparations preparations) {
		this.createTexture(preparations.width(), preparations.height(), preparations.mipLevel());
		this.clearTextureData();
		this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		this.texturesByName = Map.copyOf(preparations.regions());
		this.missingSprite = this.texturesByName.get(MissingTextureAtlasSprite.getLocation());
		if (this.missingSprite == null) {
			throw new IllegalStateException("Atlas '" + this.location + "' (" + this.texturesByName.size() + " sprites) has no missing texture sprite");
		}

		List<TextureAtlasSprite> list = new ArrayList<>();
		List<SpriteContents.AnimationState> list2 = new ArrayList<>();
		int i = (int)preparations.regions().values().stream().filter(TextureAtlasSprite::isAnimated).count();
		int j = Mth.roundToward(SpriteContents.UBO_SIZE, RenderSystem.getDevice().getUniformOffsetAlignment());
		int k = j * this.mipLevelCount;
		ByteBuffer byteBuffer = MemoryUtil.memAlloc(i * k);
		int l = 0;

		for (TextureAtlasSprite textureAtlasSprite : preparations.regions().values()) {
			if (textureAtlasSprite.isAnimated()) {
				textureAtlasSprite.uploadSpriteUbo(byteBuffer, l * k, this.maxMipLevel, this.width, this.height, j);
				l++;
			}
		}

		GpuBuffer gpuBuffer = l > 0 ? RenderSystem.getDevice().createBuffer(() -> this.location + " sprite UBOs", 128, byteBuffer) : null;
		l = 0;

		for (TextureAtlasSprite textureAtlasSprite2 : preparations.regions().values()) {
			list.add(textureAtlasSprite2);
			if (textureAtlasSprite2.isAnimated() && gpuBuffer != null) {
				SpriteContents.AnimationState animationState = textureAtlasSprite2.createAnimationState(gpuBuffer.slice(l * k, k), j);
				l++;
				if (animationState != null) {
					list2.add(animationState);
				}
			}
		}

		this.spriteUbos = gpuBuffer;
		this.sprites = list;
		this.animatedTexturesStates = List.copyOf(list2);
		this.uploadInitialContents();
		if (SharedConstants.DEBUG_DUMP_TEXTURE_ATLAS) {
			Path path = TextureUtil.getDebugTexturePath();

			try {
				Files.createDirectories(path);
				this.dumpContents(this.location, path);
			} catch (Exception exception) {
				LOGGER.warn("Failed to dump atlas contents to {}", path);
			}
		}
	}

	private void uploadInitialContents() {
		GpuDevice gpuDevice = RenderSystem.getDevice();
		int i = Mth.roundToward(SpriteContents.UBO_SIZE, RenderSystem.getDevice().getUniformOffsetAlignment());
		int j = i * this.mipLevelCount;
		GpuSampler gpuSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, true);
		List<TextureAtlasSprite> list = this.sprites.stream().filter(textureAtlasSprite -> !textureAtlasSprite.isAnimated()).toList();
		List<GpuTextureView[]> list2 = new ArrayList<>();
		ByteBuffer byteBuffer = MemoryUtil.memAlloc(list.size() * j);

		for (int k = 0; k < list.size(); k++) {
			TextureAtlasSprite textureAtlasSprite = list.get(k);
			textureAtlasSprite.uploadSpriteUbo(byteBuffer, k * j, this.maxMipLevel, this.width, this.height, i);
			GpuTexture gpuTexture = gpuDevice.createTexture(
				() -> textureAtlasSprite.contents().name().toString(),
				5,
				TextureFormat.RGBA8,
				textureAtlasSprite.contents().width(),
				textureAtlasSprite.contents().height(),
				1,
				this.mipLevelCount
			);
			GpuTextureView[] gpuTextureViews = new GpuTextureView[this.mipLevelCount];

			for (int l = 0; l <= this.maxMipLevel; l++) {
				textureAtlasSprite.uploadFirstFrame(gpuTexture, l);
				gpuTextureViews[l] = gpuDevice.createTextureView(gpuTexture);
			}

			list2.add(gpuTextureViews);
		}

		try (GpuBuffer gpuBuffer = gpuDevice.createBuffer(() -> "SpriteAnimationInfo", 128, byteBuffer)) {
			for (int m = 0; m < this.mipLevelCount; m++) {
				try (RenderPass renderPass = RenderSystem.getDevice()
						.createCommandEncoder()
						.createRenderPass(() -> "Animate " + this.location, this.mipViews[m], OptionalInt.empty())) {
					renderPass.setPipeline(RenderPipelines.ANIMATE_SPRITE_BLIT);

					for (int n = 0; n < list.size(); n++) {
						renderPass.bindTexture("Sprite", list2.get(n)[m], gpuSampler);
						renderPass.setUniform("SpriteAnimationInfo", gpuBuffer.slice(n * j + m * i, SpriteContents.UBO_SIZE));
						renderPass.draw(0, 6);
					}
				}
			}
		}

		for (GpuTextureView[] gpuTextureViews2 : list2) {
			for (GpuTextureView gpuTextureView : gpuTextureViews2) {
				gpuTextureView.close();
				gpuTextureView.texture().close();
			}
		}

		MemoryUtil.memFree(byteBuffer);
		this.uploadAnimationFrames();
	}

	@Override
	public void dumpContents(Identifier identifier, Path path) throws IOException {
		String string = identifier.toDebugFileName();
		TextureUtil.writeAsPNG(path, string, this.getTexture(), this.maxMipLevel, i -> i);
		dumpSpriteNames(path, string, this.texturesByName);
	}

	private static void dumpSpriteNames(Path path, String string, Map<Identifier, TextureAtlasSprite> map) {
		Path path2 = path.resolve(string + ".txt");

		try (Writer writer = Files.newBufferedWriter(path2)) {
			for (Entry<Identifier, TextureAtlasSprite> entry : map.entrySet().stream().sorted(Entry.comparingByKey()).toList()) {
				TextureAtlasSprite textureAtlasSprite = entry.getValue();
				writer.write(
					String.format(
						Locale.ROOT,
						"%s\tx=%d\ty=%d\tw=%d\th=%d%n",
						entry.getKey(),
						textureAtlasSprite.getX(),
						textureAtlasSprite.getY(),
						textureAtlasSprite.contents().width(),
						textureAtlasSprite.contents().height()
					)
				);
			}
		} catch (IOException iOException) {
			LOGGER.warn("Failed to write file {}", path2, iOException);
		}
	}

	public void cycleAnimationFrames() {
		if (this.texture != null) {
			for (SpriteContents.AnimationState animationState : this.animatedTexturesStates) {
				animationState.tick();
			}

			this.uploadAnimationFrames();
		}
	}

	private void uploadAnimationFrames() {
		if (this.animatedTexturesStates.stream().anyMatch(SpriteContents.AnimationState::needsToDraw)) {
			for (int i = 0; i <= this.maxMipLevel; i++) {
				try (RenderPass renderPass = RenderSystem.getDevice()
						.createCommandEncoder()
						.createRenderPass(() -> "Animate " + this.location, this.mipViews[i], OptionalInt.empty())) {
					for (SpriteContents.AnimationState animationState : this.animatedTexturesStates) {
						if (animationState.needsToDraw()) {
							animationState.drawToAtlas(renderPass, animationState.getDrawUbo(i));
						}
					}
				}
			}
		}
	}

	@Override
	public void tick() {
		this.cycleAnimationFrames();
	}

	public TextureAtlasSprite getSprite(Identifier identifier) {
		TextureAtlasSprite textureAtlasSprite = this.texturesByName.getOrDefault(identifier, this.missingSprite);
		if (textureAtlasSprite == null) {
			throw new IllegalStateException("Tried to lookup sprite, but atlas is not initialized");
		} else {
			return textureAtlasSprite;
		}
	}

	public TextureAtlasSprite missingSprite() {
		return Objects.requireNonNull(this.missingSprite, "Atlas not initialized");
	}

	public void clearTextureData() {
		this.sprites.forEach(TextureAtlasSprite::close);
		this.sprites = List.of();
		this.animatedTexturesStates = List.of();
		this.texturesByName = Map.of();
		this.missingSprite = null;
	}

	@Override
	public void close() {
		super.close();

		for (GpuTextureView gpuTextureView : this.mipViews) {
			gpuTextureView.close();
		}

		for (SpriteContents.AnimationState animationState : this.animatedTexturesStates) {
			animationState.close();
		}

		if (this.spriteUbos != null) {
			this.spriteUbos.close();
			this.spriteUbos = null;
		}
	}

	public Identifier location() {
		return this.location;
	}

	public int maxSupportedTextureSize() {
		return this.maxSupportedTextureSize;
	}

	int getWidth() {
		return this.width;
	}

	int getHeight() {
		return this.height;
	}
}
