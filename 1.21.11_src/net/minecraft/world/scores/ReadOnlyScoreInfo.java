package net.minecraft.world.scores;

import java.util.Objects;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.NumberFormat;
import org.jspecify.annotations.Nullable;

public interface ReadOnlyScoreInfo {
	int value();

	boolean isLocked();

	@Nullable NumberFormat numberFormat();

	default MutableComponent formatValue(NumberFormat numberFormat) {
		return Objects.requireNonNullElse(this.numberFormat(), numberFormat).format(this.value());
	}

	static MutableComponent safeFormatValue(@Nullable ReadOnlyScoreInfo readOnlyScoreInfo, NumberFormat numberFormat) {
		return readOnlyScoreInfo != null ? readOnlyScoreInfo.formatValue(numberFormat) : numberFormat.format(0);
	}
}
