package net.minecraft.advancements;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class AdvancementTree {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final Map<Identifier, AdvancementNode> nodes = new Object2ObjectOpenHashMap<>();
	private final Set<AdvancementNode> roots = new ObjectLinkedOpenHashSet<>();
	private final Set<AdvancementNode> tasks = new ObjectLinkedOpenHashSet<>();
	private AdvancementTree.@Nullable Listener listener;

	private void remove(AdvancementNode advancementNode) {
		for (AdvancementNode advancementNode2 : advancementNode.children()) {
			this.remove(advancementNode2);
		}

		LOGGER.info("Forgot about advancement {}", advancementNode.holder());
		this.nodes.remove(advancementNode.holder().id());
		if (advancementNode.parent() == null) {
			this.roots.remove(advancementNode);
			if (this.listener != null) {
				this.listener.onRemoveAdvancementRoot(advancementNode);
			}
		} else {
			this.tasks.remove(advancementNode);
			if (this.listener != null) {
				this.listener.onRemoveAdvancementTask(advancementNode);
			}
		}
	}

	public void remove(Set<Identifier> set) {
		for (Identifier identifier : set) {
			AdvancementNode advancementNode = this.nodes.get(identifier);
			if (advancementNode == null) {
				LOGGER.warn("Told to remove advancement {} but I don't know what that is", identifier);
			} else {
				this.remove(advancementNode);
			}
		}
	}

	public void addAll(Collection<AdvancementHolder> collection) {
		List<AdvancementHolder> list = new ArrayList<>(collection);

		while (!list.isEmpty()) {
			if (!list.removeIf(this::tryInsert)) {
				LOGGER.error("Couldn't load advancements: {}", list);
				break;
			}
		}

		LOGGER.info("Loaded {} advancements", this.nodes.size());
	}

	private boolean tryInsert(AdvancementHolder advancementHolder) {
		Optional<Identifier> optional = advancementHolder.value().parent();
		AdvancementNode advancementNode = optional.map(this.nodes::get).orElse(null);
		if (advancementNode == null && optional.isPresent()) {
			return false;
		}

		AdvancementNode advancementNode2 = new AdvancementNode(advancementHolder, advancementNode);
		if (advancementNode != null) {
			advancementNode.addChild(advancementNode2);
		}

		this.nodes.put(advancementHolder.id(), advancementNode2);
		if (advancementNode == null) {
			this.roots.add(advancementNode2);
			if (this.listener != null) {
				this.listener.onAddAdvancementRoot(advancementNode2);
			}
		} else {
			this.tasks.add(advancementNode2);
			if (this.listener != null) {
				this.listener.onAddAdvancementTask(advancementNode2);
			}
		}

		return true;
	}

	public void clear() {
		this.nodes.clear();
		this.roots.clear();
		this.tasks.clear();
		if (this.listener != null) {
			this.listener.onAdvancementsCleared();
		}
	}

	public Iterable<AdvancementNode> roots() {
		return this.roots;
	}

	public Collection<AdvancementNode> nodes() {
		return this.nodes.values();
	}

	public @Nullable AdvancementNode get(Identifier identifier) {
		return this.nodes.get(identifier);
	}

	public @Nullable AdvancementNode get(AdvancementHolder advancementHolder) {
		return this.nodes.get(advancementHolder.id());
	}

	public void setListener(AdvancementTree.@Nullable Listener listener) {
		this.listener = listener;
		if (listener != null) {
			for (AdvancementNode advancementNode : this.roots) {
				listener.onAddAdvancementRoot(advancementNode);
			}

			for (AdvancementNode advancementNode : this.tasks) {
				listener.onAddAdvancementTask(advancementNode);
			}
		}
	}

	public interface Listener {
		void onAddAdvancementRoot(AdvancementNode advancementNode);

		void onRemoveAdvancementRoot(AdvancementNode advancementNode);

		void onAddAdvancementTask(AdvancementNode advancementNode);

		void onRemoveAdvancementTask(AdvancementNode advancementNode);

		void onAdvancementsCleared();
	}
}
