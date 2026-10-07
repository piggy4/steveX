package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class CatRenderState extends FelineRenderState {
	private static final Identifier DEFAULT_TEXTURE = Identifier.withDefaultNamespace("textures/entity/cat/tabby.png");
	public Identifier texture = DEFAULT_TEXTURE;
	public boolean isLyingOnTopOfSleepingPlayer;
	public @Nullable DyeColor collarColor;
}
