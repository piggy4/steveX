package net.minecraft.util.parsing.packrat;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public class Dictionary<S> {
	private final Map<Atom<?>, Dictionary.Entry<S, ?>> terms = new IdentityHashMap<>();

	public <T> NamedRule<S, T> put(Atom<T> atom, Rule<S, T> rule) {
		Dictionary.Entry<S, T> entry = (Dictionary.Entry<S, T>)this.terms.computeIfAbsent(atom, Dictionary.Entry::new);
		if (entry.value != null) {
			throw new IllegalArgumentException("Trying to override rule: " + atom);
		}

		entry.value = rule;
		return entry;
	}

	public <T> NamedRule<S, T> putComplex(Atom<T> atom, Term<S> term, Rule.RuleAction<S, T> ruleAction) {
		return this.put(atom, Rule.fromTerm(term, ruleAction));
	}

	public <T> NamedRule<S, T> put(Atom<T> atom, Term<S> term, Rule.SimpleRuleAction<S, T> simpleRuleAction) {
		return this.put(atom, Rule.fromTerm(term, simpleRuleAction));
	}

	public void checkAllBound() {
		List<? extends Atom<?>> list = this.terms.entrySet().stream().filter(entry -> entry.getValue().value == null).map(Map.Entry::getKey).toList();
		if (!list.isEmpty()) {
			throw new IllegalStateException("Unbound names: " + list);
		}
	}

	public <T> NamedRule<S, T> getOrThrow(Atom<T> atom) {
		return (NamedRule<S, T>)Objects.requireNonNull(this.terms.get(atom), () -> "No rule called " + atom);
	}

	public <T> NamedRule<S, T> forward(Atom<T> atom) {
		return this.getOrCreateEntry(atom);
	}

	private <T> Dictionary.Entry<S, T> getOrCreateEntry(Atom<T> atom) {
		return (Dictionary.Entry<S, T>)this.terms.computeIfAbsent(atom, Dictionary.Entry::new);
	}

	public <T> Term<S> named(Atom<T> atom) {
		return new Dictionary.Reference<>(this.getOrCreateEntry(atom), atom);
	}

	public <T> Term<S> namedWithAlias(Atom<T> atom, Atom<T> atom2) {
		return new Dictionary.Reference<>(this.getOrCreateEntry(atom), atom2);
	}

	static class Entry<S, T> implements NamedRule<S, T>, Supplier<String> {
		private final Atom<T> name;
		@Nullable Rule<S, T> value;

		private Entry(Atom<T> atom) {
			this.name = atom;
		}

		@Override
		public Atom<T> name() {
			return this.name;
		}

		@Override
		public Rule<S, T> value() {
			return Objects.requireNonNull(this.value, this);
		}

		public String get() {
			return "Unbound rule " + this.name;
		}
	}

	record Reference<S, T>(Dictionary.Entry<S, T> ruleToParse, Atom<T> nameToStore) implements Term<S> {
		@Override
		public boolean parse(ParseState<S> parseState, Scope scope, Control control) {
			T object = parseState.parse(this.ruleToParse);
			if (object == null) {
				return false;
			}

			scope.put(this.nameToStore, object);
			return true;
		}
	}
}
