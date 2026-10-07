package net.minecraft.client.renderer.blockentity.state;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;

@Environment(EnvType.CLIENT)
public class TestInstanceRenderState extends BlockEntityRenderState {
	public BeaconRenderState beaconRenderState;
	public BlockEntityWithBoundingBoxRenderState blockEntityWithBoundingBoxRenderState;
	public final List<TestInstanceBlockEntity.ErrorMarker> errorMarkers = new ArrayList<>();
}
