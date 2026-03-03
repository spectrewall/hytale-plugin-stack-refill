package com.spectrewall.stackrefill.util.records;

import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import java.util.Objects;

public record SearchResult(boolean success, ItemContainer container, short slot) {

	public SearchResult {
		if (success) {
			Objects.requireNonNull(container, "container cannot be null when success is true");

			if (slot < 0) {
				throw new IllegalArgumentException("slot must be >= 0 when success is true");
			}
		} else {
			if (container != null) {
				throw new IllegalArgumentException("container must be null when success is false");
			}

			if (slot != -1) {
				throw new IllegalArgumentException("slot must be -1 when success is false");
			}
		}
	}

	/**
	 * Return a result indicating that the item was not found.
	 */
	public static SearchResult notFound() {
		return new SearchResult(false, null, (short) -1);
	}

	/**
	 * Return a result indicating that the item was found in the given container and
	 * slot.
	 */
	public static SearchResult found(ItemContainer container, short slot) {
		return new SearchResult(true, container, slot);
	}
}
