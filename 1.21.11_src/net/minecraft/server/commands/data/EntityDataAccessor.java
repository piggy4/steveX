package net.minecraft.server.commands.data;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.advancements.criterion.NbtPredicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.TagValueInput;
import org.slf4j.Logger;

public class EntityDataAccessor implements DataAccessor {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final SimpleCommandExceptionType ERROR_NO_PLAYERS = new SimpleCommandExceptionType(Component.translatable("commands.data.entity.invalid"));
	public static final Function<String, DataCommands.DataProvider> PROVIDER = string -> new DataCommands.DataProvider() {
		@Override
		public DataAccessor access(CommandContext<CommandSourceStack> commandContext) throws CommandSyntaxException {
			return new EntityDataAccessor(EntityArgument.getEntity(commandContext, string));
		}

		@Override
		public ArgumentBuilder<CommandSourceStack, ?> wrap(
			ArgumentBuilder<CommandSourceStack, ?> argumentBuilder, Function<ArgumentBuilder<CommandSourceStack, ?>, ArgumentBuilder<CommandSourceStack, ?>> function
		) {
			return argumentBuilder.then(Commands.literal("entity").then(function.apply(Commands.argument(string, EntityArgument.entity()))));
		}
	};
	private final Entity entity;

	public EntityDataAccessor(Entity entity) {
		this.entity = entity;
	}

	@Override
	public void setData(CompoundTag compoundTag) throws CommandSyntaxException {
		if (this.entity instanceof Player) {
			throw ERROR_NO_PLAYERS.create();
		}

		UUID uUID = this.entity.getUUID();

		try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(this.entity.problemPath(), LOGGER)) {
			this.entity.load(TagValueInput.create(scopedCollector, this.entity.registryAccess(), compoundTag));
			this.entity.setUUID(uUID);
		}
	}

	@Override
	public CompoundTag getData() {
		return NbtPredicate.getEntityTagToCompare(this.entity);
	}

	@Override
	public Component getModifiedSuccess() {
		return Component.translatable("commands.data.entity.modified", this.entity.getDisplayName());
	}

	@Override
	public Component getPrintSuccess(Tag tag) {
		return Component.translatable("commands.data.entity.query", this.entity.getDisplayName(), NbtUtils.toPrettyComponent(tag));
	}

	@Override
	public Component getPrintSuccess(NbtPathArgument.NbtPath nbtPath, double d, int i) {
		return Component.translatable("commands.data.entity.get", nbtPath.asString(), this.entity.getDisplayName(), String.format(Locale.ROOT, "%.2f", d), i);
	}
}
