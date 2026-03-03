package com.spectrewall.stackrefill.util;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.spectrewall.stackrefill.util.records.SearchQuery;
import com.spectrewall.stackrefill.util.records.SearchResult;

public class InventorySearch {
	/**
	 * Searches the given container for an item with the specified ID and returns
	 * the slot index if found, or -1 if not found.
	 */
	public static SearchResult findItemSlot(SearchQuery query) {
		ItemContainer container = query.container();
		String itemId = query.itemId();

		for (short slot = 0; slot < container.getCapacity(); slot++) {
			if (query.shouldIgnore(slot)) {
				continue;
			}

			ItemStack stack = container.getItemStack(slot);

			if (stack == null || stack.isEmpty()) {
				continue;
			}

			if (stack.getItem().getId().equals(itemId)) {
				return SearchResult.found(container, slot);
			}
		}

		return SearchResult.notFound();
	}

	/**
	 * Searches the given containers for an item with the specified ID and returns
	 * the slot index if found, or -1 if not found.
	 */
	public static SearchResult findItemSlot(SearchQuery[] queries) {
		for (SearchQuery query : queries) {
			SearchResult result = findItemSlot(query);

			if (result.success()) {
				return result;
			}
		}

		return SearchResult.notFound();
	}
}
