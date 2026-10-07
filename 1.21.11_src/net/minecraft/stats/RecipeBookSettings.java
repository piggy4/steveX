package net.minecraft.stats;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.function.UnaryOperator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.inventory.RecipeBookType;

public final class RecipeBookSettings {
	public static final StreamCodec<FriendlyByteBuf, RecipeBookSettings> STREAM_CODEC = StreamCodec.composite(
		RecipeBookSettings.TypeSettings.STREAM_CODEC,
		recipeBookSettings -> recipeBookSettings.crafting,
		RecipeBookSettings.TypeSettings.STREAM_CODEC,
		recipeBookSettings -> recipeBookSettings.furnace,
		RecipeBookSettings.TypeSettings.STREAM_CODEC,
		recipeBookSettings -> recipeBookSettings.blastFurnace,
		RecipeBookSettings.TypeSettings.STREAM_CODEC,
		recipeBookSettings -> recipeBookSettings.smoker,
		RecipeBookSettings::new
	);
	public static final MapCodec<RecipeBookSettings> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				RecipeBookSettings.TypeSettings.CRAFTING_MAP_CODEC.forGetter(recipeBookSettings -> recipeBookSettings.crafting),
				RecipeBookSettings.TypeSettings.FURNACE_MAP_CODEC.forGetter(recipeBookSettings -> recipeBookSettings.furnace),
				RecipeBookSettings.TypeSettings.BLAST_FURNACE_MAP_CODEC.forGetter(recipeBookSettings -> recipeBookSettings.blastFurnace),
				RecipeBookSettings.TypeSettings.SMOKER_MAP_CODEC.forGetter(recipeBookSettings -> recipeBookSettings.smoker)
			)
			.apply(instance, RecipeBookSettings::new)
	);
	private RecipeBookSettings.TypeSettings crafting;
	private RecipeBookSettings.TypeSettings furnace;
	private RecipeBookSettings.TypeSettings blastFurnace;
	private RecipeBookSettings.TypeSettings smoker;

	public RecipeBookSettings() {
		this(
			RecipeBookSettings.TypeSettings.DEFAULT,
			RecipeBookSettings.TypeSettings.DEFAULT,
			RecipeBookSettings.TypeSettings.DEFAULT,
			RecipeBookSettings.TypeSettings.DEFAULT
		);
	}

	private RecipeBookSettings(
		RecipeBookSettings.TypeSettings typeSettings,
		RecipeBookSettings.TypeSettings typeSettings2,
		RecipeBookSettings.TypeSettings typeSettings3,
		RecipeBookSettings.TypeSettings typeSettings4
	) {
		this.crafting = typeSettings;
		this.furnace = typeSettings2;
		this.blastFurnace = typeSettings3;
		this.smoker = typeSettings4;
	}

	@VisibleForTesting
	public RecipeBookSettings.TypeSettings getSettings(RecipeBookType recipeBookType) {
		return switch (recipeBookType) {
			case CRAFTING -> this.crafting;
			case FURNACE -> this.furnace;
			case BLAST_FURNACE -> this.blastFurnace;
			case SMOKER -> this.smoker;
		};
	}

	private void updateSettings(RecipeBookType recipeBookType, UnaryOperator<RecipeBookSettings.TypeSettings> unaryOperator) {
		switch (recipeBookType) {
			case CRAFTING:
				this.crafting = unaryOperator.apply(this.crafting);
				break;
			case FURNACE:
				this.furnace = unaryOperator.apply(this.furnace);
				break;
			case BLAST_FURNACE:
				this.blastFurnace = unaryOperator.apply(this.blastFurnace);
				break;
			case SMOKER:
				this.smoker = unaryOperator.apply(this.smoker);
		}
	}

	public boolean isOpen(RecipeBookType recipeBookType) {
		return this.getSettings(recipeBookType).open;
	}

	public void setOpen(RecipeBookType recipeBookType, boolean bl) {
		this.updateSettings(recipeBookType, typeSettings -> typeSettings.setOpen(bl));
	}

	public boolean isFiltering(RecipeBookType recipeBookType) {
		return this.getSettings(recipeBookType).filtering;
	}

	public void setFiltering(RecipeBookType recipeBookType, boolean bl) {
		this.updateSettings(recipeBookType, typeSettings -> typeSettings.setFiltering(bl));
	}

	public RecipeBookSettings copy() {
		return new RecipeBookSettings(this.crafting, this.furnace, this.blastFurnace, this.smoker);
	}

	public void replaceFrom(RecipeBookSettings recipeBookSettings) {
		this.crafting = recipeBookSettings.crafting;
		this.furnace = recipeBookSettings.furnace;
		this.blastFurnace = recipeBookSettings.blastFurnace;
		this.smoker = recipeBookSettings.smoker;
	}

	public record TypeSettings(boolean open, boolean filtering) {
		public static final RecipeBookSettings.TypeSettings DEFAULT = new RecipeBookSettings.TypeSettings(false, false);
		public static final MapCodec<RecipeBookSettings.TypeSettings> CRAFTING_MAP_CODEC = codec("isGuiOpen", "isFilteringCraftable");
		public static final MapCodec<RecipeBookSettings.TypeSettings> FURNACE_MAP_CODEC = codec("isFurnaceGuiOpen", "isFurnaceFilteringCraftable");
		public static final MapCodec<RecipeBookSettings.TypeSettings> BLAST_FURNACE_MAP_CODEC = codec(
			"isBlastingFurnaceGuiOpen", "isBlastingFurnaceFilteringCraftable"
		);
		public static final MapCodec<RecipeBookSettings.TypeSettings> SMOKER_MAP_CODEC = codec("isSmokerGuiOpen", "isSmokerFilteringCraftable");
		public static final StreamCodec<ByteBuf, RecipeBookSettings.TypeSettings> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL,
			RecipeBookSettings.TypeSettings::open,
			ByteBufCodecs.BOOL,
			RecipeBookSettings.TypeSettings::filtering,
			RecipeBookSettings.TypeSettings::new
		);

		@Override
		public String toString() {
			return "[open=" + this.open + ", filtering=" + this.filtering + "]";
		}

		public RecipeBookSettings.TypeSettings setOpen(boolean bl) {
			return new RecipeBookSettings.TypeSettings(bl, this.filtering);
		}

		public RecipeBookSettings.TypeSettings setFiltering(boolean bl) {
			return new RecipeBookSettings.TypeSettings(this.open, bl);
		}

		private static MapCodec<RecipeBookSettings.TypeSettings> codec(String string, String string2) {
			return RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						Codec.BOOL.optionalFieldOf(string, false).forGetter(RecipeBookSettings.TypeSettings::open),
						Codec.BOOL.optionalFieldOf(string2, false).forGetter(RecipeBookSettings.TypeSettings::filtering)
					)
					.apply(instance, RecipeBookSettings.TypeSettings::new)
			);
		}
	}
}
