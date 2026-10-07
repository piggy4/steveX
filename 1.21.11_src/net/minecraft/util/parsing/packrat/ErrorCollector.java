package net.minecraft.util.parsing.packrat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public interface ErrorCollector<S> {
	void store(int i, SuggestionSupplier<S> suggestionSupplier, Object object);

	default void store(int i, Object object) {
		this.store(i, SuggestionSupplier.empty(), object);
	}

	void finish(int i);

	class LongestOnly<S> implements ErrorCollector<S> {
		private ErrorCollector.LongestOnly.@Nullable MutableErrorEntry<S>[] entries = new ErrorCollector.LongestOnly.MutableErrorEntry[16];
		private int nextErrorEntry;
		private int lastCursor = -1;

		private void discardErrorsFromShorterParse(int i) {
			if (i > this.lastCursor) {
				this.lastCursor = i;
				this.nextErrorEntry = 0;
			}
		}

		@Override
		public void finish(int i) {
			this.discardErrorsFromShorterParse(i);
		}

		@Override
		public void store(int i, SuggestionSupplier<S> suggestionSupplier, Object object) {
			this.discardErrorsFromShorterParse(i);
			if (i == this.lastCursor) {
				this.addErrorEntry(suggestionSupplier, object);
			}
		}

		private void addErrorEntry(SuggestionSupplier<S> suggestionSupplier, Object object) {
			int i = this.entries.length;
			if (this.nextErrorEntry >= i) {
				int j = Util.growByHalf(i, this.nextErrorEntry + 1);
				ErrorCollector.LongestOnly.MutableErrorEntry<S>[] mutableErrorEntrys = new ErrorCollector.LongestOnly.MutableErrorEntry[j];
				System.arraycopy(this.entries, 0, mutableErrorEntrys, 0, i);
				this.entries = mutableErrorEntrys;
			}

			int j = this.nextErrorEntry++;
			ErrorCollector.LongestOnly.MutableErrorEntry<S> mutableErrorEntry = this.entries[j];
			if (mutableErrorEntry == null) {
				mutableErrorEntry = new ErrorCollector.LongestOnly.MutableErrorEntry<>();
				this.entries[j] = mutableErrorEntry;
			}

			mutableErrorEntry.suggestions = suggestionSupplier;
			mutableErrorEntry.reason = object;
		}

		public List<ErrorEntry<S>> entries() {
			int i = this.nextErrorEntry;
			if (i == 0) {
				return List.of();
			}

			List<ErrorEntry<S>> list = new ArrayList<>(i);

			for (int j = 0; j < i; j++) {
				ErrorCollector.LongestOnly.MutableErrorEntry<S> mutableErrorEntry = this.entries[j];
				list.add(new ErrorEntry<>(this.lastCursor, mutableErrorEntry.suggestions, mutableErrorEntry.reason));
			}

			return list;
		}

		public int cursor() {
			return this.lastCursor;
		}

		static class MutableErrorEntry<S> {
			SuggestionSupplier<S> suggestions = SuggestionSupplier.empty();
			Object reason = "empty";
		}
	}

	class Nop<S> implements ErrorCollector<S> {
		@Override
		public void store(int i, SuggestionSupplier<S> suggestionSupplier, Object object) {
		}

		@Override
		public void finish(int i) {
		}
	}
}
