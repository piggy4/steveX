package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class EndCrystalRenderState extends EntityRenderState {
	public boolean showsBottom = true;
	public @Nullable Vec3 beamOffset;
}
