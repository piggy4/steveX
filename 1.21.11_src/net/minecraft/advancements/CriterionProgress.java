package net.minecraft.advancements;

import java.time.Instant;
import net.minecraft.network.FriendlyByteBuf;
import org.jspecify.annotations.Nullable;

public class CriterionProgress {
	private @Nullable Instant obtained;

	public CriterionProgress() {
	}

	public CriterionProgress(Instant instant) {
		this.obtained = instant;
	}

	public boolean isDone() {
		return this.obtained != null;
	}

	public void grant() {
		this.obtained = Instant.now();
	}

	public void revoke() {
		this.obtained = null;
	}

	public @Nullable Instant getObtained() {
		return this.obtained;
	}

	@Override
	public String toString() {
		return "CriterionProgress{obtained=" + (this.obtained == null ? "false" : this.obtained) + "}";
	}

	public void serializeToNetwork(FriendlyByteBuf friendlyByteBuf) {
		friendlyByteBuf.writeNullable(this.obtained, FriendlyByteBuf::writeInstant);
	}

	public static CriterionProgress fromNetwork(FriendlyByteBuf friendlyByteBuf) {
		CriterionProgress criterionProgress = new CriterionProgress();
		criterionProgress.obtained = friendlyByteBuf.readNullable(FriendlyByteBuf::readInstant);
		return criterionProgress;
	}
}
