package net.minecraft.advancements;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CacheableFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public record AdvancementRewards(int experience, List<ResourceKey<LootTable>> loot, List<ResourceKey<Recipe<?>>> recipes, Optional<CacheableFunction> function) {
	public static final Codec<AdvancementRewards> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				Codec.INT.optionalFieldOf("experience", 0).forGetter(AdvancementRewards::experience),
				LootTable.KEY_CODEC.listOf().optionalFieldOf("loot", List.of()).forGetter(AdvancementRewards::loot),
				Recipe.KEY_CODEC.listOf().optionalFieldOf("recipes", List.of()).forGetter(AdvancementRewards::recipes),
				CacheableFunction.CODEC.optionalFieldOf("function").forGetter(AdvancementRewards::function)
			)
			.apply(instance, AdvancementRewards::new)
	);
	public static final AdvancementRewards EMPTY = new AdvancementRewards(0, List.of(), List.of(), Optional.empty());

	public void grant(ServerPlayer serverPlayer) {
		serverPlayer.giveExperiencePoints(this.experience);
		ServerLevel serverLevel = serverPlayer.level();
		MinecraftServer minecraftServer = serverLevel.getServer();
		LootParams lootParams = new LootParams.Builder(serverLevel)
			.withParameter(LootContextParams.THIS_ENTITY, serverPlayer)
			.withParameter(LootContextParams.ORIGIN, serverPlayer.position())
			.create(LootContextParamSets.ADVANCEMENT_REWARD);
		boolean bl = false;

		for (ResourceKey<LootTable> resourceKey : this.loot) {
			for (ItemStack itemStack : minecraftServer.reloadableRegistries().getLootTable(resourceKey).getRandomItems(lootParams)) {
				if (serverPlayer.addItem(itemStack)) {
					serverLevel.playSound(
						null,
						serverPlayer.getX(),
						serverPlayer.getY(),
						serverPlayer.getZ(),
						SoundEvents.ITEM_PICKUP,
						SoundSource.PLAYERS,
						0.2F,
						((serverPlayer.getRandom().nextFloat() - serverPlayer.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F
					);
					bl = true;
				} else {
					ItemEntity itemEntity = serverPlayer.drop(itemStack, false);
					if (itemEntity != null) {
						itemEntity.setNoPickUpDelay();
						itemEntity.setTarget(serverPlayer.getUUID());
					}
				}
			}
		}

		if (bl) {
			serverPlayer.containerMenu.broadcastChanges();
		}

		if (!this.recipes.isEmpty()) {
			serverPlayer.awardRecipesByKey(this.recipes);
		}

		this.function
			.flatMap(cacheableFunction -> cacheableFunction.get(minecraftServer.getFunctions()))
			.ifPresent(
				commandFunction -> minecraftServer.getFunctions()
					.execute(
						(CommandFunction<CommandSourceStack>)commandFunction,
						serverPlayer.createCommandSourceStack().withSuppressedOutput().withPermission(LevelBasedPermissionSet.GAMEMASTER)
					)
			);
	}

	public static class Builder {
		private int experience;
		private final ImmutableList.Builder<ResourceKey<LootTable>> loot = ImmutableList.builder();
		private final ImmutableList.Builder<ResourceKey<Recipe<?>>> recipes = ImmutableList.builder();
		private Optional<Identifier> function = Optional.empty();

		public static AdvancementRewards.Builder experience(int i) {
			return new AdvancementRewards.Builder().addExperience(i);
		}

		public AdvancementRewards.Builder addExperience(int i) {
			this.experience += i;
			return this;
		}

		public static AdvancementRewards.Builder loot(ResourceKey<LootTable> resourceKey) {
			return new AdvancementRewards.Builder().addLootTable(resourceKey);
		}

		public AdvancementRewards.Builder addLootTable(ResourceKey<LootTable> resourceKey) {
			this.loot.add(resourceKey);
			return this;
		}

		public static AdvancementRewards.Builder recipe(ResourceKey<Recipe<?>> resourceKey) {
			return new AdvancementRewards.Builder().addRecipe(resourceKey);
		}

		public AdvancementRewards.Builder addRecipe(ResourceKey<Recipe<?>> resourceKey) {
			this.recipes.add(resourceKey);
			return this;
		}

		public static AdvancementRewards.Builder function(Identifier identifier) {
			return new AdvancementRewards.Builder().runs(identifier);
		}

		public AdvancementRewards.Builder runs(Identifier identifier) {
			this.function = Optional.of(identifier);
			return this;
		}

		public AdvancementRewards build() {
			return new AdvancementRewards(this.experience, this.loot.build(), this.recipes.build(), this.function.map(CacheableFunction::new));
		}
	}
}
