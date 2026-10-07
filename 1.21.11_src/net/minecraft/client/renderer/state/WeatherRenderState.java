package net.minecraft.client.renderer.state;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.renderer.WeatherEffectRenderer;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState} injected by mod fabric-rendering-v1</p>
 */
@Environment(EnvType.CLIENT)
public class WeatherRenderState implements FabricRenderState {
	public final List<WeatherEffectRenderer.ColumnInstance> rainColumns = new ArrayList<>();
	public final List<WeatherEffectRenderer.ColumnInstance> snowColumns = new ArrayList<>();
	public float intensity;
	public int radius;

	public void reset() {
		this.rainColumns.clear();
		this.snowColumns.clear();
		this.intensity = 0.0F;
		this.radius = 0;
	}
}
