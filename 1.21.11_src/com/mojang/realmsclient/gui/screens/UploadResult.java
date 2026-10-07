package com.mojang.realmsclient.gui.screens;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record UploadResult(int statusCode, @Nullable String errorMessage) {
	public @Nullable String getSimplifiedErrorMessage() {
		if (this.statusCode >= 200 && this.statusCode < 300) {
			return null;
		} else {
			return this.statusCode == 400 && this.errorMessage != null ? this.errorMessage : String.valueOf(this.statusCode);
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Builder {
		private int statusCode = -1;
		private @Nullable String errorMessage;

		public UploadResult.Builder withStatusCode(int i) {
			this.statusCode = i;
			return this;
		}

		public UploadResult.Builder withErrorMessage(@Nullable String string) {
			this.errorMessage = string;
			return this;
		}

		public UploadResult build() {
			return new UploadResult(this.statusCode, this.errorMessage);
		}
	}
}
