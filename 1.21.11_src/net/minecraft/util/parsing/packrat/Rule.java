package net.minecraft.util.parsing.packrat;

import org.jspecify.annotations.Nullable;

public interface Rule<S, T> {
	@Nullable T parse(ParseState<S> parseState);

	static <S, T> Rule<S, T> fromTerm(Term<S> term, Rule.RuleAction<S, T> ruleAction) {
		return new Rule.WrappedTerm<>(ruleAction, term);
	}

	static <S, T> Rule<S, T> fromTerm(Term<S> term, Rule.SimpleRuleAction<S, T> simpleRuleAction) {
		return new Rule.WrappedTerm<>(simpleRuleAction, term);
	}

	@FunctionalInterface
	interface RuleAction<S, T> {
		@Nullable T run(ParseState<S> parseState);
	}

	@FunctionalInterface
	interface SimpleRuleAction<S, T> extends Rule.RuleAction<S, T> {
		T run(Scope scope);

		@Override
		default T run(ParseState<S> parseState) {
			return this.run(parseState.scope());
		}
	}

	record WrappedTerm<S, T>(Rule.RuleAction<S, T> action, Term<S> child) implements Rule<S, T> {
		@Override
		public @Nullable T parse(ParseState<S> parseState) {
			Scope scope = parseState.scope();
			scope.pushFrame();

			try {
				return this.child.parse(parseState, scope, Control.UNBOUND) ? this.action.run(parseState) : null;
			} finally {
				scope.popFrame();
			}
		}
	}
}
