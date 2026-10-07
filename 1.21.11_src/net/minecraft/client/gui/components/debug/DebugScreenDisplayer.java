package net.minecraft.client.gui.components.debug;

import java.util.Collection;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public interface DebugScreenDisplayer {
	void addPriorityLine(String string);

	void addLine(String string);

	void addToGroup(Identifier identifier, Collection<String> collection);

	void addToGroup(Identifier identifier, String string);
}
