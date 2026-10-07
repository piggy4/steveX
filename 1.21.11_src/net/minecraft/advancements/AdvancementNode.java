package net.minecraft.advancements;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public class AdvancementNode {
	private final AdvancementHolder holder;
	private final @Nullable AdvancementNode parent;
	private final Set<AdvancementNode> children = new ReferenceOpenHashSet<>();

	@VisibleForTesting
	public AdvancementNode(AdvancementHolder advancementHolder, @Nullable AdvancementNode advancementNode) {
		this.holder = advancementHolder;
		this.parent = advancementNode;
	}

	public Advancement advancement() {
		return this.holder.value();
	}

	public AdvancementHolder holder() {
		return this.holder;
	}

	public @Nullable AdvancementNode parent() {
		return this.parent;
	}

	public AdvancementNode root() {
		return getRoot(this);
	}

	public static AdvancementNode getRoot(AdvancementNode advancementNode) {
		AdvancementNode advancementNode2 = advancementNode;

		while (true) {
			AdvancementNode advancementNode3 = advancementNode2.parent();
			if (advancementNode3 == null) {
				return advancementNode2;
			}

			advancementNode2 = advancementNode3;
		}
	}

	public Iterable<AdvancementNode> children() {
		return this.children;
	}

	@VisibleForTesting
	public void addChild(AdvancementNode advancementNode) {
		this.children.add(advancementNode);
	}

	@Override
	public boolean equals(Object object) {
		return this == object ? true : object instanceof AdvancementNode advancementNode && this.holder.equals(advancementNode.holder);
	}

	@Override
	public int hashCode() {
		return this.holder.hashCode();
	}

	@Override
	public String toString() {
		return this.holder.id().toString();
	}
}
