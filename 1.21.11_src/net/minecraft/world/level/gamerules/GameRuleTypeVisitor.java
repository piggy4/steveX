package net.minecraft.world.level.gamerules;

public interface GameRuleTypeVisitor {
	default <T> void visit(GameRule<T> gameRule) {
	}

	default void visitBoolean(GameRule<Boolean> gameRule) {
	}

	default void visitInteger(GameRule<Integer> gameRule) {
	}
}
