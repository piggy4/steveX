package net.minecraft.util.parsing.packrat;

import java.util.ArrayList;
import java.util.List;

public interface Term<S> {
	boolean parse(ParseState<S> parseState, Scope scope, Control control);

	static <S, T> Term<S> marker(Atom<T> atom, T object) {
		return new Term.Marker<>(atom, object);
	}

	@SafeVarargs
	static <S> Term<S> sequence(Term<S>... terms) {
		return new Term.Sequence<>(terms);
	}

	@SafeVarargs
	static <S> Term<S> alternative(Term<S>... terms) {
		return new Term.Alternative<>(terms);
	}

	static <S> Term<S> optional(Term<S> term) {
		return new Term.Maybe<>(term);
	}

	static <S, T> Term<S> repeated(NamedRule<S, T> namedRule, Atom<List<T>> atom) {
		return repeated(namedRule, atom, 0);
	}

	static <S, T> Term<S> repeated(NamedRule<S, T> namedRule, Atom<List<T>> atom, int i) {
		return new Term.Repeated<>(namedRule, atom, i);
	}

	static <S, T> Term<S> repeatedWithTrailingSeparator(NamedRule<S, T> namedRule, Atom<List<T>> atom, Term<S> term) {
		return repeatedWithTrailingSeparator(namedRule, atom, term, 0);
	}

	static <S, T> Term<S> repeatedWithTrailingSeparator(NamedRule<S, T> namedRule, Atom<List<T>> atom, Term<S> term, int i) {
		return new Term.RepeatedWithSeparator<>(namedRule, atom, term, i, true);
	}

	static <S, T> Term<S> repeatedWithoutTrailingSeparator(NamedRule<S, T> namedRule, Atom<List<T>> atom, Term<S> term) {
		return repeatedWithoutTrailingSeparator(namedRule, atom, term, 0);
	}

	static <S, T> Term<S> repeatedWithoutTrailingSeparator(NamedRule<S, T> namedRule, Atom<List<T>> atom, Term<S> term, int i) {
		return new Term.RepeatedWithSeparator<>(namedRule, atom, term, i, false);
	}

	static <S> Term<S> positiveLookahead(Term<S> term) {
		return new Term.LookAhead<>(term, true);
	}

	static <S> Term<S> negativeLookahead(Term<S> term) {
		return new Term.LookAhead<>(term, false);
	}

	static <S> Term<S> cut() {
		return new Term<S>() {
			@Override
			public boolean parse(ParseState<S> parseState, Scope scope, Control control) {
				control.cut();
				return true;
			}

			@Override
			public String toString() {
				return "↑";
			}
		};
	}

	static <S> Term<S> empty() {
		return new Term<S>() {
			@Override
			public boolean parse(ParseState<S> parseState, Scope scope, Control control) {
				return true;
			}

			@Override
			public String toString() {
				return "ε";
			}
		};
	}

	static <S> Term<S> fail(Object object) {
		return new Term<S>() {
			@Override
			public boolean parse(ParseState<S> parseState, Scope scope, Control control) {
				parseState.errorCollector().store(parseState.mark(), object);
				return false;
			}

			@Override
			public String toString() {
				return "fail";
			}
		};
	}

	record Alternative<S>(Term<S>[] elements) implements Term<S> {
		@Override
		public boolean parse(ParseState<S> parseState, Scope scope, Control control) {
			Control control2 = parseState.acquireControl();

			try {
				int i = parseState.mark();
				scope.splitFrame();

				for (Term<S> term : this.elements) {
					if (term.parse(parseState, scope, control2)) {
						scope.mergeFrame();
						return true;
					}

					scope.clearFrameValues();
					parseState.restore(i);
					if (control2.hasCut()) {
						break;
					}
				}

				scope.popFrame();
				return false;
			} finally {
				parseState.releaseControl();
			}
		}
	}

	record LookAhead<S>(Term<S> term, boolean positive) implements Term<S> {
		@Override
		public boolean parse(ParseState<S> parseState, Scope scope, Control control) {
			int i = parseState.mark();
			boolean bl = this.term.parse(parseState.silent(), scope, control);
			parseState.restore(i);
			return this.positive == bl;
		}
	}

	record Marker<S, T>(Atom<T> name, T value) implements Term<S> {
		@Override
		public boolean parse(ParseState<S> parseState, Scope scope, Control control) {
			scope.put(this.name, this.value);
			return true;
		}
	}

	record Maybe<S>(Term<S> term) implements Term<S> {
		@Override
		public boolean parse(ParseState<S> parseState, Scope scope, Control control) {
			int i = parseState.mark();
			if (!this.term.parse(parseState, scope, control)) {
				parseState.restore(i);
			}

			return true;
		}
	}

	record Repeated<S, T>(NamedRule<S, T> element, Atom<List<T>> listName, int minRepetitions) implements Term<S> {
		@Override
		public boolean parse(ParseState<S> parseState, Scope scope, Control control) {
			int i = parseState.mark();
			List<T> list = new ArrayList<>(this.minRepetitions);

			while (true) {
				int j = parseState.mark();
				T object = parseState.parse(this.element);
				if (object == null) {
					parseState.restore(j);
					if (list.size() < this.minRepetitions) {
						parseState.restore(i);
						return false;
					} else {
						scope.put(this.listName, list);
						return true;
					}
				}

				list.add(object);
			}
		}
	}

	record RepeatedWithSeparator<S, T>(NamedRule<S, T> element, Atom<List<T>> listName, Term<S> separator, int minRepetitions, boolean allowTrailingSeparator)
		implements Term<S> {
		@Override
		public boolean parse(ParseState<S> parseState, Scope scope, Control control) {
			int i = parseState.mark();
			List<T> list = new ArrayList<>(this.minRepetitions);
			boolean bl = true;

			while (true) {
				int j = parseState.mark();
				if (!bl && !this.separator.parse(parseState, scope, control)) {
					parseState.restore(j);
					break;
				}

				int k = parseState.mark();
				T object = parseState.parse(this.element);
				if (object == null) {
					if (bl) {
						parseState.restore(k);
					} else {
						if (!this.allowTrailingSeparator) {
							parseState.restore(i);
							return false;
						}

						parseState.restore(k);
					}
					break;
				}

				list.add(object);
				bl = false;
			}

			if (list.size() < this.minRepetitions) {
				parseState.restore(i);
				return false;
			} else {
				scope.put(this.listName, list);
				return true;
			}
		}
	}

	record Sequence<S>(Term<S>[] elements) implements Term<S> {
		@Override
		public boolean parse(ParseState<S> parseState, Scope scope, Control control) {
			int i = parseState.mark();

			for (Term<S> term : this.elements) {
				if (!term.parse(parseState, scope, control)) {
					parseState.restore(i);
					return false;
				}
			}

			return true;
		}
	}
}
