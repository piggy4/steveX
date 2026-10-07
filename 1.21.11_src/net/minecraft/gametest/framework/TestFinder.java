package net.minecraft.gametest.framework;

import com.mojang.brigadier.context.CommandContext;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;

public class TestFinder implements TestInstanceFinder, TestPosFinder {
	static final TestInstanceFinder NO_FUNCTIONS = Stream::empty;
	static final TestPosFinder NO_STRUCTURES = Stream::empty;
	private final TestInstanceFinder testInstanceFinder;
	private final TestPosFinder testPosFinder;
	private final CommandSourceStack source;

	@Override
	public Stream<BlockPos> findTestPos() {
		return this.testPosFinder.findTestPos();
	}

	public static TestFinder.Builder builder() {
		return new TestFinder.Builder();
	}

	TestFinder(CommandSourceStack commandSourceStack, TestInstanceFinder testInstanceFinder, TestPosFinder testPosFinder) {
		this.source = commandSourceStack;
		this.testInstanceFinder = testInstanceFinder;
		this.testPosFinder = testPosFinder;
	}

	public CommandSourceStack source() {
		return this.source;
	}

	@Override
	public Stream<Holder.Reference<GameTestInstance>> findTests() {
		return this.testInstanceFinder.findTests();
	}

	public static class Builder {
		private final UnaryOperator<Supplier<Stream<Holder.Reference<GameTestInstance>>>> testFinderWrapper;
		private final UnaryOperator<Supplier<Stream<BlockPos>>> structureBlockPosFinderWrapper;

		public Builder() {
			this.testFinderWrapper = supplier -> supplier;
			this.structureBlockPosFinderWrapper = supplier -> supplier;
		}

		private Builder(UnaryOperator<Supplier<Stream<Holder.Reference<GameTestInstance>>>> unaryOperator, UnaryOperator<Supplier<Stream<BlockPos>>> unaryOperator2) {
			this.testFinderWrapper = unaryOperator;
			this.structureBlockPosFinderWrapper = unaryOperator2;
		}

		public TestFinder.Builder createMultipleCopies(int i) {
			return new TestFinder.Builder(createCopies(i), createCopies(i));
		}

		private static <Q> UnaryOperator<Supplier<Stream<Q>>> createCopies(int i) {
			return supplier -> {
				List<Q> list = new LinkedList<>();
				List<Q> list2 = ((Stream)supplier.get()).toList();

				for (int j = 0; j < i; j++) {
					list.addAll(list2);
				}

				return list::stream;
			};
		}

		private TestFinder build(CommandSourceStack commandSourceStack, TestInstanceFinder testInstanceFinder, TestPosFinder testPosFinder) {
			return new TestFinder(
				commandSourceStack,
				this.testFinderWrapper.apply(testInstanceFinder::findTests)::get,
				this.structureBlockPosFinderWrapper.apply(testPosFinder::findTestPos)::get
			);
		}

		public TestFinder radius(CommandContext<CommandSourceStack> commandContext, int i) {
			CommandSourceStack commandSourceStack = commandContext.getSource();
			BlockPos blockPos = BlockPos.containing(commandSourceStack.getPosition());
			return this.build(commandSourceStack, TestFinder.NO_FUNCTIONS, () -> StructureUtils.findTestBlocks(blockPos, i, commandSourceStack.getLevel()));
		}

		public TestFinder nearest(CommandContext<CommandSourceStack> commandContext) {
			CommandSourceStack commandSourceStack = commandContext.getSource();
			BlockPos blockPos = BlockPos.containing(commandSourceStack.getPosition());
			return this.build(commandSourceStack, TestFinder.NO_FUNCTIONS, () -> StructureUtils.findNearestTest(blockPos, 15, commandSourceStack.getLevel()).stream());
		}

		public TestFinder allNearby(CommandContext<CommandSourceStack> commandContext) {
			CommandSourceStack commandSourceStack = commandContext.getSource();
			BlockPos blockPos = BlockPos.containing(commandSourceStack.getPosition());
			return this.build(commandSourceStack, TestFinder.NO_FUNCTIONS, () -> StructureUtils.findTestBlocks(blockPos, 250, commandSourceStack.getLevel()));
		}

		public TestFinder lookedAt(CommandContext<CommandSourceStack> commandContext) {
			CommandSourceStack commandSourceStack = commandContext.getSource();
			return this.build(
				commandSourceStack,
				TestFinder.NO_FUNCTIONS,
				() -> StructureUtils.lookedAtTestPos(
					BlockPos.containing(commandSourceStack.getPosition()), commandSourceStack.getPlayer().getCamera(), commandSourceStack.getLevel()
				)
			);
		}

		public TestFinder failedTests(CommandContext<CommandSourceStack> commandContext, boolean bl) {
			return this.build(
				commandContext.getSource(), () -> FailedTestTracker.getLastFailedTests().filter(reference -> !bl || reference.value().required()), TestFinder.NO_STRUCTURES
			);
		}

		public TestFinder byResourceSelection(CommandContext<CommandSourceStack> commandContext, Collection<Holder.Reference<GameTestInstance>> collection) {
			return this.build(commandContext.getSource(), collection::stream, TestFinder.NO_STRUCTURES);
		}

		public TestFinder failedTests(CommandContext<CommandSourceStack> commandContext) {
			return this.failedTests(commandContext, false);
		}
	}
}
