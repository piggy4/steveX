package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Display;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class DisplayEntityRenderState extends EntityRenderState {
	public Display.@Nullable RenderState renderState;
	public float interpolationProgress;
	public float entityYRot;
	public float entityXRot;
	public float cameraYRot;
	public float cameraXRot;

	public abstract boolean hasSubState();
}
