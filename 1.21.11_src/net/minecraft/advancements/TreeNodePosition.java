package net.minecraft.advancements;

import com.google.common.collect.Lists;
import java.util.List;
import org.jspecify.annotations.Nullable;

public class TreeNodePosition {
	private final AdvancementNode node;
	private final @Nullable TreeNodePosition parent;
	private final @Nullable TreeNodePosition previousSibling;
	private final int childIndex;
	private final List<TreeNodePosition> children = Lists.newArrayList();
	private TreeNodePosition ancestor;
	private @Nullable TreeNodePosition thread;
	private int x;
	private float y;
	private float mod;
	private float change;
	private float shift;

	public TreeNodePosition(
		AdvancementNode advancementNode, @Nullable TreeNodePosition treeNodePosition, @Nullable TreeNodePosition treeNodePosition2, int i, int j
	) {
		if (advancementNode.advancement().display().isEmpty()) {
			throw new IllegalArgumentException("Can't position an invisible advancement!");
		}

		this.node = advancementNode;
		this.parent = treeNodePosition;
		this.previousSibling = treeNodePosition2;
		this.childIndex = i;
		this.ancestor = this;
		this.x = j;
		this.y = -1.0F;
		TreeNodePosition treeNodePosition3 = null;

		for (AdvancementNode advancementNode2 : advancementNode.children()) {
			treeNodePosition3 = this.addChild(advancementNode2, treeNodePosition3);
		}
	}

	private @Nullable TreeNodePosition addChild(AdvancementNode advancementNode, @Nullable TreeNodePosition treeNodePosition) {
		if (advancementNode.advancement().display().isPresent()) {
			treeNodePosition = new TreeNodePosition(advancementNode, this, treeNodePosition, this.children.size() + 1, this.x + 1);
			this.children.add(treeNodePosition);
		} else {
			for (AdvancementNode advancementNode2 : advancementNode.children()) {
				treeNodePosition = this.addChild(advancementNode2, treeNodePosition);
			}
		}

		return treeNodePosition;
	}

	private void firstWalk() {
		if (this.children.isEmpty()) {
			if (this.previousSibling != null) {
				this.y = this.previousSibling.y + 1.0F;
			} else {
				this.y = 0.0F;
			}
		} else {
			TreeNodePosition treeNodePosition = null;

			for (TreeNodePosition treeNodePosition2 : this.children) {
				treeNodePosition2.firstWalk();
				treeNodePosition = treeNodePosition2.apportion(treeNodePosition == null ? treeNodePosition2 : treeNodePosition);
			}

			this.executeShifts();
			float f = (this.children.get(0).y + this.children.get(this.children.size() - 1).y) / 2.0F;
			if (this.previousSibling != null) {
				this.y = this.previousSibling.y + 1.0F;
				this.mod = this.y - f;
			} else {
				this.y = f;
			}
		}
	}

	private float secondWalk(float f, int i, float g) {
		this.y += f;
		this.x = i;
		if (this.y < g) {
			g = this.y;
		}

		for (TreeNodePosition treeNodePosition : this.children) {
			g = treeNodePosition.secondWalk(f + this.mod, i + 1, g);
		}

		return g;
	}

	private void thirdWalk(float f) {
		this.y += f;

		for (TreeNodePosition treeNodePosition : this.children) {
			treeNodePosition.thirdWalk(f);
		}
	}

	private void executeShifts() {
		float f = 0.0F;
		float g = 0.0F;

		for (int i = this.children.size() - 1; i >= 0; i--) {
			TreeNodePosition treeNodePosition = this.children.get(i);
			treeNodePosition.y += f;
			treeNodePosition.mod += f;
			g += treeNodePosition.change;
			f += treeNodePosition.shift + g;
		}
	}

	private @Nullable TreeNodePosition previousOrThread() {
		if (this.thread != null) {
			return this.thread;
		} else {
			return !this.children.isEmpty() ? this.children.get(0) : null;
		}
	}

	private @Nullable TreeNodePosition nextOrThread() {
		if (this.thread != null) {
			return this.thread;
		} else {
			return !this.children.isEmpty() ? this.children.get(this.children.size() - 1) : null;
		}
	}

	private TreeNodePosition apportion(TreeNodePosition treeNodePosition) {
		if (this.previousSibling == null) {
			return treeNodePosition;
		}

		TreeNodePosition treeNodePosition2 = this;
		TreeNodePosition treeNodePosition3 = this;
		TreeNodePosition treeNodePosition4 = this.previousSibling;
		TreeNodePosition treeNodePosition5 = this.parent.children.get(0);
		float f = this.mod;
		float g = this.mod;
		float h = treeNodePosition4.mod;
		float i = treeNodePosition5.mod;

		while (treeNodePosition4.nextOrThread() != null && treeNodePosition2.previousOrThread() != null) {
			treeNodePosition4 = treeNodePosition4.nextOrThread();
			treeNodePosition2 = treeNodePosition2.previousOrThread();
			treeNodePosition5 = treeNodePosition5.previousOrThread();
			treeNodePosition3 = treeNodePosition3.nextOrThread();
			treeNodePosition3.ancestor = this;
			float j = treeNodePosition4.y + h - (treeNodePosition2.y + f) + 1.0F;
			if (j > 0.0F) {
				treeNodePosition4.getAncestor(this, treeNodePosition).moveSubtree(this, j);
				f += j;
				g += j;
			}

			h += treeNodePosition4.mod;
			f += treeNodePosition2.mod;
			i += treeNodePosition5.mod;
			g += treeNodePosition3.mod;
		}

		if (treeNodePosition4.nextOrThread() != null && treeNodePosition3.nextOrThread() == null) {
			treeNodePosition3.thread = treeNodePosition4.nextOrThread();
			treeNodePosition3.mod += h - g;
		} else {
			if (treeNodePosition2.previousOrThread() != null && treeNodePosition5.previousOrThread() == null) {
				treeNodePosition5.thread = treeNodePosition2.previousOrThread();
				treeNodePosition5.mod += f - i;
			}

			treeNodePosition = this;
		}

		return treeNodePosition;
	}

	private void moveSubtree(TreeNodePosition treeNodePosition, float f) {
		float g = treeNodePosition.childIndex - this.childIndex;
		if (g != 0.0F) {
			treeNodePosition.change -= f / g;
			this.change += f / g;
		}

		treeNodePosition.shift += f;
		treeNodePosition.y += f;
		treeNodePosition.mod += f;
	}

	private TreeNodePosition getAncestor(TreeNodePosition treeNodePosition, TreeNodePosition treeNodePosition2) {
		return this.ancestor != null && treeNodePosition.parent.children.contains(this.ancestor) ? this.ancestor : treeNodePosition2;
	}

	private void finalizePosition() {
		this.node.advancement().display().ifPresent(displayInfo -> displayInfo.setLocation(this.x, this.y));
		if (!this.children.isEmpty()) {
			for (TreeNodePosition treeNodePosition : this.children) {
				treeNodePosition.finalizePosition();
			}
		}
	}

	public static void run(AdvancementNode advancementNode) {
		if (advancementNode.advancement().display().isEmpty()) {
			throw new IllegalArgumentException("Can't position children of an invisible root!");
		}

		TreeNodePosition treeNodePosition = new TreeNodePosition(advancementNode, null, null, 1, 0);
		treeNodePosition.firstWalk();
		float f = treeNodePosition.secondWalk(0.0F, 0, treeNodePosition.y);
		if (f < 0.0F) {
			treeNodePosition.thirdWalk(-f);
		}

		treeNodePosition.finalizePosition();
	}
}
