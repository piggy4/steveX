package net.minecraft.client.gui.components.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DebugEntryLookingAtBlock implements DebugScreenEntry {
	private static final Identifier GROUP = Identifier.withDefaultNamespace("looking_at_block");

	@Override
	public void display(DebugScreenDisplayer debugScreenDisplayer, @Nullable Level level, @Nullable LevelChunk levelChunk, @Nullable LevelChunk levelChunk2) {
		Entity entity = Minecraft.getInstance().getCameraEntity();
		Level level2 = SharedConstants.DEBUG_SHOW_SERVER_DEBUG_VALUES ? level : Minecraft.getInstance().level;
		if (entity != null && level2 != null) {
			HitResult hitResult = entity.pick(20.0, 0.0F, false);
			List<String> list = new ArrayList<>();
			if (hitResult.getType() == HitResult.Type.BLOCK) {
				BlockPos blockPos = ((BlockHitResult)hitResult).getBlockPos();
				BlockState blockState = level2.getBlockState(blockPos);
				list.add(ChatFormatting.UNDERLINE + "Targeted Block: " + blockPos.getX() + ", " + blockPos.getY() + ", " + blockPos.getZ());
				list.add(String.valueOf(BuiltInRegistries.BLOCK.getKey(blockState.getBlock())));

				for (Entry<Property<?>, Comparable<?>> entry : blockState.getValues().entrySet()) {
					list.add(this.getPropertyValueString(entry));
				}

				blockState.getTags().map(tagKey -> "#" + tagKey.location()).forEach(list::add);
			}

			debugScreenDisplayer.addToGroup(GROUP, list);
		}
	}

	private String getPropertyValueString(Entry<Property<?>, Comparable<?>> entry) {
		Property<?> property = entry.getKey();
		Comparable<?> comparable = entry.getValue();
		String string = Util.getPropertyName(property, comparable);
		if (Boolean.TRUE.equals(comparable)) {
			string = ChatFormatting.GREEN + string;
		} else if (Boolean.FALSE.equals(comparable)) {
			string = ChatFormatting.RED + string;
		}

		return property.getName() + ": " + string;
	}
}
