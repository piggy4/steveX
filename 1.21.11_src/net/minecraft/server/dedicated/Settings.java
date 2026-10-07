package net.minecraft.server.dedicated;

import com.google.common.base.MoreObjects;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.core.RegistryAccess;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class Settings<T extends Settings<T>> {
	private static final Logger LOGGER = LogUtils.getLogger();
	protected final Properties properties;

	public Settings(Properties properties) {
		this.properties = properties;
	}

	public static Properties loadFromFile(Path path) {
		try {
			try (InputStream inputStream = Files.newInputStream(path)) {
				CharsetDecoder charsetDecoder = StandardCharsets.UTF_8
					.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT);
				Properties properties = new Properties();
				properties.load(new InputStreamReader(inputStream, charsetDecoder));
				return properties;
			} catch (CharacterCodingException characterCodingException) {
				LOGGER.info("Failed to load properties as UTF-8 from file {}, trying ISO_8859_1", path);

				try (Reader reader = Files.newBufferedReader(path, StandardCharsets.ISO_8859_1)) {
					Properties properties = new Properties();
					properties.load(reader);
					return properties;
				}
			}
		} catch (IOException iOException) {
			LOGGER.error("Failed to load properties from file: {}", path, iOException);
			return new Properties();
		}
	}

	public void store(Path path) {
		try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			this.properties.store(writer, "Minecraft server properties");
		} catch (IOException iOException) {
			LOGGER.error("Failed to store properties to file: {}", path);
		}
	}

	private static <V extends Number> Function<String, @Nullable V> wrapNumberDeserializer(Function<String, V> function) {
		return string -> {
			try {
				return function.apply(string);
			} catch (NumberFormatException numberFormatException) {
				return null;
			}
		};
	}

	protected static <V> Function<String, @Nullable V> dispatchNumberOrString(IntFunction<@Nullable V> intFunction, Function<String, @Nullable V> function) {
		return string -> {
			try {
				return intFunction.apply(Integer.parseInt(string));
			} catch (NumberFormatException numberFormatException) {
				return function.apply(string);
			}
		};
	}

	private @Nullable String getStringRaw(String string) {
		return (String)this.properties.get(string);
	}

	protected <V> @Nullable V getLegacy(String string, Function<String, V> function) {
		String string2 = this.getStringRaw(string);
		if (string2 == null) {
			return null;
		}

		this.properties.remove(string);
		return function.apply(string2);
	}

	protected <V> V get(String string, Function<String, @Nullable V> function, Function<V, String> function2, V object) {
		String string2 = this.getStringRaw(string);
		V object2 = MoreObjects.firstNonNull(string2 != null ? function.apply(string2) : null, object);
		this.properties.put(string, function2.apply(object2));
		return object2;
	}

	protected <V> Settings<T>.MutableValue<V> getMutable(String string, Function<String, @Nullable V> function, Function<V, String> function2, V object) {
		String string2 = this.getStringRaw(string);
		V object2 = MoreObjects.firstNonNull(string2 != null ? function.apply(string2) : null, object);
		this.properties.put(string, function2.apply(object2));
		return new Settings.MutableValue<>(string, object2, function2);
	}

	protected <V> V get(String string, Function<String, @Nullable V> function, UnaryOperator<V> unaryOperator, Function<V, String> function2, V object) {
		return this.get(string, stringx -> {
			V objectx = function.apply(stringx);
			return objectx != null ? unaryOperator.apply(objectx) : null;
		}, function2, object);
	}

	protected <V> V get(String string, Function<String, V> function, V object) {
		return this.get(string, function, Objects::toString, object);
	}

	protected <V> Settings<T>.MutableValue<V> getMutable(String string, Function<String, V> function, V object) {
		return this.getMutable(string, function, Objects::toString, object);
	}

	protected String get(String string, String string2) {
		return this.get(string, Function.identity(), Function.identity(), string2);
	}

	protected @Nullable String getLegacyString(String string) {
		return this.getLegacy(string, Function.identity());
	}

	protected int get(String string, int i) {
		return this.get(string, wrapNumberDeserializer(Integer::parseInt), Integer.valueOf(i));
	}

	protected Settings<T>.MutableValue<Integer> getMutable(String string, int i) {
		return this.getMutable(string, wrapNumberDeserializer(Integer::parseInt), i);
	}

	protected Settings<T>.MutableValue<String> getMutable(String string, String string2) {
		return this.getMutable(string, String::new, string2);
	}

	protected int get(String string, UnaryOperator<Integer> unaryOperator, int i) {
		return this.get(string, wrapNumberDeserializer(Integer::parseInt), unaryOperator, Objects::toString, i);
	}

	protected long get(String string, long l) {
		return this.get(string, wrapNumberDeserializer(Long::parseLong), l);
	}

	protected boolean get(String string, boolean bl) {
		return this.get(string, Boolean::valueOf, bl);
	}

	protected Settings<T>.MutableValue<Boolean> getMutable(String string, boolean bl) {
		return this.getMutable(string, Boolean::valueOf, bl);
	}

	protected @Nullable Boolean getLegacyBoolean(String string) {
		return this.getLegacy(string, Boolean::valueOf);
	}

	protected Properties cloneProperties() {
		Properties properties = new Properties();
		properties.putAll(this.properties);
		return properties;
	}

	protected abstract T reload(RegistryAccess registryAccess, Properties properties);

	public class MutableValue<V> implements Supplier<V> {
		private final String key;
		private final V value;
		private final Function<V, String> serializer;

		MutableValue(final String string, final V object, final Function<V, String> function) {
			this.key = string;
			this.value = object;
			this.serializer = function;
		}

		@Override
		public V get() {
			return this.value;
		}

		public T update(RegistryAccess registryAccess, V object) {
			Properties properties = Settings.this.cloneProperties();
			properties.put(this.key, this.serializer.apply(object));
			return Settings.this.reload(registryAccess, properties);
		}
	}
}
