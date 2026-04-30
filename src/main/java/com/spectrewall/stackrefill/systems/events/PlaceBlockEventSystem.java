package com.spectrewall.stackrefill.systems.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.spectrewall.stackrefill.util.records.SearchQuery;
import com.spectrewall.stackrefill.util.records.SearchResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;
import static com.spectrewall.stackrefill.util.InventorySearch.findItemSlot;

public class PlaceBlockEventSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

	public PlaceBlockEventSystem() {
		super(PlaceBlockEvent.class);
	}

	@Override
	public void handle(final int index, @Nonnull final ArchetypeChunk<EntityStore> archetypeChunk,
			@Nonnull final Store<EntityStore> store, @Nonnull final CommandBuffer<EntityStore> commandBuffer,
			@Nonnull final PlaceBlockEvent event) {
		Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
		var item = event.getItemInHand();

		if (item == null || item.getQuantity() > 1) {
			return;
		}

		String itemId = item.getItem().getId();

		InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
		InventoryComponent.Utility utility = store.getComponent(ref, InventoryComponent.Utility.getComponentType());
		InventoryComponent.Storage storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
		InventoryComponent.Backpack backpack = store.getComponent(ref, InventoryComponent.Backpack.getComponentType());

		if (hotbar == null) {
			return;
		}

		ItemStack mainHandItem = hotbar.getActiveItem();
		boolean mainHandMatches = mainHandItem != null && mainHandItem.getItem().getId().equals(itemId);

		// If main hand matches but has quantity > 1, the placed block must have come
		// from the off-hand,
		// since this event only fires when the placed item has quantity == 1.
		boolean isMainHand = mainHandMatches && mainHandItem.getQuantity() == 1;

		byte activeSlot;
		ItemContainer targetContainer;

		if (isMainHand) {
			activeSlot = hotbar.getActiveSlot();
			targetContainer = hotbar.getInventory();
		} else if (utility != null) {
			activeSlot = utility.getActiveSlot();
			targetContainer = utility.getInventory();
		} else {
			return;
		}

		getLogger().at(Level.FINE).log("StackRefill: Searching inventory for more blocks...");

		List<SearchQuery> queryList = new ArrayList<>();
		queryList.add(new SearchQuery(targetContainer, itemId, activeSlot));

		if (!isMainHand) {
			queryList.add(new SearchQuery(hotbar.getInventory(), itemId));
		}

		if (storage != null) {
			queryList.add(new SearchQuery(storage.getInventory(), itemId));
		}

		if (backpack != null) {
			queryList.add(new SearchQuery(backpack.getInventory(), itemId));
		}

		SearchQuery[] queries = queryList.toArray(SearchQuery[]::new);

		SearchResult result = findItemSlot(queries);

		if (!result.success()) {
			getLogger().at(Level.FINE).log("StackRefill: None found on inventory!");

			return;
		}

		getLogger().at(Level.FINE).log("StackRefill: More of the same block found!");
		ItemStack stack = result.container().getItemStack(result.slot());

		if (stack == null || !result.container().canRemoveItemStack(stack)) {
			return;
		}

		// The +1 is necessary because the block is subtracted only after this code runs
		ItemStack newStack = stack.withQuantity(stack.getQuantity() + 1);

		targetContainer.setItemStackForSlot(activeSlot, newStack);
		result.container().removeItemStackFromSlot(result.slot());
		getLogger().at(Level.FINE).log("StackRefill: Stack Refilled!");
	}

	@Nullable
	@Override
	public Query<EntityStore> getQuery() {
		return PlayerRef.getComponentType();
	}
}
