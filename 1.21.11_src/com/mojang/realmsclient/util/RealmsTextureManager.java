package com.mojang.realmsclient.util;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class RealmsTextureManager {
	private static final Map<String, RealmsTextureManager.RealmsTexture> TEXTURES = Maps.newHashMap();
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Identifier TEMPLATE_ICON_LOCATION = Identifier.withDefaultNamespace("textures/gui/presets/isles.png");

	public static Identifier worldTemplate(String string, @Nullable String string2) {
		return string2 == null ? TEMPLATE_ICON_LOCATION : getTexture(string, string2);
	}

	private static Identifier getTexture(String string, String string2) {
		RealmsTextureManager.RealmsTexture realmsTexture = TEXTURES.get(string);
		if (realmsTexture != null && realmsTexture.image().equals(string2)) {
			return realmsTexture.textureId;
		} else {
			NativeImage nativeImage = loadImage(string2);
			if (nativeImage == null) {
				Identifier identifier = MissingTextureAtlasSprite.getLocation();
				TEXTURES.put(string, new RealmsTextureManager.RealmsTexture(string2, identifier));
				return identifier;
			} else {
				Identifier identifier = Identifier.fromNamespaceAndPath("realms", "dynamic/" + string);
				Minecraft.getInstance().getTextureManager().register(identifier, new DynamicTexture(identifier::toString, nativeImage));
				TEXTURES.put(string, new RealmsTextureManager.RealmsTexture(string2, identifier));
				return identifier;
			}
		}
	}

	private static @Nullable NativeImage loadImage(String string) {
		byte[] bs = Base64.getDecoder().decode(string);
		ByteBuffer byteBuffer = MemoryUtil.memAlloc(bs.length);

		try {
			return NativeImage.read(byteBuffer.put(bs).flip());
		} catch (IOException iOException) {
			LOGGER.warn("Failed to load world image: {}", string, iOException);
		} finally {
			MemoryUtil.memFree(byteBuffer);
		}

		return null;
	}

	@Environment(EnvType.CLIENT)
	public record RealmsTexture(String image, Identifier textureId) {
	}
}
