package net.minecraft.client.resources.metadata.animation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.util.StringRepresentable;

@Environment(EnvType.CLIENT)
public record VillagerMetadataSection(VillagerMetadataSection.Hat hat) {
	public static final Codec<VillagerMetadataSection> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(VillagerMetadataSection.Hat.CODEC.optionalFieldOf("hat", VillagerMetadataSection.Hat.NONE).forGetter(VillagerMetadataSection::hat))
			.apply(instance, VillagerMetadataSection::new)
	);
	public static final MetadataSectionType<VillagerMetadataSection> TYPE = new MetadataSectionType<>("villager", CODEC);

	@Environment(EnvType.CLIENT)
	public enum Hat implements StringRepresentable {
		NONE("none"),
		PARTIAL("partial"),
		FULL("full");

		public static final Codec<VillagerMetadataSection.Hat> CODEC = StringRepresentable.fromEnum(VillagerMetadataSection.Hat::values);
		private final String name;

		Hat(final String string2) {
			this.name = string2;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}
}
