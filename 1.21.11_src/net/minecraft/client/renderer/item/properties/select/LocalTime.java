package net.minecraft.client.renderer.item.properties.select;

import com.ibm.icu.text.DateFormat;
import com.ibm.icu.text.SimpleDateFormat;
import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class LocalTime implements SelectItemModelProperty<String> {
	public static final String ROOT_LOCALE = "";
	private static final long UPDATE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(1L);
	public static final Codec<String> VALUE_CODEC = Codec.STRING;
	private static final Codec<TimeZone> TIME_ZONE_CODEC = VALUE_CODEC.comapFlatMap(string -> {
		TimeZone timeZone = TimeZone.getTimeZone(string);
		return timeZone.equals(TimeZone.UNKNOWN_ZONE) ? DataResult.error(() -> "Unknown timezone: " + string) : DataResult.success(timeZone);
	}, TimeZone::getID);
	private static final MapCodec<LocalTime.Data> DATA_MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				Codec.STRING.fieldOf("pattern").forGetter(data -> data.format),
				Codec.STRING.optionalFieldOf("locale", "").forGetter(data -> data.localeId),
				TIME_ZONE_CODEC.optionalFieldOf("time_zone").forGetter(data -> data.timeZone)
			)
			.apply(instance, LocalTime.Data::new)
	);
	public static final SelectItemModelProperty.Type<LocalTime, String> TYPE = SelectItemModelProperty.Type.create(
		DATA_MAP_CODEC.flatXmap(LocalTime::create, localTime -> DataResult.success(localTime.data)), VALUE_CODEC
	);
	private final LocalTime.Data data;
	private final DateFormat parsedFormat;
	private long nextUpdateTimeMs;
	private String lastResult = "";

	private LocalTime(LocalTime.Data data, DateFormat dateFormat) {
		this.data = data;
		this.parsedFormat = dateFormat;
	}

	public static LocalTime create(String string, String string2, Optional<TimeZone> optional) {
		return create(new LocalTime.Data(string, string2, optional)).getOrThrow(stringx -> new IllegalStateException("Failed to validate format: " + stringx));
	}

	private static DataResult<LocalTime> create(LocalTime.Data data) {
		ULocale uLocale = new ULocale(data.localeId);
		Calendar calendar = data.timeZone.<Calendar>map(timeZone -> Calendar.getInstance(timeZone, uLocale)).orElseGet(() -> Calendar.getInstance(uLocale));
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(data.format, uLocale);
		simpleDateFormat.setCalendar(calendar);

		try {
			simpleDateFormat.format(new Date());
		} catch (Exception exception) {
			return DataResult.error(() -> "Invalid time format '" + simpleDateFormat + "': " + exception.getMessage());
		}

		return DataResult.success(new LocalTime(data, simpleDateFormat));
	}

	public @Nullable String get(
		ItemStack itemStack, @Nullable ClientLevel clientLevel, @Nullable LivingEntity livingEntity, int i, ItemDisplayContext itemDisplayContext
	) {
		long l = Util.getMillis();
		if (l > this.nextUpdateTimeMs) {
			this.lastResult = this.update();
			this.nextUpdateTimeMs = l + UPDATE_INTERVAL_MS;
		}

		return this.lastResult;
	}

	private String update() {
		return this.parsedFormat.format(new Date());
	}

	@Override
	public SelectItemModelProperty.Type<LocalTime, String> type() {
		return TYPE;
	}

	@Override
	public Codec<String> valueCodec() {
		return VALUE_CODEC;
	}

	@Environment(EnvType.CLIENT)
	record Data(String format, String localeId, Optional<TimeZone> timeZone) {
	}
}
