package net.minecraft.util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public interface ProblemReporter {
	ProblemReporter DISCARDING = new ProblemReporter() {
		@Override
		public ProblemReporter forChild(ProblemReporter.PathElement pathElement) {
			return this;
		}

		@Override
		public void report(ProblemReporter.Problem problem) {
		}
	};

	ProblemReporter forChild(ProblemReporter.PathElement pathElement);

	void report(ProblemReporter.Problem problem);

	class Collector implements ProblemReporter {
		public static final ProblemReporter.PathElement EMPTY_ROOT = () -> "";
		private final ProblemReporter.@Nullable Collector parent;
		private final ProblemReporter.PathElement element;
		private final Set<ProblemReporter.Collector.Entry> problems;

		public Collector() {
			this(EMPTY_ROOT);
		}

		public Collector(ProblemReporter.PathElement pathElement) {
			this.parent = null;
			this.problems = new LinkedHashSet<>();
			this.element = pathElement;
		}

		private Collector(ProblemReporter.Collector collector, ProblemReporter.PathElement pathElement) {
			this.problems = collector.problems;
			this.parent = collector;
			this.element = pathElement;
		}

		@Override
		public ProblemReporter forChild(ProblemReporter.PathElement pathElement) {
			return new ProblemReporter.Collector(this, pathElement);
		}

		@Override
		public void report(ProblemReporter.Problem problem) {
			this.problems.add(new ProblemReporter.Collector.Entry(this, problem));
		}

		public boolean isEmpty() {
			return this.problems.isEmpty();
		}

		public void forEach(BiConsumer<String, ProblemReporter.Problem> biConsumer) {
			List<ProblemReporter.PathElement> list = new ArrayList<>();
			StringBuilder stringBuilder = new StringBuilder();

			for (ProblemReporter.Collector.Entry entry : this.problems) {
				for (ProblemReporter.Collector collector = entry.source; collector != null; collector = collector.parent) {
					list.add(collector.element);
				}

				for (int i = list.size() - 1; i >= 0; i--) {
					stringBuilder.append(list.get(i).get());
				}

				biConsumer.accept(stringBuilder.toString(), entry.problem());
				stringBuilder.setLength(0);
				list.clear();
			}
		}

		public String getReport() {
			Multimap<String, ProblemReporter.Problem> multimap = HashMultimap.create();
			this.forEach(multimap::put);
			return multimap.asMap()
				.entrySet()
				.stream()
				.map(entry -> " at " + entry.getKey() + ": " + entry.getValue().stream().map(ProblemReporter.Problem::description).collect(Collectors.joining("; ")))
				.collect(Collectors.joining("\n"));
		}

		public String getTreeReport() {
			List<ProblemReporter.PathElement> list = new ArrayList<>();
			ProblemReporter.Collector.ProblemTreeNode problemTreeNode = new ProblemReporter.Collector.ProblemTreeNode(this.element);

			for (ProblemReporter.Collector.Entry entry : this.problems) {
				for (ProblemReporter.Collector collector = entry.source; collector != this; collector = collector.parent) {
					list.add(collector.element);
				}

				ProblemReporter.Collector.ProblemTreeNode problemTreeNode2 = problemTreeNode;

				for (int i = list.size() - 1; i >= 0; i--) {
					problemTreeNode2 = problemTreeNode2.child(list.get(i));
				}

				list.clear();
				problemTreeNode2.problems.add(entry.problem);
			}

			return String.join("\n", problemTreeNode.getLines());
		}

		record Entry(ProblemReporter.Collector source, ProblemReporter.Problem problem) {
		}

		record ProblemTreeNode(
			ProblemReporter.PathElement element,
			List<ProblemReporter.Problem> problems,
			Map<ProblemReporter.PathElement, ProblemReporter.Collector.ProblemTreeNode> children
		) {

			public ProblemTreeNode(ProblemReporter.PathElement pathElement) {
				this(pathElement, new ArrayList<>(), new LinkedHashMap<>());
			}

			public ProblemReporter.Collector.ProblemTreeNode child(ProblemReporter.PathElement pathElement) {
				return this.children.computeIfAbsent(pathElement, ProblemReporter.Collector.ProblemTreeNode::new);
			}

			public List<String> getLines() {
				int i = this.problems.size();
				int j = this.children.size();
				if (i == 0 && j == 0) {
					return List.of();
				}

				if (i == 0 && j == 1) {
					List<String> list = new ArrayList<>();
					this.children.forEach((pathElement, problemTreeNode) -> list.addAll(problemTreeNode.getLines()));
					list.set(0, this.element.get() + list.get(0));
					return list;
				}

				if (i == 1 && j == 0) {
					return List.of(this.element.get() + ": " + this.problems.getFirst().description());
				}

				List<String> list = new ArrayList<>();
				this.children.forEach((pathElement, problemTreeNode) -> list.addAll(problemTreeNode.getLines()));
				list.replaceAll(string -> "  " + string);

				for (ProblemReporter.Problem problem : this.problems) {
					list.add("  " + problem.description());
				}

				list.addFirst(this.element.get() + ":");
				return list;
			}
		}
	}

	record ElementReferencePathElement(ResourceKey<?> id) implements ProblemReporter.PathElement {
		@Override
		public String get() {
			return "->{" + this.id.identifier() + "@" + this.id.registry() + "}";
		}
	}

	record FieldPathElement(String name) implements ProblemReporter.PathElement {
		@Override
		public String get() {
			return "." + this.name;
		}
	}

	record IndexedFieldPathElement(String name, int index) implements ProblemReporter.PathElement {
		@Override
		public String get() {
			return "." + this.name + "[" + this.index + "]";
		}
	}

	record IndexedPathElement(int index) implements ProblemReporter.PathElement {
		@Override
		public String get() {
			return "[" + this.index + "]";
		}
	}

	@FunctionalInterface
	interface PathElement {
		String get();
	}

	interface Problem {
		String description();
	}

	record RootElementPathElement(ResourceKey<?> id) implements ProblemReporter.PathElement {
		@Override
		public String get() {
			return "{" + this.id.identifier() + "@" + this.id.registry() + "}";
		}
	}

	record RootFieldPathElement(String name) implements ProblemReporter.PathElement {
		@Override
		public String get() {
			return this.name;
		}
	}

	class ScopedCollector extends ProblemReporter.Collector implements AutoCloseable {
		private final Logger logger;

		public ScopedCollector(Logger logger) {
			this.logger = logger;
		}

		public ScopedCollector(ProblemReporter.PathElement pathElement, Logger logger) {
			super(pathElement);
			this.logger = logger;
		}

		@Override
		public void close() {
			if (!this.isEmpty()) {
				this.logger.warn("[{}] Serialization errors:\n{}", this.logger.getName(), this.getTreeReport());
			}
		}
	}
}
