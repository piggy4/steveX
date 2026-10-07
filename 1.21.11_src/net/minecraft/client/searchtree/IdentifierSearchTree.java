package net.minecraft.client.searchtree;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public interface IdentifierSearchTree<T> {
	static <T> IdentifierSearchTree<T> empty() {
		return new IdentifierSearchTree<T>() {
			@Override
			public List<T> searchNamespace(String string) {
				return List.of();
			}

			@Override
			public List<T> searchPath(String string) {
				return List.of();
			}
		};
	}

	static <T> IdentifierSearchTree<T> create(List<T> list, Function<T, Stream<Identifier>> function) {
		if (list.isEmpty()) {
			return empty();
		}

		final SuffixArray<T> suffixArray = new SuffixArray<>();
		final SuffixArray<T> suffixArray2 = new SuffixArray<>();

		for (T object : list) {
			function.apply(object).forEach(identifier -> {
				suffixArray.add(object, identifier.getNamespace().toLowerCase(Locale.ROOT));
				suffixArray2.add(object, identifier.getPath().toLowerCase(Locale.ROOT));
			});
		}

		suffixArray.generate();
		suffixArray2.generate();
		return new IdentifierSearchTree<T>() {
			@Override
			public List<T> searchNamespace(String string) {
				return suffixArray.search(string);
			}

			@Override
			public List<T> searchPath(String string) {
				return suffixArray2.search(string);
			}
		};
	}

	List<T> searchNamespace(String string);

	List<T> searchPath(String string);
}
