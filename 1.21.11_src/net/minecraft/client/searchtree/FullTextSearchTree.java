package net.minecraft.client.searchtree;

import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public class FullTextSearchTree<T> extends IdSearchTree<T> {
	private final SearchTree<T> plainTextSearchTree;

	public FullTextSearchTree(Function<T, Stream<String>> function, Function<T, Stream<Identifier>> function2, List<T> list) {
		super(function2, list);
		this.plainTextSearchTree = SearchTree.plainText(list, function);
	}

	@Override
	protected List<T> searchPlainText(String string) {
		return this.plainTextSearchTree.search(string);
	}

	@Override
	protected List<T> searchIdentifier(String string, String string2) {
		List<T> list = this.identifierSearchTree.searchNamespace(string);
		List<T> list2 = this.identifierSearchTree.searchPath(string2);
		List<T> list3 = this.plainTextSearchTree.search(string2);
		Iterator<T> iterator = new MergingUniqueIterator<>(list2.iterator(), list3.iterator(), this.additionOrder);
		return ImmutableList.copyOf(new IntersectionIterator<>(list.iterator(), iterator, this.additionOrder));
	}
}
