package net.minecraft.client.resources;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

@Environment(EnvType.CLIENT)
public class DefaultPlayerSkin {
	private static final PlayerSkin[] DEFAULT_SKINS = new PlayerSkin[]{
		create("entity/player/slim/alex", PlayerModelType.SLIM),
		create("entity/player/slim/ari", PlayerModelType.SLIM),
		create("entity/player/slim/efe", PlayerModelType.SLIM),
		create("entity/player/slim/kai", PlayerModelType.SLIM),
		create("entity/player/slim/makena", PlayerModelType.SLIM),
		create("entity/player/slim/noor", PlayerModelType.SLIM),
		create("entity/player/slim/steve", PlayerModelType.SLIM),
		create("entity/player/slim/sunny", PlayerModelType.SLIM),
		create("entity/player/slim/zuri", PlayerModelType.SLIM),
		create("entity/player/wide/alex", PlayerModelType.WIDE),
		create("entity/player/wide/ari", PlayerModelType.WIDE),
		create("entity/player/wide/efe", PlayerModelType.WIDE),
		create("entity/player/wide/kai", PlayerModelType.WIDE),
		create("entity/player/wide/makena", PlayerModelType.WIDE),
		create("entity/player/wide/noor", PlayerModelType.WIDE),
		create("entity/player/wide/steve", PlayerModelType.WIDE),
		create("entity/player/wide/sunny", PlayerModelType.WIDE),
		create("entity/player/wide/zuri", PlayerModelType.WIDE)
	};

	public static Identifier getDefaultTexture() {
		return getDefaultSkin().body().texturePath();
	}

	public static PlayerSkin getDefaultSkin() {
		return DEFAULT_SKINS[6];
	}

	public static PlayerSkin get(UUID uUID) {
		return DEFAULT_SKINS[Math.floorMod(uUID.hashCode(), DEFAULT_SKINS.length)];
	}

	public static PlayerSkin get(GameProfile gameProfile) {
		return get(gameProfile.id());
	}

	private static PlayerSkin create(String string, PlayerModelType playerModelType) {
		return new PlayerSkin(new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace(string)), null, null, playerModelType, true);
	}
}
