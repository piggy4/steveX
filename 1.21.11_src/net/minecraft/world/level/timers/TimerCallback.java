package net.minecraft.world.level.timers;

import com.mojang.serialization.MapCodec;

public interface TimerCallback<T> {
	void handle(T object, TimerQueue<T> timerQueue, long l);

	MapCodec<? extends TimerCallback<T>> codec();
}
