package dev.emi.emi.platform.forge.compat;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

@EmiEntrypoint
public class Ae2EmiPlugin implements EmiPlugin {

	@Override
	public void register(EmiRegistry registry) {
		Ae2InventoryProvider.tryRegister(registry::addExternalInventoryProvider);
	}
}
