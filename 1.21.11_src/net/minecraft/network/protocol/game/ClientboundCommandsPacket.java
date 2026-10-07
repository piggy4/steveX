package net.minecraft.network.protocol.game;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.function.BiPredicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class ClientboundCommandsPacket implements Packet<ClientGamePacketListener> {
	public static final StreamCodec<FriendlyByteBuf, ClientboundCommandsPacket> STREAM_CODEC = Packet.codec(
		ClientboundCommandsPacket::write, ClientboundCommandsPacket::new
	);
	private static final byte MASK_TYPE = 3;
	private static final byte FLAG_EXECUTABLE = 4;
	private static final byte FLAG_REDIRECT = 8;
	private static final byte FLAG_CUSTOM_SUGGESTIONS = 16;
	private static final byte FLAG_RESTRICTED = 32;
	private static final byte TYPE_ROOT = 0;
	private static final byte TYPE_LITERAL = 1;
	private static final byte TYPE_ARGUMENT = 2;
	private final int rootIndex;
	private final List<ClientboundCommandsPacket.Entry> entries;

	public <S> ClientboundCommandsPacket(RootCommandNode<S> rootCommandNode, ClientboundCommandsPacket.NodeInspector<S> nodeInspector) {
		Object2IntMap<CommandNode<S>> object2IntMap = enumerateNodes(rootCommandNode);
		this.entries = createEntries(object2IntMap, nodeInspector);
		this.rootIndex = object2IntMap.getInt(rootCommandNode);
	}

	private ClientboundCommandsPacket(FriendlyByteBuf friendlyByteBuf) {
		this.entries = friendlyByteBuf.readList(ClientboundCommandsPacket::readNode);
		this.rootIndex = friendlyByteBuf.readVarInt();
		validateEntries(this.entries);
	}

	private void write(FriendlyByteBuf friendlyByteBuf) {
		friendlyByteBuf.writeCollection(this.entries, (friendlyByteBufx, entry) -> entry.write(friendlyByteBufx));
		friendlyByteBuf.writeVarInt(this.rootIndex);
	}

	private static void validateEntries(List<ClientboundCommandsPacket.Entry> list, BiPredicate<ClientboundCommandsPacket.Entry, IntSet> biPredicate) {
		IntSet intSet = new IntOpenHashSet(IntSets.fromTo(0, list.size()));

		while (!intSet.isEmpty()) {
			boolean bl = intSet.removeIf(i -> biPredicate.test(list.get(i), intSet));
			if (!bl) {
				throw new IllegalStateException("Server sent an impossible command tree");
			}
		}
	}

	private static void validateEntries(List<ClientboundCommandsPacket.Entry> list) {
		validateEntries(list, ClientboundCommandsPacket.Entry::canBuild);
		validateEntries(list, ClientboundCommandsPacket.Entry::canResolve);
	}

	private static <S> Object2IntMap<CommandNode<S>> enumerateNodes(RootCommandNode<S> rootCommandNode) {
		Object2IntMap<CommandNode<S>> object2IntMap = new Object2IntOpenHashMap<>();
		Queue<CommandNode<S>> queue = new ArrayDeque<>();
		queue.add(rootCommandNode);

		CommandNode<S> commandNode;
		while ((commandNode = queue.poll()) != null) {
			if (!object2IntMap.containsKey(commandNode)) {
				int i = object2IntMap.size();
				object2IntMap.put(commandNode, i);
				queue.addAll(commandNode.getChildren());
				if (commandNode.getRedirect() != null) {
					queue.add(commandNode.getRedirect());
				}
			}
		}

		return object2IntMap;
	}

	private static <S> List<ClientboundCommandsPacket.Entry> createEntries(
		Object2IntMap<CommandNode<S>> object2IntMap, ClientboundCommandsPacket.NodeInspector<S> nodeInspector
	) {
		ObjectArrayList<ClientboundCommandsPacket.Entry> objectArrayList = new ObjectArrayList<>(object2IntMap.size());
		objectArrayList.size(object2IntMap.size());

		for (Object2IntMap.Entry<CommandNode<S>> entry : Object2IntMaps.fastIterable(object2IntMap)) {
			objectArrayList.set(entry.getIntValue(), createEntry(entry.getKey(), nodeInspector, object2IntMap));
		}

		return objectArrayList;
	}

	private static ClientboundCommandsPacket.Entry readNode(FriendlyByteBuf friendlyByteBuf) {
		byte b = friendlyByteBuf.readByte();
		int[] is = friendlyByteBuf.readVarIntArray();
		int i = (b & 8) != 0 ? friendlyByteBuf.readVarInt() : 0;
		ClientboundCommandsPacket.NodeStub nodeStub = read(friendlyByteBuf, b);
		return new ClientboundCommandsPacket.Entry(nodeStub, b, i, is);
	}

	private static ClientboundCommandsPacket.@Nullable NodeStub read(FriendlyByteBuf friendlyByteBuf, byte b) {
		int i = b & 3;
		if (i == 2) {
			String string = friendlyByteBuf.readUtf();
			int j = friendlyByteBuf.readVarInt();
			ArgumentTypeInfo<?, ?> argumentTypeInfo = BuiltInRegistries.COMMAND_ARGUMENT_TYPE.byId(j);
			if (argumentTypeInfo == null) {
				return null;
			}

			ArgumentTypeInfo.Template<?> template = argumentTypeInfo.deserializeFromNetwork(friendlyByteBuf);
			Identifier identifier = (b & 16) != 0 ? friendlyByteBuf.readIdentifier() : null;
			return new ClientboundCommandsPacket.ArgumentNodeStub(string, template, identifier);
		} else if (i == 1) {
			String string = friendlyByteBuf.readUtf();
			return new ClientboundCommandsPacket.LiteralNodeStub(string);
		} else {
			return null;
		}
	}

	private static <S> ClientboundCommandsPacket.Entry createEntry(
		CommandNode<S> commandNode, ClientboundCommandsPacket.NodeInspector<S> nodeInspector, Object2IntMap<CommandNode<S>> object2IntMap
	) {
		int i = 0;
		int j;
		if (commandNode.getRedirect() != null) {
			i |= 8;
			j = object2IntMap.getInt(commandNode.getRedirect());
		} else {
			j = 0;
		}

		if (nodeInspector.isExecutable(commandNode)) {
			i |= 4;
		}

		if (nodeInspector.isRestricted(commandNode)) {
			i |= 32;
		}

		ClientboundCommandsPacket.NodeStub nodeStub;
		switch (commandNode) {
			case RootCommandNode<S> rootCommandNode:
				i |= 0;
				nodeStub = null;
				break;
			case ArgumentCommandNode<S, ?> argumentCommandNode:
				Identifier identifier = nodeInspector.suggestionId(argumentCommandNode);
				nodeStub = new ClientboundCommandsPacket.ArgumentNodeStub(
					argumentCommandNode.getName(), ArgumentTypeInfos.unpack(argumentCommandNode.getType()), identifier
				);
				i |= 2;
				if (identifier != null) {
					i |= 16;
				}
				break;
			case LiteralCommandNode<S> literalCommandNode:
				nodeStub = new ClientboundCommandsPacket.LiteralNodeStub(literalCommandNode.getLiteral());
				i |= 1;
				break;
			default:
				throw new UnsupportedOperationException("Unknown node type " + commandNode);
		}

		int[] is = commandNode.getChildren().stream().mapToInt(object2IntMap::getInt).toArray();
		return new ClientboundCommandsPacket.Entry(nodeStub, i, j, is);
	}

	@Override
	public PacketType<ClientboundCommandsPacket> type() {
		return GamePacketTypes.CLIENTBOUND_COMMANDS;
	}

	public void handle(ClientGamePacketListener clientGamePacketListener) {
		clientGamePacketListener.handleCommands(this);
	}

	public <S> RootCommandNode<S> getRoot(CommandBuildContext commandBuildContext, ClientboundCommandsPacket.NodeBuilder<S> nodeBuilder) {
		return (RootCommandNode<S>)new ClientboundCommandsPacket.NodeResolver<>(commandBuildContext, nodeBuilder, this.entries).resolve(this.rootIndex);
	}

	record ArgumentNodeStub(String id, ArgumentTypeInfo.Template<?> argumentType, @Nullable Identifier suggestionId) implements ClientboundCommandsPacket.NodeStub {
		@Override
		public <S> ArgumentBuilder<S, ?> build(CommandBuildContext commandBuildContext, ClientboundCommandsPacket.NodeBuilder<S> nodeBuilder) {
			ArgumentType<?> argumentType = this.argumentType.instantiate(commandBuildContext);
			return nodeBuilder.createArgument(this.id, argumentType, this.suggestionId);
		}

		@Override
		public void write(FriendlyByteBuf friendlyByteBuf) {
			friendlyByteBuf.writeUtf(this.id);
			serializeCap(friendlyByteBuf, this.argumentType);
			if (this.suggestionId != null) {
				friendlyByteBuf.writeIdentifier(this.suggestionId);
			}
		}

		private static <A extends ArgumentType<?>> void serializeCap(FriendlyByteBuf friendlyByteBuf, ArgumentTypeInfo.Template<A> template) {
			serializeCap(friendlyByteBuf, template.type(), template);
		}

		private static <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>> void serializeCap(
			FriendlyByteBuf friendlyByteBuf, ArgumentTypeInfo<A, T> argumentTypeInfo, ArgumentTypeInfo.Template<A> template
		) {
			friendlyByteBuf.writeVarInt(BuiltInRegistries.COMMAND_ARGUMENT_TYPE.getId(argumentTypeInfo));
			argumentTypeInfo.serializeToNetwork((T)template, friendlyByteBuf);
		}
	}

	record Entry(ClientboundCommandsPacket.@Nullable NodeStub stub, int flags, int redirect, int[] children) {

		public void write(FriendlyByteBuf friendlyByteBuf) {
			friendlyByteBuf.writeByte(this.flags);
			friendlyByteBuf.writeVarIntArray(this.children);
			if ((this.flags & 8) != 0) {
				friendlyByteBuf.writeVarInt(this.redirect);
			}

			if (this.stub != null) {
				this.stub.write(friendlyByteBuf);
			}
		}

		public boolean canBuild(IntSet intSet) {
			return (this.flags & 8) != 0 ? !intSet.contains(this.redirect) : true;
		}

		public boolean canResolve(IntSet intSet) {
			for (int i : this.children) {
				if (intSet.contains(i)) {
					return false;
				}
			}

			return true;
		}
	}

	record LiteralNodeStub(String id) implements ClientboundCommandsPacket.NodeStub {
		@Override
		public <S> ArgumentBuilder<S, ?> build(CommandBuildContext commandBuildContext, ClientboundCommandsPacket.NodeBuilder<S> nodeBuilder) {
			return nodeBuilder.createLiteral(this.id);
		}

		@Override
		public void write(FriendlyByteBuf friendlyByteBuf) {
			friendlyByteBuf.writeUtf(this.id);
		}
	}

	public interface NodeBuilder<S> {
		ArgumentBuilder<S, ?> createLiteral(String string);

		ArgumentBuilder<S, ?> createArgument(String string, ArgumentType<?> argumentType, @Nullable Identifier identifier);

		ArgumentBuilder<S, ?> configure(ArgumentBuilder<S, ?> argumentBuilder, boolean bl, boolean bl2);
	}

	public interface NodeInspector<S> {
		@Nullable Identifier suggestionId(ArgumentCommandNode<S, ?> argumentCommandNode);

		boolean isExecutable(CommandNode<S> commandNode);

		boolean isRestricted(CommandNode<S> commandNode);
	}

	static class NodeResolver<S> {
		private final CommandBuildContext context;
		private final ClientboundCommandsPacket.NodeBuilder<S> builder;
		private final List<ClientboundCommandsPacket.Entry> entries;
		private final List<CommandNode<S>> nodes;

		NodeResolver(CommandBuildContext commandBuildContext, ClientboundCommandsPacket.NodeBuilder<S> nodeBuilder, List<ClientboundCommandsPacket.Entry> list) {
			this.context = commandBuildContext;
			this.builder = nodeBuilder;
			this.entries = list;
			ObjectArrayList<CommandNode<S>> objectArrayList = new ObjectArrayList<>();
			objectArrayList.size(list.size());
			this.nodes = objectArrayList;
		}

		public CommandNode<S> resolve(int i) {
			CommandNode<S> commandNode = this.nodes.get(i);
			if (commandNode != null) {
				return commandNode;
			}

			ClientboundCommandsPacket.Entry entry = this.entries.get(i);
			CommandNode<S> commandNode2;
			if (entry.stub == null) {
				commandNode2 = new RootCommandNode<>();
			} else {
				ArgumentBuilder<S, ?> argumentBuilder = entry.stub.build(this.context, this.builder);
				if ((entry.flags & 8) != 0) {
					argumentBuilder.redirect(this.resolve(entry.redirect));
				}

				boolean bl = (entry.flags & 4) != 0;
				boolean bl2 = (entry.flags & 32) != 0;
				commandNode2 = this.builder.configure(argumentBuilder, bl, bl2).build();
			}

			this.nodes.set(i, commandNode2);

			for (int j : entry.children) {
				CommandNode<S> commandNode3 = this.resolve(j);
				if (!(commandNode3 instanceof RootCommandNode)) {
					commandNode2.addChild(commandNode3);
				}
			}

			return commandNode2;
		}
	}

	interface NodeStub {
		<S> ArgumentBuilder<S, ?> build(CommandBuildContext commandBuildContext, ClientboundCommandsPacket.NodeBuilder<S> nodeBuilder);

		void write(FriendlyByteBuf friendlyByteBuf);
	}
}
