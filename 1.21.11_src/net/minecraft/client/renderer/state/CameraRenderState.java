package net.minecraft.client.renderer.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState} injected by mod fabric-rendering-v1</p>
 */
@Environment(EnvType.CLIENT)
public class CameraRenderState implements FabricRenderState {
	public BlockPos blockPos = BlockPos.ZERO;
	public Vec3 pos = new Vec3(0.0, 0.0, 0.0);
	public boolean initialized;
	public Vec3 entityPos = new Vec3(0.0, 0.0, 0.0);
	public Quaternionf orientation = new Quaternionf();
}
