package net.minecraft.gametest.framework;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public class GameTestAssertPosException extends GameTestAssertException {
	private final BlockPos absolutePos;
	private final BlockPos relativePos;

	public GameTestAssertPosException(Component component, BlockPos blockPos, BlockPos blockPos2, int i) {
		super(component, i);
		this.absolutePos = blockPos;
		this.relativePos = blockPos2;
	}

	@Override
	public Component getDescription() {
		return Component.translatable(
			"test.error.position",
			this.message,
			this.absolutePos.getX(),
			this.absolutePos.getY(),
			this.absolutePos.getZ(),
			this.relativePos.getX(),
			this.relativePos.getY(),
			this.relativePos.getZ(),
			this.tick
		);
	}

	public Component getMessageToShowAtBlock() {
		return this.message;
	}

	public @Nullable BlockPos getRelativePos() {
		return this.relativePos;
	}

	public @Nullable BlockPos getAbsolutePos() {
		return this.absolutePos;
	}
}
