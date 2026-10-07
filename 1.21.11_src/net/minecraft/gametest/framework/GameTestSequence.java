package net.minecraft.gametest.framework;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

public class GameTestSequence {
	final GameTestInfo parent;
	private final List<GameTestEvent> events = Lists.newArrayList();
	private int lastTick;

	GameTestSequence(GameTestInfo gameTestInfo) {
		this.parent = gameTestInfo;
		this.lastTick = gameTestInfo.getTick();
	}

	public GameTestSequence thenWaitUntil(Runnable runnable) {
		this.events.add(GameTestEvent.create(runnable));
		return this;
	}

	public GameTestSequence thenWaitUntil(long l, Runnable runnable) {
		this.events.add(GameTestEvent.create(l, runnable));
		return this;
	}

	public GameTestSequence thenIdle(int i) {
		return this.thenExecuteAfter(i, () -> {});
	}

	public GameTestSequence thenExecute(Runnable runnable) {
		this.events.add(GameTestEvent.create(() -> this.executeWithoutFail(runnable)));
		return this;
	}

	public GameTestSequence thenExecuteAfter(int i, Runnable runnable) {
		this.events.add(GameTestEvent.create(() -> {
			if (this.parent.getTick() < this.lastTick + i) {
				throw new GameTestAssertException(Component.translatable("test.error.sequence.not_completed"), this.parent.getTick());
			}

			this.executeWithoutFail(runnable);
		}));
		return this;
	}

	public GameTestSequence thenExecuteFor(int i, Runnable runnable) {
		this.events.add(GameTestEvent.create(() -> {
			if (this.parent.getTick() < this.lastTick + i) {
				this.executeWithoutFail(runnable);
				throw new GameTestAssertException(Component.translatable("test.error.sequence.not_completed"), this.parent.getTick());
			}
		}));
		return this;
	}

	public void thenSucceed() {
		this.events.add(GameTestEvent.create(this.parent::succeed));
	}

	public void thenFail(Supplier<GameTestException> supplier) {
		this.events.add(GameTestEvent.create(() -> this.parent.fail(supplier.get())));
	}

	public GameTestSequence.Condition thenTrigger() {
		GameTestSequence.Condition condition = new GameTestSequence.Condition();
		this.events.add(GameTestEvent.create(() -> condition.trigger(this.parent.getTick())));
		return condition;
	}

	public void tickAndContinue(int i) {
		try {
			this.tick(i);
		} catch (GameTestAssertException var3) {
		}
	}

	public void tickAndFailIfNotComplete(int i) {
		try {
			this.tick(i);
		} catch (GameTestAssertException gameTestAssertException) {
			this.parent.fail(gameTestAssertException);
		}
	}

	private void executeWithoutFail(Runnable runnable) {
		try {
			runnable.run();
		} catch (GameTestAssertException gameTestAssertException) {
			this.parent.fail(gameTestAssertException);
		}
	}

	private void tick(int i) {
		Iterator<GameTestEvent> iterator = this.events.iterator();

		while (iterator.hasNext()) {
			GameTestEvent gameTestEvent = iterator.next();
			gameTestEvent.assertion.run();
			iterator.remove();
			int j = i - this.lastTick;
			int k = this.lastTick;
			this.lastTick = i;
			if (gameTestEvent.expectedDelay != null && gameTestEvent.expectedDelay != j) {
				this.parent.fail(new GameTestAssertException(Component.translatable("test.error.sequence.invalid_tick", k + gameTestEvent.expectedDelay), i));
				break;
			}
		}
	}

	public class Condition {
		private static final int NOT_TRIGGERED = -1;
		private int triggerTime = -1;

		void trigger(int i) {
			if (this.triggerTime != -1) {
				throw new IllegalStateException("Condition already triggered at " + this.triggerTime);
			}

			this.triggerTime = i;
		}

		public void assertTriggeredThisTick() {
			int i = GameTestSequence.this.parent.getTick();
			if (this.triggerTime != i) {
				if (this.triggerTime == -1) {
					throw new GameTestAssertException(Component.translatable("test.error.sequence.condition_not_triggered"), i);
				} else {
					throw new GameTestAssertException(Component.translatable("test.error.sequence.condition_already_triggered", this.triggerTime), i);
				}
			}
		}
	}
}
