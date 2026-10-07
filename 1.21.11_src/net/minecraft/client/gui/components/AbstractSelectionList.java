package net.minecraft.client.gui.components;

import com.google.common.collect.Lists;
import java.util.AbstractList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class AbstractSelectionList<E extends AbstractSelectionList.Entry<E>> extends AbstractContainerWidget {
	private static final Identifier MENU_LIST_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/menu_list_background.png");
	private static final Identifier INWORLD_MENU_LIST_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/inworld_menu_list_background.png");
	private static final int SEPARATOR_HEIGHT = 2;
	protected final Minecraft minecraft;
	protected final int defaultEntryHeight;
	private final List<E> children = new AbstractSelectionList.TrackedList();
	protected boolean centerListVertically = true;
	private @Nullable E selected;
	private @Nullable E hovered;

	public AbstractSelectionList(Minecraft minecraft, int i, int j, int k, int l) {
		super(0, k, i, j, CommonComponents.EMPTY);
		this.minecraft = minecraft;
		this.defaultEntryHeight = l;
	}

	public @Nullable E getSelected() {
		return this.selected;
	}

	public void setSelected(@Nullable E entry) {
		this.selected = entry;
		if (entry != null) {
			boolean bl = entry.getContentY() < this.getY();
			boolean bl2 = entry.getContentBottom() > this.getBottom();
			if (this.minecraft.getLastInputType().isKeyboard() || bl || bl2) {
				this.scrollToEntry(entry);
			}
		}
	}

	public @Nullable E getFocused() {
		return (E)super.getFocused();
	}

	@Override
	public final List<E> children() {
		return Collections.unmodifiableList(this.children);
	}

	protected void sort(Comparator<E> comparator) {
		this.children.sort(comparator);
		this.repositionEntries();
	}

	protected void swap(int i, int j) {
		Collections.swap(this.children, i, j);
		this.repositionEntries();
		this.scrollToEntry(this.children.get(j));
	}

	protected void clearEntries() {
		this.children.clear();
		this.selected = null;
	}

	protected void clearEntriesExcept(E entry) {
		this.children.removeIf(entry2 -> entry2 != entry);
		if (this.selected != entry) {
			this.setSelected(null);
		}
	}

	public void replaceEntries(Collection<E> collection) {
		this.clearEntries();

		for (E entry : collection) {
			this.addEntry(entry);
		}
	}

	private int getFirstEntryY() {
		return this.getY() + 2;
	}

	public int getNextY() {
		int i = this.getFirstEntryY() - (int)this.scrollAmount();

		for (E entry : this.children) {
			i += entry.getHeight();
		}

		return i;
	}

	protected int addEntry(E entry) {
		return this.addEntry(entry, this.defaultEntryHeight);
	}

	protected int addEntry(E entry, int i) {
		entry.setX(this.getRowLeft());
		entry.setWidth(this.getRowWidth());
		entry.setY(this.getNextY());
		entry.setHeight(i);
		this.children.add(entry);
		return this.children.size() - 1;
	}

	protected void addEntryToTop(E entry) {
		this.addEntryToTop(entry, this.defaultEntryHeight);
	}

	protected void addEntryToTop(E entry, int i) {
		double d = this.maxScrollAmount() - this.scrollAmount();
		entry.setHeight(i);
		this.children.addFirst(entry);
		this.repositionEntries();
		this.setScrollAmount(this.maxScrollAmount() - d);
	}

	private void repositionEntries() {
		int i = this.getFirstEntryY() - (int)this.scrollAmount();

		for (E entry : this.children) {
			entry.setY(i);
			i += entry.getHeight();
			entry.setX(this.getRowLeft());
			entry.setWidth(this.getRowWidth());
		}
	}

	protected void removeEntryFromTop(E entry) {
		double d = this.maxScrollAmount() - this.scrollAmount();
		this.removeEntry(entry);
		this.setScrollAmount(this.maxScrollAmount() - d);
	}

	protected int getItemCount() {
		return this.children().size();
	}

	protected boolean entriesCanBeSelected() {
		return true;
	}

	protected final @Nullable E getEntryAtPosition(double d, double e) {
		for (E entry : this.children) {
			if (entry.isMouseOver(d, e)) {
				return entry;
			}
		}

		return null;
	}

	public void updateSize(int i, HeaderAndFooterLayout headerAndFooterLayout) {
		this.updateSizeAndPosition(i, headerAndFooterLayout.getContentHeight(), headerAndFooterLayout.getHeaderHeight());
	}

	public void updateSizeAndPosition(int i, int j, int k) {
		this.updateSizeAndPosition(i, j, 0, k);
	}

	public void updateSizeAndPosition(int i, int j, int k, int l) {
		this.setSize(i, j);
		this.setPosition(k, l);
		this.repositionEntries();
		if (this.getSelected() != null) {
			this.scrollToEntry(this.getSelected());
		}

		this.refreshScrollAmount();
	}

	@Override
	protected int contentHeight() {
		int i = 0;

		for (E entry : this.children) {
			i += entry.getHeight();
		}

		return i + 4;
	}

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
		this.hovered = this.isMouseOver(i, j) ? this.getEntryAtPosition(i, j) : null;
		this.renderListBackground(guiGraphics);
		this.enableScissor(guiGraphics);
		this.renderListItems(guiGraphics, i, j, f);
		guiGraphics.disableScissor();
		this.renderListSeparators(guiGraphics);
		this.renderScrollbar(guiGraphics, i, j);
	}

	protected void renderListSeparators(GuiGraphics guiGraphics) {
		Identifier identifier = this.minecraft.level == null ? Screen.HEADER_SEPARATOR : Screen.INWORLD_HEADER_SEPARATOR;
		Identifier identifier2 = this.minecraft.level == null ? Screen.FOOTER_SEPARATOR : Screen.INWORLD_FOOTER_SEPARATOR;
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, identifier, this.getX(), this.getY() - 2, 0.0F, 0.0F, this.getWidth(), 2, 32, 2);
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, identifier2, this.getX(), this.getBottom(), 0.0F, 0.0F, this.getWidth(), 2, 32, 2);
	}

	protected void renderListBackground(GuiGraphics guiGraphics) {
		Identifier identifier = this.minecraft.level == null ? MENU_LIST_BACKGROUND : INWORLD_MENU_LIST_BACKGROUND;
		guiGraphics.blit(
			RenderPipelines.GUI_TEXTURED,
			identifier,
			this.getX(),
			this.getY(),
			this.getRight(),
			this.getBottom() + (int)this.scrollAmount(),
			this.getWidth(),
			this.getHeight(),
			32,
			32
		);
	}

	protected void enableScissor(GuiGraphics guiGraphics) {
		guiGraphics.enableScissor(this.getX(), this.getY(), this.getRight(), this.getBottom());
	}

	protected void scrollToEntry(E entry) {
		int i = entry.getY() - this.getY() - 2;
		if (i < 0) {
			this.scroll(i);
		}

		int j = this.getBottom() - entry.getY() - entry.getHeight() - 2;
		if (j < 0) {
			this.scroll(-j);
		}
	}

	protected void centerScrollOn(E entry) {
		int i = 0;

		for (E entry2 : this.children) {
			if (entry2 == entry) {
				i += entry2.getHeight() / 2;
				break;
			}

			i += entry2.getHeight();
		}

		this.setScrollAmount(i - this.height / 2.0);
	}

	private void scroll(int i) {
		this.setScrollAmount(this.scrollAmount() + i);
	}

	@Override
	public void setScrollAmount(double d) {
		super.setScrollAmount(d);
		this.repositionEntries();
	}

	@Override
	protected double scrollRate() {
		return this.defaultEntryHeight / 2.0;
	}

	@Override
	protected int scrollBarX() {
		return this.getRowRight() + 6 + 2;
	}

	@Override
	public Optional<GuiEventListener> getChildAt(double d, double e) {
		return Optional.ofNullable(this.getEntryAtPosition(d, e));
	}

	@Override
	public void setFocused(boolean bl) {
		super.setFocused(bl);
		if (!bl) {
			this.setFocused(null);
		}
	}

	@Override
	public void setFocused(@Nullable GuiEventListener guiEventListener) {
		E entry = this.getFocused();
		if (entry != guiEventListener && entry instanceof ContainerEventHandler containerEventHandler) {
			containerEventHandler.setFocused(null);
		}

		super.setFocused(guiEventListener);
		int i = this.children.indexOf(guiEventListener);
		if (i >= 0) {
			E entry2 = this.children.get(i);
			this.setSelected(entry2);
		}
	}

	protected @Nullable E nextEntry(ScreenDirection screenDirection) {
		return this.nextEntry(screenDirection, entry -> true);
	}

	protected @Nullable E nextEntry(ScreenDirection screenDirection, Predicate<E> predicate) {
		return this.nextEntry(screenDirection, predicate, this.getSelected());
	}

	protected @Nullable E nextEntry(ScreenDirection screenDirection, Predicate<E> predicate, @Nullable E entry) {
		int i = switch (screenDirection) {
			case RIGHT, LEFT -> 0;
			case UP -> -1;
			case DOWN -> 1;
		};
		if (!this.children().isEmpty() && i != 0) {
			int j;
			if (entry == null) {
				j = i > 0 ? 0 : this.children().size() - 1;
			} else {
				j = this.children().indexOf(entry) + i;
			}

			for (int k = j; k >= 0 && k < this.children.size(); k += i) {
				E entry2 = this.children().get(k);
				if (predicate.test(entry2)) {
					return entry2;
				}
			}
		}

		return null;
	}

	protected void renderListItems(GuiGraphics guiGraphics, int i, int j, float f) {
		for (E entry : this.children) {
			if (entry.getY() + entry.getHeight() >= this.getY() && entry.getY() <= this.getBottom()) {
				this.renderItem(guiGraphics, i, j, f, entry);
			}
		}
	}

	protected void renderItem(GuiGraphics guiGraphics, int i, int j, float f, E entry) {
		if (this.entriesCanBeSelected() && this.getSelected() == entry) {
			int k = this.isFocused() ? -1 : -8355712;
			this.renderSelection(guiGraphics, entry, k);
		}

		entry.renderContent(guiGraphics, i, j, Objects.equals(this.hovered, entry), f);
	}

	protected void renderSelection(GuiGraphics guiGraphics, E entry, int i) {
		int j = entry.getX();
		int k = entry.getY();
		int l = j + entry.getWidth();
		int m = k + entry.getHeight();
		guiGraphics.fill(j, k, l, m, i);
		guiGraphics.fill(j + 1, k + 1, l - 1, m - 1, -16777216);
	}

	public int getRowLeft() {
		return this.getX() + this.width / 2 - this.getRowWidth() / 2;
	}

	public int getRowRight() {
		return this.getRowLeft() + this.getRowWidth();
	}

	public int getRowTop(int i) {
		return this.children.get(i).getY();
	}

	public int getRowBottom(int i) {
		E entry = this.children.get(i);
		return entry.getY() + entry.getHeight();
	}

	public int getRowWidth() {
		return 220;
	}

	@Override
	public NarratableEntry.NarrationPriority narrationPriority() {
		if (this.isFocused()) {
			return NarratableEntry.NarrationPriority.FOCUSED;
		} else {
			return this.hovered != null ? NarratableEntry.NarrationPriority.HOVERED : NarratableEntry.NarrationPriority.NONE;
		}
	}

	protected void removeEntries(List<E> list) {
		list.forEach(this::removeEntry);
	}

	protected void removeEntry(E entry) {
		boolean bl = this.children.remove(entry);
		if (bl) {
			this.repositionEntries();
			if (entry == this.getSelected()) {
				this.setSelected(null);
			}
		}
	}

	protected @Nullable E getHovered() {
		return this.hovered;
	}

	void bindEntryToSelf(AbstractSelectionList.Entry<E> entry) {
		entry.list = this;
	}

	protected void narrateListElementPosition(NarrationElementOutput narrationElementOutput, E entry) {
		List<E> list = this.children();
		if (list.size() > 1) {
			int i = list.indexOf(entry);
			if (i != -1) {
				narrationElementOutput.add(NarratedElementType.POSITION, Component.translatable("narrator.position.list", i + 1, list.size()));
			}
		}
	}

	@Environment(EnvType.CLIENT)
	protected abstract static class Entry<E extends AbstractSelectionList.Entry<E>> implements GuiEventListener, LayoutElement {
		public static final int CONTENT_PADDING = 2;
		private int x = 0;
		private int y = 0;
		private int width = 0;
		private int height;
		@Deprecated
		AbstractSelectionList<E> list;

		@Override
		public void setFocused(boolean bl) {
		}

		@Override
		public boolean isFocused() {
			return this.list.getFocused() == this;
		}

		public abstract void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f);

		@Override
		public boolean isMouseOver(double d, double e) {
			return this.getRectangle().containsPoint((int)d, (int)e);
		}

		@Override
		public void setX(int i) {
			this.x = i;
		}

		@Override
		public void setY(int i) {
			this.y = i;
		}

		public void setWidth(int i) {
			this.width = i;
		}

		public void setHeight(int i) {
			this.height = i;
		}

		public int getContentX() {
			return this.getX() + 2;
		}

		public int getContentY() {
			return this.getY() + 2;
		}

		public int getContentHeight() {
			return this.getHeight() - 4;
		}

		public int getContentYMiddle() {
			return this.getContentY() + this.getContentHeight() / 2;
		}

		public int getContentBottom() {
			return this.getContentY() + this.getContentHeight();
		}

		public int getContentWidth() {
			return this.getWidth() - 4;
		}

		public int getContentXMiddle() {
			return this.getContentX() + this.getContentWidth() / 2;
		}

		public int getContentRight() {
			return this.getContentX() + this.getContentWidth();
		}

		@Override
		public int getX() {
			return this.x;
		}

		@Override
		public int getY() {
			return this.y;
		}

		@Override
		public int getWidth() {
			return this.width;
		}

		@Override
		public int getHeight() {
			return this.height;
		}

		@Override
		public void visitWidgets(Consumer<AbstractWidget> consumer) {
		}

		@Override
		public ScreenRectangle getRectangle() {
			return LayoutElement.super.getRectangle();
		}
	}

	@Environment(EnvType.CLIENT)
	class TrackedList extends AbstractList<E> {
		private final List<E> delegate = Lists.newArrayList();

		public E get(int i) {
			return this.delegate.get(i);
		}

		@Override
		public int size() {
			return this.delegate.size();
		}

		public E set(int i, E entry) {
			E entry2 = this.delegate.set(i, entry);
			AbstractSelectionList.this.bindEntryToSelf(entry);
			return entry2;
		}

		public void add(int i, E entry) {
			this.delegate.add(i, entry);
			AbstractSelectionList.this.bindEntryToSelf(entry);
		}

		public E remove(int i) {
			return this.delegate.remove(i);
		}
	}
}
