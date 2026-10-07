package net.minecraft.tags;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;

public class TagBuilder {
	private final List<TagEntry> entries = new ArrayList<>();

	public static TagBuilder create() {
		return new TagBuilder();
	}

	public List<TagEntry> build() {
		return List.copyOf(this.entries);
	}

	public TagBuilder add(TagEntry tagEntry) {
		this.entries.add(tagEntry);
		return this;
	}

	public TagBuilder addElement(Identifier identifier) {
		return this.add(TagEntry.element(identifier));
	}

	public TagBuilder addOptionalElement(Identifier identifier) {
		return this.add(TagEntry.optionalElement(identifier));
	}

	public TagBuilder addTag(Identifier identifier) {
		return this.add(TagEntry.tag(identifier));
	}

	public TagBuilder addOptionalTag(Identifier identifier) {
		return this.add(TagEntry.optionalTag(identifier));
	}
}
