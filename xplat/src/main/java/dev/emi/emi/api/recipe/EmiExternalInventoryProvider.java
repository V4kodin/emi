package dev.emi.emi.api.recipe;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

/**
 * Contributes additional {@link EmiStack}s to {@link EmiPlayerInventory} from
 * sources outside the player's slots — e.g. an open AE2 terminal's ME network,
 * a nearby machine's fluid tank, or other mod-specific storage.
 *
 * <p>Registered via {@code EmiRegistry.addExternalInventoryProvider}. All
 * registered providers are queried whenever EMI builds a player inventory
 * snapshot for recipe tree / craftable calculations.
 *
 * <p>Providers are called on the client thread and should be cheap — prefer
 * reading already-cached screen data over issuing network requests.
 */
@FunctionalInterface
public interface EmiExternalInventoryProvider {

	/**
	 * @param screen The currently open screen, or {@code null} if none.
	 * @param player The local player.
	 * @return Stacks to add to the inventory. Empty list if this provider
	 *         has nothing to contribute for the given context.
	 */
	List<EmiStack> getStacks(@Nullable HandledScreen<?> screen, PlayerEntity player);

	/**
	 * Whether this provider owns a given visible slot. Used to avoid double counting
	 * between generic slot scans and external storage mirrors.
	 */
	default boolean handlesSlot(@Nullable HandledScreen<?> screen, Slot slot) {
		return false;
	}

	/**
	 * Additional stack candidates represented by the currently rendered slot. Used by
	 * storage highlighting when a modded slot visually wraps non-vanilla content.
	 */
	default List<EmiStack> getSlotCandidates(@Nullable HandledScreen<?> screen, Slot slot, ItemStack displayedStack) {
		return List.of();
	}
}
