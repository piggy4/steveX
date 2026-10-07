package net.minecraft.network.chat.contents.data;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.advancements.criterion.NbtPredicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public record EntityDataSource(String selectorPattern, @Nullable EntitySelector compiledSelector) implements DataSource {
	public static final MapCodec<EntityDataSource> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(Codec.STRING.fieldOf("entity").forGetter(EntityDataSource::selectorPattern)).apply(instance, EntityDataSource::new)
	);

	public EntityDataSource(String string) {
		this(string, compileSelector(string));
	}

	private static @Nullable EntitySelector compileSelector(String string) {
		try {
			EntitySelectorParser entitySelectorParser = new EntitySelectorParser(new StringReader(string), true);
			return entitySelectorParser.parse();
		} catch (CommandSyntaxException commandSyntaxException) {
			return null;
		}
	}

	@Override
	public Stream<CompoundTag> getData(CommandSourceStack commandSourceStack) throws CommandSyntaxException {
		if (this.compiledSelector != null) {
			List<? extends Entity> list = this.compiledSelector.findEntities(commandSourceStack);
			return list.stream().map(NbtPredicate::getEntityTagToCompare);
		} else {
			return Stream.empty();
		}
	}

	@Override
	public MapCodec<EntityDataSource> codec() {
		return MAP_CODEC;
	}

	@Override
	public String toString() {
		return "entity=" + this.selectorPattern;
	}

	@Override
	public boolean equals(Object object) {
		return this == object ? true : object instanceof EntityDataSource entityDataSource && this.selectorPattern.equals(entityDataSource.selectorPattern);
	}

	@Override
	public int hashCode() {
		return this.selectorPattern.hashCode();
	}
}
