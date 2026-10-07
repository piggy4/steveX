package net.minecraft.stats;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookRemovePacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookSettingsPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.slf4j.Logger;

public class ServerRecipeBook extends RecipeBook {
	public static final String RECIPE_BOOK_TAG = "recipeBook";
	private static final Logger LOGGER = LogUtils.getLogger();
	private final ServerRecipeBook.DisplayResolver displayResolver;
	@VisibleForTesting
	protected final Set<ResourceKey<Recipe<?>>> known = Sets.newIdentityHashSet();
	@VisibleForTesting
	protected final Set<ResourceKey<Recipe<?>>> highlight = Sets.newIdentityHashSet();

	public ServerRecipeBook(ServerRecipeBook.DisplayResolver displayResolver) {
		this.displayResolver = displayResolver;
	}

	public void add(ResourceKey<Recipe<?>> resourceKey) {
		this.known.add(resourceKey);
	}

	public boolean contains(ResourceKey<Recipe<?>> resourceKey) {
		return this.known.contains(resourceKey);
	}

	public void remove(ResourceKey<Recipe<?>> resourceKey) {
		this.known.remove(resourceKey);
		this.highlight.remove(resourceKey);
	}

	public void removeHighlight(ResourceKey<Recipe<?>> resourceKey) {
		this.highlight.remove(resourceKey);
	}

	private void addHighlight(ResourceKey<Recipe<?>> resourceKey) {
		this.highlight.add(resourceKey);
	}

	public int addRecipes(Collection<RecipeHolder<?>> collection, ServerPlayer serverPlayer) {
		List<ClientboundRecipeBookAddPacket.Entry> list = new ArrayList<>();

		for (RecipeHolder<?> recipeHolder : collection) {
			ResourceKey<Recipe<?>> resourceKey = recipeHolder.id();
			if (!this.known.contains(resourceKey) && !recipeHolder.value().isSpecial()) {
				this.add(resourceKey);
				this.addHighlight(resourceKey);
				this.displayResolver
					.displaysForRecipe(
						resourceKey, recipeDisplayEntry -> list.add(new ClientboundRecipeBookAddPacket.Entry(recipeDisplayEntry, recipeHolder.value().showNotification(), true))
					);
				CriteriaTriggers.RECIPE_UNLOCKED.trigger(serverPlayer, recipeHolder);
			}
		}

		if (!list.isEmpty()) {
			serverPlayer.connection.send(new ClientboundRecipeBookAddPacket(list, false));
		}

		return list.size();
	}

	public int removeRecipes(Collection<RecipeHolder<?>> collection, ServerPlayer serverPlayer) {
		List<RecipeDisplayId> list = Lists.newArrayList();

		for (RecipeHolder<?> recipeHolder : collection) {
			ResourceKey<Recipe<?>> resourceKey = recipeHolder.id();
			if (this.known.contains(resourceKey)) {
				this.remove(resourceKey);
				this.displayResolver.displaysForRecipe(resourceKey, recipeDisplayEntry -> list.add(recipeDisplayEntry.id()));
			}
		}

		if (!list.isEmpty()) {
			serverPlayer.connection.send(new ClientboundRecipeBookRemovePacket(list));
		}

		return list.size();
	}

	private void loadRecipes(List<ResourceKey<Recipe<?>>> list, Consumer<ResourceKey<Recipe<?>>> consumer, Predicate<ResourceKey<Recipe<?>>> predicate) {
		for (ResourceKey<Recipe<?>> resourceKey : list) {
			if (!predicate.test(resourceKey)) {
				LOGGER.error("Tried to load unrecognized recipe: {} removed now.", resourceKey);
			} else {
				consumer.accept(resourceKey);
			}
		}
	}

	public void sendInitialRecipeBook(ServerPlayer serverPlayer) {
		serverPlayer.connection.send(new ClientboundRecipeBookSettingsPacket(this.getBookSettings().copy()));
		List<ClientboundRecipeBookAddPacket.Entry> list = new ArrayList<>(this.known.size());

		for (ResourceKey<Recipe<?>> resourceKey : this.known) {
			this.displayResolver
				.displaysForRecipe(
					resourceKey, recipeDisplayEntry -> list.add(new ClientboundRecipeBookAddPacket.Entry(recipeDisplayEntry, false, this.highlight.contains(resourceKey)))
				);
		}

		serverPlayer.connection.send(new ClientboundRecipeBookAddPacket(list, true));
	}

	public void copyOverData(ServerRecipeBook serverRecipeBook) {
		this.apply(serverRecipeBook.pack());
	}

	public ServerRecipeBook.Packed pack() {
		return new ServerRecipeBook.Packed(this.bookSettings.copy(), List.copyOf(this.known), List.copyOf(this.highlight));
	}

	private void apply(ServerRecipeBook.Packed packed) {
		this.known.clear();
		this.highlight.clear();
		this.bookSettings.replaceFrom(packed.settings);
		this.known.addAll(packed.known);
		this.highlight.addAll(packed.highlight);
	}

	public void loadUntrusted(ServerRecipeBook.Packed packed, Predicate<ResourceKey<Recipe<?>>> predicate) {
		this.bookSettings.replaceFrom(packed.settings);
		this.loadRecipes(packed.known, this.known::add, predicate);
		this.loadRecipes(packed.highlight, this.highlight::add, predicate);
	}

	@FunctionalInterface
	public interface DisplayResolver {
		void displaysForRecipe(ResourceKey<Recipe<?>> resourceKey, Consumer<RecipeDisplayEntry> consumer);
	}

	public record Packed(RecipeBookSettings settings, List<ResourceKey<Recipe<?>>> known, List<ResourceKey<Recipe<?>>> highlight) {
		public static final Codec<ServerRecipeBook.Packed> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					RecipeBookSettings.MAP_CODEC.forGetter(ServerRecipeBook.Packed::settings),
					Recipe.KEY_CODEC.listOf().fieldOf("recipes").forGetter(ServerRecipeBook.Packed::known),
					Recipe.KEY_CODEC.listOf().fieldOf("toBeDisplayed").forGetter(ServerRecipeBook.Packed::highlight)
				)
				.apply(instance, ServerRecipeBook.Packed::new)
		);
	}
}
