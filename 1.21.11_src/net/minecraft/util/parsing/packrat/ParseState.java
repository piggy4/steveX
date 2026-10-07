package net.minecraft.util.parsing.packrat;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

public interface ParseState<S> {
	Scope scope();

	ErrorCollector<S> errorCollector();

	default <T> Optional<T> parseTopRule(NamedRule<S, T> namedRule) {
		T object = this.parse(namedRule);
		if (object != null) {
			this.errorCollector().finish(this.mark());
		}

		if (!this.scope().hasOnlySingleFrame()) {
			throw new IllegalStateException("Malformed scope: " + this.scope());
		} else {
			return Optional.ofNullable(object);
		}
	}

	<T> @Nullable T parse(NamedRule<S, T> namedRule);

	S input();

	int mark();

	void restore(int i);

	Control acquireControl();

	void releaseControl();

	ParseState<S> silent();
}
