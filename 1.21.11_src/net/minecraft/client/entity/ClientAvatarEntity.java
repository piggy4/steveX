package net.minecraft.client.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface ClientAvatarEntity {
	ClientAvatarState avatarState();

	PlayerSkin getSkin();

	@Nullable Component belowNameDisplay();

	Parrot.@Nullable Variant getParrotVariantOnShoulder(boolean bl);

	boolean showExtraEars();
}
