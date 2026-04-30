package com.spectrewall.stackrefill.systems.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.spectrewall.stackrefill.util.records.SearchQuery;
import com.spectrewall.stackrefill.util.records.SearchResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
		Player player = store.getComponent(ref, Player.getComponentType());
		var item = event.getItemInHand();

		if (item == null || player == null || item.getQuantity() > 1) {
			return;
		}

		String itemId = item.getItem().getId();
		Inventory inventory = player.getInventory();

		ItemStack utilityItem = inventory.getUtilityItem();
		boolean isOffHand = utilityItem != null && utilityItem.getItem().getId().equals(itemId);

		byte activeSlot;
		ItemContainer targetContainer;

		if (isOffHand) {
			activeSlot = inventory.getActiveUtilitySlot();
			targetContainer = inventory.getUtility();
		} else {
			activeSlot = inventory.getActiveHotbarSlot();
			targetContainer = inventory.getHotbar();
		}

		getLogger().at(Level.FINE).log("StackRefill: Searching inventory for more blocks...");

		SearchQuery[] queries = isOffHand
				? new SearchQuery[]{new SearchQuery(targetContainer, itemId, activeSlot),
						new SearchQuery(inventory.getHotbar(), itemId), new SearchQuery(inventory.getStorage(), itemId),
						new SearchQuery(inventory.getBackpack(), itemId),}
				: new SearchQuery[]{new SearchQuery(targetContainer, itemId, activeSlot),
						new SearchQuery(inventory.getStorage(), itemId),
						new SearchQuery(inventory.getBackpack(), itemId),};

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
