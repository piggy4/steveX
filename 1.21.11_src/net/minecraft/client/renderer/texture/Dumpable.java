package net.minecraft.client.renderer.texture;

import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public interface Dumpable {
	void dumpContents(Identifier identifier, Path path) throws IOException;
}
