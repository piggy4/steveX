package net.minecraft.gametest.framework;

import com.google.common.base.MoreObjects;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import org.apache.commons.lang3.exception.ExceptionUtils;

class ReportGameListener implements GameTestListener {
	private int attempts = 0;
	private int successes = 0;

	public ReportGameListener() {
	}

	@Override
	public void testStructureLoaded(GameTestInfo gameTestInfo) {
		this.attempts++;
	}

	private void handleRetry(GameTestInfo gameTestInfo, GameTestRunner gameTestRunner, boolean bl) {
		RetryOptions retryOptions = gameTestInfo.retryOptions();
		String string = String.format(Locale.ROOT, "[Run: %4d, Ok: %4d, Fail: %4d", this.attempts, this.successes, this.attempts - this.successes);
		if (!retryOptions.unlimitedTries()) {
			string = string + String.format(Locale.ROOT, ", Left: %4d", retryOptions.numberOfTries() - this.attempts);
		}

		string = string + "]";
		String string2 = gameTestInfo.id() + " " + (bl ? "passed" : "failed") + "! " + gameTestInfo.getRunTime() + "ms";
		String string3 = String.format(Locale.ROOT, "%-53s%s", string, string2);
		if (bl) {
			reportPassed(gameTestInfo, string3);
		} else {
			say(gameTestInfo.getLevel(), ChatFormatting.RED, string3);
		}

		if (retryOptions.hasTriesLeft(this.attempts, this.successes)) {
			gameTestRunner.rerunTest(gameTestInfo);
		}
	}

	@Override
	public void testPassed(GameTestInfo gameTestInfo, GameTestRunner gameTestRunner) {
		this.successes++;
		if (gameTestInfo.retryOptions().hasRetries()) {
			this.handleRetry(gameTestInfo, gameTestRunner, true);
		} else if (!gameTestInfo.isFlaky()) {
			reportPassed(gameTestInfo, gameTestInfo.id() + " passed! (" + gameTestInfo.getRunTime() + "ms / " + gameTestInfo.getTick() + "gameticks)");
		} else {
			if (this.successes >= gameTestInfo.requiredSuccesses()) {
				reportPassed(gameTestInfo, gameTestInfo + " passed " + this.successes + " times of " + this.attempts + " attempts.");
			} else {
				say(gameTestInfo.getLevel(), ChatFormatting.GREEN, "Flaky test " + gameTestInfo + " succeeded, attempt: " + this.attempts + " successes: " + this.successes);
				gameTestRunner.rerunTest(gameTestInfo);
			}
		}
	}

	@Override
	public void testFailed(GameTestInfo gameTestInfo, GameTestRunner gameTestRunner) {
		if (!gameTestInfo.isFlaky()) {
			reportFailure(gameTestInfo, gameTestInfo.getError());
			if (gameTestInfo.retryOptions().hasRetries()) {
				this.handleRetry(gameTestInfo, gameTestRunner, false);
			}
		} else {
			GameTestInstance gameTestInstance = gameTestInfo.getTest();
			String string = "Flaky test " + gameTestInfo + " failed, attempt: " + this.attempts + "/" + gameTestInstance.maxAttempts();
			if (gameTestInstance.requiredSuccesses() > 1) {
				string = string + ", successes: " + this.successes + " (" + gameTestInstance.requiredSuccesses() + " required)";
			}

			say(gameTestInfo.getLevel(), ChatFormatting.YELLOW, string);
			if (gameTestInfo.maxAttempts() - this.attempts + this.successes >= gameTestInfo.requiredSuccesses()) {
				gameTestRunner.rerunTest(gameTestInfo);
			} else {
				reportFailure(gameTestInfo, new ExhaustedAttemptsException(this.attempts, this.successes, gameTestInfo));
			}
		}
	}

	@Override
	public void testAddedForRerun(GameTestInfo gameTestInfo, GameTestInfo gameTestInfo2, GameTestRunner gameTestRunner) {
		gameTestInfo2.addListener(this);
	}

	public static void reportPassed(GameTestInfo gameTestInfo, String string) {
		getTestInstanceBlockEntity(gameTestInfo).ifPresent(testInstanceBlockEntity -> testInstanceBlockEntity.setSuccess());
		visualizePassedTest(gameTestInfo, string);
	}

	private static void visualizePassedTest(GameTestInfo gameTestInfo, String string) {
		say(gameTestInfo.getLevel(), ChatFormatting.GREEN, string);
		GlobalTestReporter.onTestSuccess(gameTestInfo);
	}

	protected static void reportFailure(GameTestInfo gameTestInfo, Throwable throwable) {
		Component component;
		if (throwable instanceof GameTestAssertException gameTestAssertException) {
			component = gameTestAssertException.getDescription();
		} else {
			component = Component.literal(Util.describeError(throwable));
		}

		getTestInstanceBlockEntity(gameTestInfo).ifPresent(testInstanceBlockEntity -> testInstanceBlockEntity.setErrorMessage(component));
		visualizeFailedTest(gameTestInfo, throwable);
	}

	protected static void visualizeFailedTest(GameTestInfo gameTestInfo, Throwable throwable) {
		String string = throwable.getMessage() + (throwable.getCause() == null ? "" : " cause: " + Util.describeError(throwable.getCause()));
		String string2 = (gameTestInfo.isRequired() ? "" : "(optional) ") + gameTestInfo.id() + " failed! " + string;
		say(gameTestInfo.getLevel(), gameTestInfo.isRequired() ? ChatFormatting.RED : ChatFormatting.YELLOW, string2);
		Throwable throwable2 = MoreObjects.firstNonNull(ExceptionUtils.getRootCause(throwable), throwable);
		if (throwable2 instanceof GameTestAssertPosException gameTestAssertPosException) {
			gameTestInfo.getTestInstanceBlockEntity().markError(gameTestAssertPosException.getAbsolutePos(), gameTestAssertPosException.getMessageToShowAtBlock());
		}

		GlobalTestReporter.onTestFailed(gameTestInfo);
	}

	private static Optional<TestInstanceBlockEntity> getTestInstanceBlockEntity(GameTestInfo gameTestInfo) {
		ServerLevel serverLevel = gameTestInfo.getLevel();
		Optional<BlockPos> optional = Optional.ofNullable(gameTestInfo.getTestBlockPos());
		return optional.flatMap(blockPos -> serverLevel.getBlockEntity(blockPos, BlockEntityType.TEST_INSTANCE_BLOCK));
	}

	protected static void say(ServerLevel serverLevel, ChatFormatting chatFormatting, String string) {
		serverLevel.getPlayers(serverPlayer -> true).forEach(serverPlayer -> serverPlayer.sendSystemMessage(Component.literal(string).withStyle(chatFormatting)));
	}
}
