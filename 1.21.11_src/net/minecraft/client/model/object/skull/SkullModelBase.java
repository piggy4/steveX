package net.minecraft.client.model.object.skull;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;

@Environment(EnvType.CLIENT)
public abstract class SkullModelBase extends Model<SkullModelBase.State> {
	public SkullModelBase(ModelPart modelPart) {
		super(modelPart, RenderTypes::entityTranslucent);
	}

	@Environment(EnvType.CLIENT)
	public static class State {
		public float animationPos;
		public float yRot;
		public float xRot;
	}
}
