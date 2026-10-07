package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BedRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public class BedSpecialRenderer implements NoDataSpecialModelRenderer {
	private final BedRenderer bedRenderer;
	private final Material material;

	public BedSpecialRenderer(BedRenderer bedRenderer, Material material) {
		this.bedRenderer = bedRenderer;
		this.material = material;
	}

	@Override
	public void submit(ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, boolean bl, int k) {
		this.bedRenderer.submitSpecial(poseStack, submitNodeCollector, i, j, this.material, k);
	}

	@Override
	public void getExtents(Consumer<Vector3fc> consumer) {
		this.bedRenderer.getExtents(consumer);
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked(Identifier texture) implements SpecialModelRenderer.Unbaked {
		public static final MapCodec<BedSpecialRenderer.Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(Identifier.CODEC.fieldOf("texture").forGetter(BedSpecialRenderer.Unbaked::texture))
				.apply(instance, BedSpecialRenderer.Unbaked::new)
		);

		public Unbaked(DyeColor dyeColor) {
			this(Sheets.colorToResourceMaterial(dyeColor));
		}

		@Override
		public MapCodec<BedSpecialRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext bakingContext) {
			return new BedSpecialRenderer(new BedRenderer(bakingContext), Sheets.BED_MAPPER.apply(this.texture));
		}
	}
}
