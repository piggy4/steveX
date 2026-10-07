package net.minecraft.world.entity.player;

import com.mojang.datafixers.DataFixUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

public record PlayerSkin(
	ClientAsset.Texture body, ClientAsset.@Nullable Texture cape, ClientAsset.@Nullable Texture elytra, PlayerModelType model, boolean secure
) {
	public static PlayerSkin insecure(
		ClientAsset.Texture texture, ClientAsset.@Nullable Texture texture2, ClientAsset.@Nullable Texture texture3, PlayerModelType playerModelType
	) {
		return new PlayerSkin(texture, texture2, texture3, playerModelType, false);
	}

	public PlayerSkin with(PlayerSkin.Patch patch) {
		return patch.equals(PlayerSkin.Patch.EMPTY)
			? this
			: insecure(
				DataFixUtils.orElse(patch.body, this.body),
				DataFixUtils.orElse(patch.cape, this.cape),
				DataFixUtils.orElse(patch.elytra, this.elytra),
				patch.model.orElse(this.model)
			);
	}

	public record Patch(
		Optional<ClientAsset.ResourceTexture> body,
		Optional<ClientAsset.ResourceTexture> cape,
		Optional<ClientAsset.ResourceTexture> elytra,
		Optional<PlayerModelType> model
	) {
		public static final PlayerSkin.Patch EMPTY = new PlayerSkin.Patch(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
		public static final MapCodec<PlayerSkin.Patch> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ClientAsset.ResourceTexture.CODEC.optionalFieldOf("texture").forGetter(PlayerSkin.Patch::body),
					ClientAsset.ResourceTexture.CODEC.optionalFieldOf("cape").forGetter(PlayerSkin.Patch::cape),
					ClientAsset.ResourceTexture.CODEC.optionalFieldOf("elytra").forGetter(PlayerSkin.Patch::elytra),
					PlayerModelType.CODEC.optionalFieldOf("model").forGetter(PlayerSkin.Patch::model)
				)
				.apply(instance, PlayerSkin.Patch::create)
		);
		public static final StreamCodec<ByteBuf, PlayerSkin.Patch> STREAM_CODEC = StreamCodec.composite(
			ClientAsset.ResourceTexture.STREAM_CODEC.apply(ByteBufCodecs::optional),
			PlayerSkin.Patch::body,
			ClientAsset.ResourceTexture.STREAM_CODEC.apply(ByteBufCodecs::optional),
			PlayerSkin.Patch::cape,
			ClientAsset.ResourceTexture.STREAM_CODEC.apply(ByteBufCodecs::optional),
			PlayerSkin.Patch::elytra,
			PlayerModelType.STREAM_CODEC.apply(ByteBufCodecs::optional),
			PlayerSkin.Patch::model,
			PlayerSkin.Patch::create
		);

		public static PlayerSkin.Patch create(
			Optional<ClientAsset.ResourceTexture> optional,
			Optional<ClientAsset.ResourceTexture> optional2,
			Optional<ClientAsset.ResourceTexture> optional3,
			Optional<PlayerModelType> optional4
		) {
			return optional.isEmpty() && optional2.isEmpty() && optional3.isEmpty() && optional4.isEmpty()
				? EMPTY
				: new PlayerSkin.Patch(optional, optional2, optional3, optional4);
		}
	}
}
