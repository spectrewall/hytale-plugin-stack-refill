package com.spectrewall.stackrefill;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.spectrewall.stackrefill.systems.events.PlaceBlockEventSystem;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Main plugin class.
 *
 * @author YourName
 * @version 1.0.0
 */
@SuppressWarnings("unused")
public class StackRefill extends JavaPlugin {

	private static StackRefill instance;

	/**
	 * Constructor - Called when plugin is loaded.
	 */
	public StackRefill(@Nonnull JavaPluginInit init) {
		super(init);
		instance = this;
	}

	/**
	 * Get plugin instance.
	 */
	public static StackRefill getInstance() {
		return instance;
	}

	/**
	 * Called when plugin is set up.
	 */
	@Override
	protected void setup() {
		getLogger().at(Level.INFO).log("[StackRefill] Plugin setup!");

		super.setup();
		this.getEntityStoreRegistry().registerSystem(new PlaceBlockEventSystem());
	}
}
