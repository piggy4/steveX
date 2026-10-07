package net.minecraft.util.context;

import net.minecraft.resources.Identifier;

public class ContextKey<T> {
	private final Identifier name;

	public ContextKey(Identifier identifier) {
		this.name = identifier;
	}

	public static <T> ContextKey<T> vanilla(String string) {
		return new ContextKey<>(Identifier.withDefaultNamespace(string));
	}

	public Identifier name() {
		return this.name;
	}

	@Override
	public String toString() {
		return "<parameter " + this.name + ">";
	}
}
