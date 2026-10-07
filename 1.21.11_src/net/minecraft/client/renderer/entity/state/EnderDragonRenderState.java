package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.boss.enderdragon.DragonFlightHistory;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class EnderDragonRenderState extends EntityRenderState {
	public float flapTime;
	public float deathTime;
	public boolean hasRedOverlay;
	public @Nullable Vec3 beamOffset;
	public boolean isLandingOrTakingOff;
	public boolean isSitting;
	public double distanceToEgg;
	public float partialTicks;
	public final DragonFlightHistory flightHistory = new DragonFlightHistory();

	public DragonFlightHistory.Sample getHistoricalPos(int i) {
		return this.flightHistory.get(i, this.partialTicks);
	}

	public float getHeadPartYOffset(int i, DragonFlightHistory.Sample sample, DragonFlightHistory.Sample sample2) {
		double d;
		if (this.isLandingOrTakingOff) {
			d = i / Math.max(this.distanceToEgg / 4.0, 1.0);
		} else if (this.isSitting) {
			d = i;
		} else if (i == 6) {
			d = 0.0;
		} else {
			d = sample2.y() - sample.y();
		}

		return (float)d;
	}
}
