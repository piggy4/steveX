package net.minecraft.commands.arguments.selector;

import com.google.common.primitives.Doubles;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.command.v2.FabricEntitySelectorReader;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSetSupplier;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Mth;
import net.minecraft.util.ToFloatFunction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.command.v2.FabricEntitySelectorReader} injected by mod fabric-command-api-v2</p>
 */
public class EntitySelectorParser implements FabricEntitySelectorReader {
	public static final char SYNTAX_SELECTOR_START = '@';
	private static final char SYNTAX_OPTIONS_START = '[';
	private static final char SYNTAX_OPTIONS_END = ']';
	public static final char SYNTAX_OPTIONS_KEY_VALUE_SEPARATOR = '=';
	private static final char SYNTAX_OPTIONS_SEPARATOR = ',';
	public static final char SYNTAX_NOT = '!';
	public static final char SYNTAX_TAG = '#';
	private static final char SELECTOR_NEAREST_PLAYER = 'p';
	private static final char SELECTOR_ALL_PLAYERS = 'a';
	private static final char SELECTOR_RANDOM_PLAYERS = 'r';
	private static final char SELECTOR_CURRENT_ENTITY = 's';
	private static final char SELECTOR_ALL_ENTITIES = 'e';
	private static final char SELECTOR_NEAREST_ENTITY = 'n';
	public static final SimpleCommandExceptionType ERROR_INVALID_NAME_OR_UUID = new SimpleCommandExceptionType(Component.translatable("argument.entity.invalid"));
	public static final DynamicCommandExceptionType ERROR_UNKNOWN_SELECTOR_TYPE = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("argument.entity.selector.unknown", object)
	);
	public static final SimpleCommandExceptionType ERROR_SELECTORS_NOT_ALLOWED = new SimpleCommandExceptionType(
		Component.translatable("argument.entity.selector.not_allowed")
	);
	public static final SimpleCommandExceptionType ERROR_MISSING_SELECTOR_TYPE = new SimpleCommandExceptionType(
		Component.translatable("argument.entity.selector.missing")
	);
	public static final SimpleCommandExceptionType ERROR_EXPECTED_END_OF_OPTIONS = new SimpleCommandExceptionType(
		Component.translatable("argument.entity.options.unterminated")
	);
	public static final DynamicCommandExceptionType ERROR_EXPECTED_OPTION_VALUE = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("argument.entity.options.valueless", object)
	);
	public static final BiConsumer<Vec3, List<? extends Entity>> ORDER_NEAREST = (vec3, list) -> list.sort(
		(entity, entity2) -> Doubles.compare(entity.distanceToSqr(vec3), entity2.distanceToSqr(vec3))
	);
	public static final BiConsumer<Vec3, List<? extends Entity>> ORDER_FURTHEST = (vec3, list) -> list.sort(
		(entity, entity2) -> Doubles.compare(entity2.distanceToSqr(vec3), entity.distanceToSqr(vec3))
	);
	public static final BiConsumer<Vec3, List<? extends Entity>> ORDER_RANDOM = (vec3, list) -> Collections.shuffle(list);
	public static final BiFunction<SuggestionsBuilder, Consumer<SuggestionsBuilder>, CompletableFuture<Suggestions>> SUGGEST_NOTHING = (suggestionsBuilder, consumer) -> suggestionsBuilder.buildFuture();
	private final StringReader reader;
	private final boolean allowSelectors;
	private int maxResults;
	private boolean includesEntities;
	private boolean worldLimited;
	private MinMaxBounds.@Nullable Doubles distance;
	private MinMaxBounds.@Nullable Ints level;
	private @Nullable Double x;
	private @Nullable Double y;
	private @Nullable Double z;
	private @Nullable Double deltaX;
	private @Nullable Double deltaY;
	private @Nullable Double deltaZ;
	private MinMaxBounds.@Nullable FloatDegrees rotX;
	private MinMaxBounds.@Nullable FloatDegrees rotY;
	private final List<Predicate<Entity>> predicates = new ArrayList<>();
	private BiConsumer<Vec3, List<? extends Entity>> order = EntitySelector.ORDER_ARBITRARY;
	private boolean currentEntity;
	private @Nullable String playerName;
	private int startPosition;
	private @Nullable UUID entityUUID;
	private BiFunction<SuggestionsBuilder, Consumer<SuggestionsBuilder>, CompletableFuture<Suggestions>> suggestions = SUGGEST_NOTHING;
	private boolean hasNameEquals;
	private boolean hasNameNotEquals;
	private boolean isLimited;
	private boolean isSorted;
	private boolean hasGamemodeEquals;
	private boolean hasGamemodeNotEquals;
	private boolean hasTeamEquals;
	private boolean hasTeamNotEquals;
	private @Nullable EntityType<?> type;
	private boolean typeInverse;
	private boolean hasScores;
	private boolean hasAdvancements;
	private boolean usesSelectors;

	public EntitySelectorParser(StringReader stringReader, boolean bl) {
		this.reader = stringReader;
		this.allowSelectors = bl;
	}

	public static <S> boolean allowSelectors(S object) {
		return object instanceof PermissionSetSupplier permissionSetSupplier
			&& permissionSetSupplier.permissions().hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS);
	}

	@Deprecated
	public static boolean allowSelectors(PermissionSetSupplier permissionSetSupplier) {
		return permissionSetSupplier.permissions().hasPermission(Permissions.COMMANDS_ENTITY_SELECTORS);
	}

	public EntitySelector getSelector() {
		AABB aABB;
		if (this.deltaX == null && this.deltaY == null && this.deltaZ == null) {
			if (this.distance != null && this.distance.max().isPresent()) {
				double d = (Double)this.distance.max().get();
				aABB = new AABB(-d, -d, -d, d + 1.0, d + 1.0, d + 1.0);
			} else {
				aABB = null;
			}
		} else {
			aABB = this.createAabb(this.deltaX == null ? 0.0 : this.deltaX, this.deltaY == null ? 0.0 : this.deltaY, this.deltaZ == null ? 0.0 : this.deltaZ);
		}

		Function<Vec3, Vec3> function;
		if (this.x == null && this.y == null && this.z == null) {
			function = vec3 -> vec3;
		} else {
			function = vec3 -> new Vec3(this.x == null ? vec3.x : this.x, this.y == null ? vec3.y : this.y, this.z == null ? vec3.z : this.z);
		}

		return new EntitySelector(
			this.maxResults,
			this.includesEntities,
			this.worldLimited,
			List.copyOf(this.predicates),
			this.distance,
			function,
			aABB,
			this.order,
			this.currentEntity,
			this.playerName,
			this.entityUUID,
			this.type,
			this.usesSelectors
		);
	}

	private AABB createAabb(double d, double e, double f) {
		boolean bl = d < 0.0;
		boolean bl2 = e < 0.0;
		boolean bl3 = f < 0.0;
		double g = bl ? d : 0.0;
		double h = bl2 ? e : 0.0;
		double i = bl3 ? f : 0.0;
		double j = (bl ? 0.0 : d) + 1.0;
		double k = (bl2 ? 0.0 : e) + 1.0;
		double l = (bl3 ? 0.0 : f) + 1.0;
		return new AABB(g, h, i, j, k, l);
	}

	private void finalizePredicates() {
		if (this.rotX != null) {
			this.predicates.add(this.createRotationPredicate(this.rotX, Entity::getXRot));
		}

		if (this.rotY != null) {
			this.predicates.add(this.createRotationPredicate(this.rotY, Entity::getYRot));
		}

		if (this.level != null) {
			this.predicates.add(entity -> entity instanceof ServerPlayer serverPlayer && this.level.matches(serverPlayer.experienceLevel));
		}
	}

	private Predicate<Entity> createRotationPredicate(MinMaxBounds.FloatDegrees floatDegrees, ToFloatFunction<Entity> toFloatFunction) {
		float f = Mth.wrapDegrees(floatDegrees.min().orElse(0.0F));
		float g = Mth.wrapDegrees(floatDegrees.max().orElse(359.0F));
		return entity -> {
			float h = Mth.wrapDegrees(toFloatFunction.applyAsFloat(entity));
			return f > g ? h >= f || h <= g : h >= f && h <= g;
		};
	}

	protected void parseSelector() throws CommandSyntaxException {
		this.usesSelectors = true;
		this.suggestions = this::suggestSelector;
		if (!this.reader.canRead()) {
			throw ERROR_MISSING_SELECTOR_TYPE.createWithContext(this.reader);
		}

		int i = this.reader.getCursor();
		char c = this.reader.read();

		if (switch (c) {
			case 'a' -> {
				this.maxResults = Integer.MAX_VALUE;
				this.includesEntities = false;
				this.order = EntitySelector.ORDER_ARBITRARY;
				this.limitToType(EntityType.PLAYER);
				yield false;
			}
			default -> {
				this.reader.setCursor(i);
				throw ERROR_UNKNOWN_SELECTOR_TYPE.createWithContext(this.reader, "@" + c);
			}
			case 'e' -> {
				this.maxResults = Integer.MAX_VALUE;
				this.includesEntities = true;
				this.order = EntitySelector.ORDER_ARBITRARY;
				yield true;
			}
			case 'n' -> {
				this.maxResults = 1;
				this.includesEntities = true;
				this.order = ORDER_NEAREST;
				yield true;
			}
			case 'p' -> {
				this.maxResults = 1;
				this.includesEntities = false;
				this.order = ORDER_NEAREST;
				this.limitToType(EntityType.PLAYER);
				yield false;
			}
			case 'r' -> {
				this.maxResults = 1;
				this.includesEntities = false;
				this.order = ORDER_RANDOM;
				this.limitToType(EntityType.PLAYER);
				yield false;
			}
			case 's' -> {
				this.maxResults = 1;
				this.includesEntities = true;
				this.currentEntity = true;
				yield false;
			}
		}) {
			this.predicates.add(Entity::isAlive);
		}

		this.suggestions = this::suggestOpenOptions;
		if (this.reader.canRead() && this.reader.peek() == '[') {
			this.reader.skip();
			this.suggestions = this::suggestOptionsKeyOrClose;
			this.parseOptions();
		}
	}

	protected void parseNameOrUUID() throws CommandSyntaxException {
		if (this.reader.canRead()) {
			this.suggestions = this::suggestName;
		}

		int i = this.reader.getCursor();
		String string = this.reader.readString();

		try {
			this.entityUUID = UUID.fromString(string);
			this.includesEntities = true;
		} catch (IllegalArgumentException illegalArgumentException) {
			if (string.isEmpty() || string.length() > 16) {
				this.reader.setCursor(i);
				throw ERROR_INVALID_NAME_OR_UUID.createWithContext(this.reader);
			}

			this.includesEntities = false;
			this.playerName = string;
		}

		this.maxResults = 1;
	}

	protected void parseOptions() throws CommandSyntaxException {
		this.suggestions = this::suggestOptionsKey;
		this.reader.skipWhitespace();

		while (this.reader.canRead() && this.reader.peek() != ']') {
			this.reader.skipWhitespace();
			int i = this.reader.getCursor();
			String string = this.reader.readString();
			EntitySelectorOptions.Modifier modifier = EntitySelectorOptions.get(this, string, i);
			this.reader.skipWhitespace();
			if (!this.reader.canRead() || this.reader.peek() != '=') {
				this.reader.setCursor(i);
				throw ERROR_EXPECTED_OPTION_VALUE.createWithContext(this.reader, string);
			}

			this.reader.skip();
			this.reader.skipWhitespace();
			this.suggestions = SUGGEST_NOTHING;
			modifier.handle(this);
			this.reader.skipWhitespace();
			this.suggestions = this::suggestOptionsNextOrClose;
			if (this.reader.canRead()) {
				if (this.reader.peek() != ',') {
					if (this.reader.peek() != ']') {
						throw ERROR_EXPECTED_END_OF_OPTIONS.createWithContext(this.reader);
					}
					break;
				}

				this.reader.skip();
				this.suggestions = this::suggestOptionsKey;
			}
		}

		if (this.reader.canRead()) {
			this.reader.skip();
			this.suggestions = SUGGEST_NOTHING;
		} else {
			throw ERROR_EXPECTED_END_OF_OPTIONS.createWithContext(this.reader);
		}
	}

	public boolean shouldInvertValue() {
		this.reader.skipWhitespace();
		if (this.reader.canRead() && this.reader.peek() == '!') {
			this.reader.skip();
			this.reader.skipWhitespace();
			return true;
		} else {
			return false;
		}
	}

	public boolean isTag() {
		this.reader.skipWhitespace();
		if (this.reader.canRead() && this.reader.peek() == '#') {
			this.reader.skip();
			this.reader.skipWhitespace();
			return true;
		} else {
			return false;
		}
	}

	public StringReader getReader() {
		return this.reader;
	}

	public void addPredicate(Predicate<Entity> predicate) {
		this.predicates.add(predicate);
	}

	public void setWorldLimited() {
		this.worldLimited = true;
	}

	public MinMaxBounds.@Nullable Doubles getDistance() {
		return this.distance;
	}

	public void setDistance(MinMaxBounds.Doubles doubles) {
		this.distance = doubles;
	}

	public MinMaxBounds.@Nullable Ints getLevel() {
		return this.level;
	}

	public void setLevel(MinMaxBounds.Ints ints) {
		this.level = ints;
	}

	public MinMaxBounds.@Nullable FloatDegrees getRotX() {
		return this.rotX;
	}

	public void setRotX(MinMaxBounds.FloatDegrees floatDegrees) {
		this.rotX = floatDegrees;
	}

	public MinMaxBounds.@Nullable FloatDegrees getRotY() {
		return this.rotY;
	}

	public void setRotY(MinMaxBounds.FloatDegrees floatDegrees) {
		this.rotY = floatDegrees;
	}

	public @Nullable Double getX() {
		return this.x;
	}

	public @Nullable Double getY() {
		return this.y;
	}

	public @Nullable Double getZ() {
		return this.z;
	}

	public void setX(double d) {
		this.x = d;
	}

	public void setY(double d) {
		this.y = d;
	}

	public void setZ(double d) {
		this.z = d;
	}

	public void setDeltaX(double d) {
		this.deltaX = d;
	}

	public void setDeltaY(double d) {
		this.deltaY = d;
	}

	public void setDeltaZ(double d) {
		this.deltaZ = d;
	}

	public @Nullable Double getDeltaX() {
		return this.deltaX;
	}

	public @Nullable Double getDeltaY() {
		return this.deltaY;
	}

	public @Nullable Double getDeltaZ() {
		return this.deltaZ;
	}

	public void setMaxResults(int i) {
		this.maxResults = i;
	}

	public void setIncludesEntities(boolean bl) {
		this.includesEntities = bl;
	}

	public BiConsumer<Vec3, List<? extends Entity>> getOrder() {
		return this.order;
	}

	public void setOrder(BiConsumer<Vec3, List<? extends Entity>> biConsumer) {
		this.order = biConsumer;
	}

	public EntitySelector parse() throws CommandSyntaxException {
		this.startPosition = this.reader.getCursor();
		this.suggestions = this::suggestNameOrSelector;
		if (this.reader.canRead() && this.reader.peek() == '@') {
			if (!this.allowSelectors) {
				throw ERROR_SELECTORS_NOT_ALLOWED.createWithContext(this.reader);
			}

			this.reader.skip();
			this.parseSelector();
		} else {
			this.parseNameOrUUID();
		}

		this.finalizePredicates();
		return this.getSelector();
	}

	private static void fillSelectorSuggestions(SuggestionsBuilder suggestionsBuilder) {
		suggestionsBuilder.suggest("@p", Component.translatable("argument.entity.selector.nearestPlayer"));
		suggestionsBuilder.suggest("@a", Component.translatable("argument.entity.selector.allPlayers"));
		suggestionsBuilder.suggest("@r", Component.translatable("argument.entity.selector.randomPlayer"));
		suggestionsBuilder.suggest("@s", Component.translatable("argument.entity.selector.self"));
		suggestionsBuilder.suggest("@e", Component.translatable("argument.entity.selector.allEntities"));
		suggestionsBuilder.suggest("@n", Component.translatable("argument.entity.selector.nearestEntity"));
	}

	private CompletableFuture<Suggestions> suggestNameOrSelector(SuggestionsBuilder suggestionsBuilder, Consumer<SuggestionsBuilder> consumer) {
		consumer.accept(suggestionsBuilder);
		if (this.allowSelectors) {
			fillSelectorSuggestions(suggestionsBuilder);
		}

		return suggestionsBuilder.buildFuture();
	}

	private CompletableFuture<Suggestions> suggestName(SuggestionsBuilder suggestionsBuilder, Consumer<SuggestionsBuilder> consumer) {
		SuggestionsBuilder suggestionsBuilder2 = suggestionsBuilder.createOffset(this.startPosition);
		consumer.accept(suggestionsBuilder2);
		return suggestionsBuilder.add(suggestionsBuilder2).buildFuture();
	}

	private CompletableFuture<Suggestions> suggestSelector(SuggestionsBuilder suggestionsBuilder, Consumer<SuggestionsBuilder> consumer) {
		SuggestionsBuilder suggestionsBuilder2 = suggestionsBuilder.createOffset(suggestionsBuilder.getStart() - 1);
		fillSelectorSuggestions(suggestionsBuilder2);
		suggestionsBuilder.add(suggestionsBuilder2);
		return suggestionsBuilder.buildFuture();
	}

	private CompletableFuture<Suggestions> suggestOpenOptions(SuggestionsBuilder suggestionsBuilder, Consumer<SuggestionsBuilder> consumer) {
		suggestionsBuilder.suggest(String.valueOf('['));
		return suggestionsBuilder.buildFuture();
	}

	private CompletableFuture<Suggestions> suggestOptionsKeyOrClose(SuggestionsBuilder suggestionsBuilder, Consumer<SuggestionsBuilder> consumer) {
		suggestionsBuilder.suggest(String.valueOf(']'));
		EntitySelectorOptions.suggestNames(this, suggestionsBuilder);
		return suggestionsBuilder.buildFuture();
	}

	private CompletableFuture<Suggestions> suggestOptionsKey(SuggestionsBuilder suggestionsBuilder, Consumer<SuggestionsBuilder> consumer) {
		EntitySelectorOptions.suggestNames(this, suggestionsBuilder);
		return suggestionsBuilder.buildFuture();
	}

	private CompletableFuture<Suggestions> suggestOptionsNextOrClose(SuggestionsBuilder suggestionsBuilder, Consumer<SuggestionsBuilder> consumer) {
		suggestionsBuilder.suggest(String.valueOf(','));
		suggestionsBuilder.suggest(String.valueOf(']'));
		return suggestionsBuilder.buildFuture();
	}

	private CompletableFuture<Suggestions> suggestEquals(SuggestionsBuilder suggestionsBuilder, Consumer<SuggestionsBuilder> consumer) {
		suggestionsBuilder.suggest(String.valueOf('='));
		return suggestionsBuilder.buildFuture();
	}

	public boolean isCurrentEntity() {
		return this.currentEntity;
	}

	public void setSuggestions(BiFunction<SuggestionsBuilder, Consumer<SuggestionsBuilder>, CompletableFuture<Suggestions>> biFunction) {
		this.suggestions = biFunction;
	}

	public CompletableFuture<Suggestions> fillSuggestions(SuggestionsBuilder suggestionsBuilder, Consumer<SuggestionsBuilder> consumer) {
		return this.suggestions.apply(suggestionsBuilder.createOffset(this.reader.getCursor()), consumer);
	}

	public boolean hasNameEquals() {
		return this.hasNameEquals;
	}

	public void setHasNameEquals(boolean bl) {
		this.hasNameEquals = bl;
	}

	public boolean hasNameNotEquals() {
		return this.hasNameNotEquals;
	}

	public void setHasNameNotEquals(boolean bl) {
		this.hasNameNotEquals = bl;
	}

	public boolean isLimited() {
		return this.isLimited;
	}

	public void setLimited(boolean bl) {
		this.isLimited = bl;
	}

	public boolean isSorted() {
		return this.isSorted;
	}

	public void setSorted(boolean bl) {
		this.isSorted = bl;
	}

	public boolean hasGamemodeEquals() {
		return this.hasGamemodeEquals;
	}

	public void setHasGamemodeEquals(boolean bl) {
		this.hasGamemodeEquals = bl;
	}

	public boolean hasGamemodeNotEquals() {
		return this.hasGamemodeNotEquals;
	}

	public void setHasGamemodeNotEquals(boolean bl) {
		this.hasGamemodeNotEquals = bl;
	}

	public boolean hasTeamEquals() {
		return this.hasTeamEquals;
	}

	public void setHasTeamEquals(boolean bl) {
		this.hasTeamEquals = bl;
	}

	public boolean hasTeamNotEquals() {
		return this.hasTeamNotEquals;
	}

	public void setHasTeamNotEquals(boolean bl) {
		this.hasTeamNotEquals = bl;
	}

	public void limitToType(EntityType<?> entityType) {
		this.type = entityType;
	}

	public void setTypeLimitedInversely() {
		this.typeInverse = true;
	}

	public boolean isTypeLimited() {
		return this.type != null;
	}

	public boolean isTypeLimitedInversely() {
		return this.typeInverse;
	}

	public boolean hasScores() {
		return this.hasScores;
	}

	public void setHasScores(boolean bl) {
		this.hasScores = bl;
	}

	public boolean hasAdvancements() {
		return this.hasAdvancements;
	}

	public void setHasAdvancements(boolean bl) {
		this.hasAdvancements = bl;
	}
}
