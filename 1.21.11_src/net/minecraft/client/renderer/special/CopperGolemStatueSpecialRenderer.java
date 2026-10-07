package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.statue.CopperGolemStatueModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.golem.CopperGolemOxidationLevels;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.CopperGolemStatueBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public class CopperGolemStatueSpecialRenderer implements NoDataSpecialModelRenderer {
	private static final Direction MODEL_STATE = Direction.SOUTH;
	private final CopperGolemStatueModel model;
	private final Identifier texture;

	public CopperGolemStatueSpecialRenderer(CopperGolemStatueModel copperGolemStatueModel, Identifier identifier) {
		this.model = copperGolemStatueModel;
		this.texture = identifier;
	}

	@Override
	public void submit(ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, boolean bl, int k) {
		positionModel(poseStack);
		submitNodeCollector.submitModel(this.model, Direction.SOUTH, poseStack, RenderTypes.entityCutoutNoCull(this.texture), i, j, -1, null, k, null);
	}

	@Override
	public void getExtents(Consumer<Vector3fc> consumer) {
		PoseStack poseStack = new PoseStack();
		positionModel(poseStack);
		this.model.setupAnim(MODEL_STATE);
		this.model.root().getExtentsForGui(poseStack, consumer);
	}

	private static void positionModel(PoseStack poseStack) {
		poseStack.translate(0.5F, 1.5F, 0.5F);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked(Identifier texture, CopperGolemStatueBlock.Pose pose) implements SpecialModelRenderer.Unbaked {
		public static final MapCodec<CopperGolemStatueSpecialRenderer.Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Identifier.CODEC.fieldOf("texture").forGetter(CopperGolemStatueSpecialRenderer.Unbaked::texture),
					CopperGolemStatueBlock.Pose.CODEC.fieldOf("pose").forGetter(CopperGolemStatueSpecialRenderer.Unbaked::pose)
				)
				.apply(instance, CopperGolemStatueSpecialRenderer.Unbaked::new)
		);

		public Unbaked(WeatheringCopper.WeatherState weatherState, CopperGolemStatueBlock.Pose pose) {
			this(CopperGolemOxidationLevels.getOxidationLevel(weatherState).texture(), pose);
		}

		@Override
		public MapCodec<CopperGolemStatueSpecialRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext bakingContext) {
			CopperGolemStatueModel copperGolemStatueModel = new CopperGolemStatueModel(bakingContext.entityModelSet().bakeLayer(getModel(this.pose)));
			return new CopperGolemStatueSpecialRenderer(copperGolemStatueModel, this.texture);
		}

		private static ModelLayerLocation getModel(CopperGolemStatueBlock.Pose pose) {
			return switch (pose) {
				case STANDING -> ModelLayers.COPPER_GOLEM;
				case SITTING -> ModelLayers.COPPER_GOLEM_SITTING;
				case STAR -> ModelLayers.COPPER_GOLEM_STAR;
				case RUNNING -> ModelLayers.COPPER_GOLEM_RUNNING;
			};
		}
	}
}
