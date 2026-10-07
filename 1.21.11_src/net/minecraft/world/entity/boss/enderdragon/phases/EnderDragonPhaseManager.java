package net.minecraft.world.entity.boss.enderdragon.phases;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class EnderDragonPhaseManager {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final EnderDragon dragon;
	private final @Nullable DragonPhaseInstance[] phases = new DragonPhaseInstance[EnderDragonPhase.getCount()];
	private @Nullable DragonPhaseInstance currentPhase;

	public EnderDragonPhaseManager(EnderDragon enderDragon) {
		this.dragon = enderDragon;
		this.setPhase(EnderDragonPhase.HOVERING);
	}

	public void setPhase(EnderDragonPhase<?> enderDragonPhase) {
		if (this.currentPhase == null || enderDragonPhase != this.currentPhase.getPhase()) {
			if (this.currentPhase != null) {
				this.currentPhase.end();
			}

			this.currentPhase = this.getPhase((EnderDragonPhase<DragonPhaseInstance>)enderDragonPhase);
			if (!this.dragon.level().isClientSide()) {
				this.dragon.getEntityData().set(EnderDragon.DATA_PHASE, enderDragonPhase.getId());
			}

			LOGGER.debug("Dragon is now in phase {} on the {}", enderDragonPhase, this.dragon.level().isClientSide() ? "client" : "server");
			this.currentPhase.begin();
		}
	}

	public DragonPhaseInstance getCurrentPhase() {
		return Objects.requireNonNull(this.currentPhase);
	}

	public <T extends DragonPhaseInstance> T getPhase(EnderDragonPhase<T> enderDragonPhase) {
		int i = enderDragonPhase.getId();
		DragonPhaseInstance dragonPhaseInstance = this.phases[i];
		if (dragonPhaseInstance == null) {
			dragonPhaseInstance = enderDragonPhase.createInstance(this.dragon);
			this.phases[i] = dragonPhaseInstance;
		}

		return (T)dragonPhaseInstance;
	}
}
