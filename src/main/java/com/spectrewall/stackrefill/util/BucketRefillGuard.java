package com.spectrewall.stackrefill.util;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Suppresses the plugin's own inventory writes so the refill logic does not
 * react to them.
 *
 * <p>
 * Writing a stack with {@code ItemContainer.setItemStackForSlot} enqueues a
 * fresh {@code InventoryChangeEvent} that is dispatched on a later tick.
 * Without this guard, the empty-refill and fill-refill behaviours would see
 * each other's writes (a hand turning filled-&gt;empty or empty-&gt;filled) and
 * bounce back and forth. Because the re-dispatch is deferred, a synchronous
 * flag is not enough; instead we record the expected post-write item id per
 * player+slot and skip the matching event when it finally arrives.
 */
public final class BucketRefillGuard {

	/** How long an expected write stays valid, in milliseconds. */
	private static final long TTL_MS = 1_000L;

	private static final ConcurrentHashMap<Key, Expected> EXPECTED = new ConcurrentHashMap<>();

	private BucketRefillGuard() {
	}

	/**
	 * Records that the plugin just wrote the given item id into the player's slot.
	 */
	public static void markExpected(final UUID playerUuid, final short slot, final String itemId) {
		EXPECTED.put(new Key(playerUuid, slot), new Expected(itemId, System.currentTimeMillis()));
	}

	/**
	 * Returns {@code true} if the change at the player's slot matches a write the
	 * plugin just made (consuming the record), so the caller can ignore it.
	 */
	public static boolean consumeExpected(final UUID playerUuid, final short slot, final String itemId) {
		Key key = new Key(playerUuid, slot);
		Expected expected = EXPECTED.remove(key);

		if (expected == null || System.currentTimeMillis() - expected.timestampMs() > TTL_MS) {
			return false;
		}

		return expected.itemId().equals(itemId);
	}

	private record Key(UUID playerUuid, short slot) {
	}

	private record Expected(String itemId, long timestampMs) {
	}
}
