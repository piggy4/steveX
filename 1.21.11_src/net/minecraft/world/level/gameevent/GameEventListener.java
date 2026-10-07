package net.minecraft.world.level.gameevent;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public interface GameEventListener {
	PositionSource getListenerSource();

	int getListenerRadius();

	boolean handleGameEvent(ServerLevel serverLevel, Holder<GameEvent> holder, GameEvent.Context context, Vec3 vec3);

	default GameEventListener.DeliveryMode getDeliveryMode() {
		return GameEventListener.DeliveryMode.UNSPECIFIED;
	}

	enum DeliveryMode {
		UNSPECIFIED,
		BY_DISTANCE;
	}

	interface Provider<T extends GameEventListener> {
		T getListener();
	}
}
