package net.minecraft.client.resources.model;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class Material {
	public static final Comparator<Material> COMPARATOR = Comparator.comparing(Material::atlasLocation).thenComparing(Material::texture);
	private final Identifier atlasLocation;
	private final Identifier texture;
	private @Nullable RenderType renderType;

	public Material(Identifier identifier, Identifier identifier2) {
		this.atlasLocation = identifier;
		this.texture = identifier2;
	}

	public Identifier atlasLocation() {
		return this.atlasLocation;
	}

	public Identifier texture() {
		return this.texture;
	}

	public RenderType renderType(Function<Identifier, RenderType> function) {
		if (this.renderType == null) {
			this.renderType = function.apply(this.atlasLocation);
		}

		return this.renderType;
	}

	public VertexConsumer buffer(MaterialSet materialSet, MultiBufferSource multiBufferSource, Function<Identifier, RenderType> function) {
		return materialSet.get(this).wrap(multiBufferSource.getBuffer(this.renderType(function)));
	}

	public VertexConsumer buffer(MaterialSet materialSet, MultiBufferSource multiBufferSource, Function<Identifier, RenderType> function, boolean bl, boolean bl2) {
		return materialSet.get(this).wrap(ItemRenderer.getFoilBuffer(multiBufferSource, this.renderType(function), bl, bl2));
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		} else if (object != null && this.getClass() == object.getClass()) {
			Material material = (Material)object;
			return this.atlasLocation.equals(material.atlasLocation) && this.texture.equals(material.texture);
		} else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.atlasLocation, this.texture);
	}

	@Override
	public String toString() {
		return "Material{atlasLocation=" + this.atlasLocation + ", texture=" + this.texture + "}";
	}
}
