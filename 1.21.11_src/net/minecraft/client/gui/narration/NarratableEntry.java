package net.minecraft.client.gui.narration;

import java.util.Collection;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.TabOrderedElement;

@Environment(EnvType.CLIENT)
public interface NarratableEntry extends TabOrderedElement, NarrationSupplier {
	NarratableEntry.NarrationPriority narrationPriority();

	default boolean isActive() {
		return true;
	}

	default Collection<? extends NarratableEntry> getNarratables() {
		return List.of(this);
	}

	@Environment(EnvType.CLIENT)
	enum NarrationPriority {
		NONE,
		HOVERED,
		FOCUSED;

		public boolean isTerminal() {
			return this == FOCUSED;
		}
	}
}
