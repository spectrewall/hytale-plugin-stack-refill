package com.spectrewall.stackrefill.util;

import javax.annotation.Nullable;

/**
 * Helpers for recognising bucket item ids and mapping a filled bucket to its
 * empty counterpart.
 *
 * <p>
 * Hytale models a filled bucket as a stateful item: the id of a filled bucket
 * looks like {@code *<base>_State_Filled_<liquid>} (the leading {@code *} is
 * the engine's stateful-item marker), while the empty bucket is just
 * {@code <base>}. For example {@code *Container_Bucket_State_Filled_Water}
 * empties into {@code Container_Bucket}. Deriving the empty id from the filled
 * id keeps the feature generic: it works for water, lava, milk and any modded
 * bucket that follows the same stateful convention, instead of hard-coding a
 * single id.
 */
public final class BucketIds {

	private static final String STATE_FILLED_MARKER = "_State_Filled_";

	private BucketIds() {
	}

	/**
	 * Whether the given item id is a filled bucket (a stateful bucket carrying a
	 * liquid).
	 */
	public static boolean isFilledBucket(@Nullable final String itemId) {
		return itemId != null && itemId.contains(STATE_FILLED_MARKER) && containsBucket(itemId);
	}

	/**
	 * Returns the empty-bucket id that the given filled-bucket id collapses into
	 * once the liquid is placed, or {@code null} if the id is not a filled bucket.
	 */
	@Nullable
	public static String emptyIdOf(@Nullable final String filledBucketId) {
		if (!isFilledBucket(filledBucketId)) {
			return null;
		}

		String base = filledBucketId.startsWith("*") ? filledBucketId.substring(1) : filledBucketId;
		int markerIndex = base.indexOf(STATE_FILLED_MARKER);

		return markerIndex < 0 ? base : base.substring(0, markerIndex);
	}

	private static boolean containsBucket(final String itemId) {
		return itemId.toLowerCase().contains("bucket");
	}
}
