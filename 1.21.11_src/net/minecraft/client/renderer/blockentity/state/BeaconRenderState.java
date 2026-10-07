package net.minecraft.client.renderer.blockentity.state;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class BeaconRenderState extends BlockEntityRenderState {
	public float animationTime;
	public float beamRadiusScale;
	public List<BeaconRenderState.Section> sections = new ArrayList<>();

	@Environment(EnvType.CLIENT)
	public record Section(int color, int height) {
	}
}
