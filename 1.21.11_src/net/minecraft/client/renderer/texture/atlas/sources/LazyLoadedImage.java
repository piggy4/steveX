package net.minecraft.client.renderer.texture.atlas.sources;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class LazyLoadedImage {
	private final Identifier id;
	private final Resource resource;
	private final AtomicReference<@Nullable NativeImage> image = new AtomicReference<>();
	private final AtomicInteger referenceCount;

	public LazyLoadedImage(Identifier identifier, Resource resource, int i) {
		this.id = identifier;
		this.resource = resource;
		this.referenceCount = new AtomicInteger(i);
	}

	public NativeImage get() throws IOException {
		NativeImage nativeImage = this.image.get();
		if (nativeImage == null) {
			synchronized (this) {
				nativeImage = this.image.get();
				if (nativeImage == null) {
					try (InputStream inputStream = this.resource.open()) {
						nativeImage = NativeImage.read(inputStream);
						this.image.set(nativeImage);
					} catch (IOException iOException) {
						throw new IOException("Failed to load image " + this.id, iOException);
					}
				}
			}
		}

		return nativeImage;
	}

	public void release() {
		int i = this.referenceCount.decrementAndGet();
		if (i <= 0) {
			NativeImage nativeImage = this.image.getAndSet(null);
			if (nativeImage != null) {
				nativeImage.close();
			}
		}
	}
}
