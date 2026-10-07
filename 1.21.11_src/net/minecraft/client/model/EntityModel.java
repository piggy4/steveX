package net.minecraft.client.model;

import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public abstract class EntityModel<T extends EntityRenderState> extends Model<T> {
	public static final float MODEL_Y_OFFSET = -1.501F;

	protected EntityModel(ModelPart modelPart) {
		this(modelPart, RenderTypes::entityCutoutNoCull);
	}

	protected EntityModel(ModelPart modelPart, Function<Identifier, RenderType> function) {
		super(modelPart, function);
	}
}
