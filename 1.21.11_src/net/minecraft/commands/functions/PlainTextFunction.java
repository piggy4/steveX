package net.minecraft.commands.functions;

import com.mojang.brigadier.CommandDispatcher;
import java.util.List;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public record PlainTextFunction<T>(Identifier id, List<UnboundEntryAction<T>> entries) implements CommandFunction<T>, InstantiatedFunction<T> {
	@Override
	public InstantiatedFunction<T> instantiate(@Nullable CompoundTag compoundTag, CommandDispatcher<T> commandDispatcher) throws FunctionInstantiationException {
		return this;
	}
}
