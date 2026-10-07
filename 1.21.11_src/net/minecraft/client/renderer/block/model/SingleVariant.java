package net.minecraft.client.renderer.block.model;

import com.mojang.serialization.Codec;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.util.RandomSource;

@Environment(EnvType.CLIENT)
public class SingleVariant implements BlockStateModel {
	private final BlockModelPart model;

	public SingleVariant(BlockModelPart blockModelPart) {
		this.model = blockModelPart;
	}

	@Override
	public void collectParts(RandomSource randomSource, List<BlockModelPart> list) {
		list.add(this.model);
	}

	@Override
	public TextureAtlasSprite particleIcon() {
		return this.model.particleIcon();
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked(Variant variant) implements BlockStateModel.Unbaked {
		public static final Codec<SingleVariant.Unbaked> CODEC = Variant.CODEC.xmap(SingleVariant.Unbaked::new, SingleVariant.Unbaked::variant);

		@Override
		public BlockStateModel bake(ModelBaker modelBaker) {
			return new SingleVariant(this.variant.bake(modelBaker));
		}

		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
			this.variant.resolveDependencies(resolver);
		}
	}
}
