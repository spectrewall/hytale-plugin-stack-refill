package com.spectrewall.stackrefill.systems.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ActionType;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.spectrewall.stackrefill.util.BucketIds;
import com.spectrewall.stackrefill.util.BucketRefillGuard;
import com.spectrewall.stackrefill.util.records.SearchQuery;
import com.spectrewall.stackrefill.util.records.SearchResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;
import static com.spectrewall.stackrefill.util.InventorySearch.findItemSlot;

/**
 * Keeps a usable bucket in the player's hand after one is used.
 *
 * <p>
 * Using a bucket does not fire {@code PlaceBlockEvent}; the held item is
 * transformed in place, which surfaces as an {@link InventoryChangeEvent}
 * carrying a {@link SlotTransaction}. Two cases are handled, both by swapping
 * in another bucket of the pre-use state from the hotbar, storage or backpack:
 * <ul>
 * <li><b>Emptied</b> ({@code slotBefore} = filled bucket, {@code slotAfter} =
 * its empty counterpart): swap in another filled bucket so the player keeps
 * placing liquid.</li>
 * <li><b>Filled</b> ({@code slotBefore} = empty, {@code slotAfter} = a filled
 * bucket): swap in another empty bucket so the player keeps collecting
 * liquid.</li>
 * </ul>
 *
 * <p>
 * A manual inventory drag/swap is a {@code MoveTransaction}, not a
 * {@link SlotTransaction}, so it never matches. The plugin's own swap writes
 * are suppressed via {@link BucketRefillGuard} to stop the two cases from
 * triggering each other. Buckets are identified generically (see
 * {@link BucketIds}).
 */
public class BucketRefillEventSystem extends EntityEventSystem<EntityStore, InventoryChangeEvent> {

	public BucketRefillEventSystem() {
		super(InventoryChangeEvent.class);
	}

	@Override
	public void handle(final int index, @Nonnull final ArchetypeChunk<EntityStore> archetypeChunk,
			@Nonnull final Store<EntityStore> store, @Nonnull final CommandBuffer<EntityStore> commandBuffer,
			@Nonnull final InventoryChangeEvent event) {
		ItemContainer activeContainer = event.getItemContainer();
		Transaction transaction = event.getTransaction();

		if (activeContainer == null || transaction == null) {
			return;
		}

		// Only an in-place slot transformation can be a bucket used by hand; a
		// manual drag/swap is a MoveTransaction and must be left alone.
		if (!(transaction instanceof SlotTransaction slot)) {
			return;
		}

		// Act only on the active hand slot. This also stops a cascade: the swap
		// below writes into a donor slot, which is never the active slot.
		byte activeSlot = activeSlotOf(event.getInventory());
		if (activeSlot < 0 || slot.getSlot() != activeSlot) {
			return;
		}

		Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
		PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
		if (playerRef == null) {
			return;
		}

		UUID playerUuid = playerRef.getUuid();
		String beforeId = idOrNull(slot.getSlotBefore());
		String afterId = idOrNull(slot.getSlotAfter());

		// Ignore the plugin's own swap writes (their re-dispatched change events).
		if (BucketRefillGuard.consumeExpected(playerUuid, activeSlot, afterId)) {
			return;
		}

		// Emptied a bucket: the hand went from a filled bucket to its empty
		// counterpart. Refill with another filled bucket of the same id.
		if (beforeId != null && BucketIds.isFilledBucket(beforeId) && afterId != null
				&& afterId.equals(BucketIds.emptyIdOf(beforeId))) {
			swapIn(store, ref, activeContainer, activeSlot, playerUuid, beforeId);

			return;
		}

		// Filled a bucket: the hand went from empty to a filled bucket. Refill with
		// another empty bucket so the player can keep collecting.
		if (afterId != null && slot.getAction() == ActionType.SET && beforeId == null
				&& BucketIds.isFilledBucket(afterId)) {
			swapIn(store, ref, activeContainer, activeSlot, playerUuid, BucketIds.emptyIdOf(afterId));
		}
	}

	/**
	 * Finds a stack of {@code wantedId} in the hotbar, storage or backpack and
	 * swaps it into the active hand slot, moving the just-used bucket to the donor
	 * slot.
	 */
	private static void swapIn(final Store<EntityStore> store, final Ref<EntityStore> ref,
			final ItemContainer activeContainer, final byte activeSlot, final UUID playerUuid, final String wantedId) {
		if (wantedId == null) {
			return;
		}

		getLogger().at(Level.FINE).log("StackRefill: Bucket used, looking for a '%s' to swap in...", wantedId);

		InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
		InventoryComponent.Storage storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
		InventoryComponent.Backpack backpack = store.getComponent(ref, InventoryComponent.Backpack.getComponentType());

		List<SearchQuery> queryList = new ArrayList<>();
		// Active container first, ignoring the slot that was just used.
		queryList.add(new SearchQuery(activeContainer, wantedId, activeSlot));

		if (hotbar != null && hotbar.getInventory() != activeContainer) {
			queryList.add(new SearchQuery(hotbar.getInventory(), wantedId));
		}

		if (storage != null) {
			queryList.add(new SearchQuery(storage.getInventory(), wantedId));
		}

		if (backpack != null) {
			queryList.add(new SearchQuery(backpack.getInventory(), wantedId));
		}

		SearchResult result = findItemSlot(queryList.toArray(SearchQuery[]::new));

		if (!result.success()) {
			getLogger().at(Level.FINE).log("StackRefill: No '%s' found to swap in.", wantedId);

			return;
		}

		ItemStack replacement = result.container().getItemStack(result.slot());

		if (replacement == null || !result.container().canRemoveItemStack(replacement)) {
			return;
		}

		// Swap: the replacement goes to the hand, the just-used bucket takes its
		// place. Mark the hand write so its re-dispatched event is ignored.
		ItemStack usedBucket = activeContainer.getItemStack(activeSlot);
		BucketRefillGuard.markExpected(playerUuid, activeSlot, wantedId);
		activeContainer.setItemStackForSlot(activeSlot, replacement);
		result.container().setItemStackForSlot(result.slot(), usedBucket);
		getLogger().at(Level.FINE).log("StackRefill: Bucket swapped in!");
	}

	private static byte activeSlotOf(@Nullable final InventoryComponent inventory) {
		if (inventory instanceof InventoryComponent.Hotbar hotbar) {
			return hotbar.getActiveSlot();
		}

		if (inventory instanceof InventoryComponent.Utility utility) {
			return utility.getActiveSlot();
		}

		return -1;
	}

	@Nullable
	private static String idOrNull(@Nullable final ItemStack stack) {
		return stack == null || stack.isEmpty() ? null : stack.getItem().getId();
	}

	@Nullable
	@Override
	public Query<EntityStore> getQuery() {
		return PlayerRef.getComponentType();
	}
}
