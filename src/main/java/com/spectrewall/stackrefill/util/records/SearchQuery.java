package com.spectrewall.stackrefill.util.records;

import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import java.util.Objects;

public record SearchQuery(ItemContainer container, String itemId, short slotToIgnore) {

	public SearchQuery {
		Objects.requireNonNull(container, "container cannot be null");
		Objects.requireNonNull(itemId, "itemId cannot be null");
	}

	public SearchQuery(ItemContainer container, String itemId) {
		this(container, itemId, (short) -1);
	}

	/**
	 * Indicates whether the given slot should be ignored when searching for items.
	 */
	public boolean shouldIgnore(short slot) {
		return slotToIgnore != -1 && slot == slotToIgnore;
	}
}
