package net.minecraft.network.protocol.game;

import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.Identifier;

public class ClientboundUpdateAdvancementsPacket implements Packet<ClientGamePacketListener> {
	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundUpdateAdvancementsPacket> STREAM_CODEC = Packet.codec(
		ClientboundUpdateAdvancementsPacket::write, ClientboundUpdateAdvancementsPacket::new
	);
	private final boolean reset;
	private final List<AdvancementHolder> added;
	private final Set<Identifier> removed;
	private final Map<Identifier, AdvancementProgress> progress;
	private final boolean showAdvancements;

	public ClientboundUpdateAdvancementsPacket(
		boolean bl, Collection<AdvancementHolder> collection, Set<Identifier> set, Map<Identifier, AdvancementProgress> map, boolean bl2
	) {
		this.reset = bl;
		this.added = List.copyOf(collection);
		this.removed = Set.copyOf(set);
		this.progress = Map.copyOf(map);
		this.showAdvancements = bl2;
	}

	private ClientboundUpdateAdvancementsPacket(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
		this.reset = registryFriendlyByteBuf.readBoolean();
		this.added = AdvancementHolder.LIST_STREAM_CODEC.decode(registryFriendlyByteBuf);
		this.removed = registryFriendlyByteBuf.readCollection(Sets::newLinkedHashSetWithExpectedSize, FriendlyByteBuf::readIdentifier);
		this.progress = registryFriendlyByteBuf.readMap(FriendlyByteBuf::readIdentifier, AdvancementProgress::fromNetwork);
		this.showAdvancements = registryFriendlyByteBuf.readBoolean();
	}

	private void write(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
		registryFriendlyByteBuf.writeBoolean(this.reset);
		AdvancementHolder.LIST_STREAM_CODEC.encode(registryFriendlyByteBuf, this.added);
		registryFriendlyByteBuf.writeCollection(this.removed, FriendlyByteBuf::writeIdentifier);
		registryFriendlyByteBuf.writeMap(
			this.progress, FriendlyByteBuf::writeIdentifier, (friendlyByteBuf, advancementProgress) -> advancementProgress.serializeToNetwork(friendlyByteBuf)
		);
		registryFriendlyByteBuf.writeBoolean(this.showAdvancements);
	}

	@Override
	public PacketType<ClientboundUpdateAdvancementsPacket> type() {
		return GamePacketTypes.CLIENTBOUND_UPDATE_ADVANCEMENTS;
	}

	public void handle(ClientGamePacketListener clientGamePacketListener) {
		clientGamePacketListener.handleUpdateAdvancementsPacket(this);
	}

	public List<AdvancementHolder> getAdded() {
		return this.added;
	}

	public Set<Identifier> getRemoved() {
		return this.removed;
	}

	public Map<Identifier, AdvancementProgress> getProgress() {
		return this.progress;
	}

	public boolean shouldReset() {
		return this.reset;
	}

	public boolean shouldShowAdvancements() {
		return this.showAdvancements;
	}
}
