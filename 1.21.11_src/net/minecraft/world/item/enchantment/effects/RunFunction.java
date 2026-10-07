package net.minecraft.world.item.enchantment.effects;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public record RunFunction(Identifier function) implements EnchantmentEntityEffect {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final MapCodec<RunFunction> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(Identifier.CODEC.fieldOf("function").forGetter(RunFunction::function)).apply(instance, RunFunction::new)
	);

	@Override
	public void apply(ServerLevel serverLevel, int i, EnchantedItemInUse enchantedItemInUse, Entity entity, Vec3 vec3) {
		MinecraftServer minecraftServer = serverLevel.getServer();
		ServerFunctionManager serverFunctionManager = minecraftServer.getFunctions();
		Optional<CommandFunction<CommandSourceStack>> optional = serverFunctionManager.get(this.function);
		if (optional.isPresent()) {
			CommandSourceStack commandSourceStack = minecraftServer.createCommandSourceStack()
				.withPermission(LevelBasedPermissionSet.GAMEMASTER)
				.withSuppressedOutput()
				.withEntity(entity)
				.withLevel(serverLevel)
				.withPosition(vec3)
				.withRotation(entity.getRotationVector());
			serverFunctionManager.execute(optional.get(), commandSourceStack);
		} else {
			LOGGER.error("Enchantment run_function effect failed for non-existent function {}", this.function);
		}
	}

	@Override
	public MapCodec<RunFunction> codec() {
		return CODEC;
	}
}
