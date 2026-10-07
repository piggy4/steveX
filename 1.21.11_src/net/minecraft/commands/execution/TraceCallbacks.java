package net.minecraft.commands.execution;

import net.minecraft.resources.Identifier;

public interface TraceCallbacks extends AutoCloseable {
	void onCommand(int i, String string);

	void onReturn(int i, String string, int j);

	void onError(String string);

	void onCall(int i, Identifier identifier, int j);

	@Override
	void close();
}
