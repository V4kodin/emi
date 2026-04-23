package dev.emi.emi.screen.highlight;

import java.util.Set;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.BoM;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;

public final class HighlightContext {
	public final boolean enabled;
	public final Set<Slot> ignoredSlots;
	public final Set<EmiStack> syntheticRequirements;
	public final Set<EmiStack> treeRequirements;

	private HighlightContext(boolean enabled, Set<Slot> ignoredSlots, Set<EmiStack> syntheticRequirements,
			Set<EmiStack> treeRequirements) {
		this.enabled = enabled;
		this.ignoredSlots = ignoredSlots;
		this.syntheticRequirements = syntheticRequirements;
		this.treeRequirements = treeRequirements;
	}

	public static HighlightContext create() {
		if (!BoM.craftingMode || BoM.getTree() == null) {
			return new HighlightContext(false, Set.of(), Set.of(), Set.of());
		}
		HandledScreen<?> handled = EmiApi.getHandledScreen();
		return new HighlightContext(
				true,
				RequirementSetProvider.buildIgnoredSlots(handled),
				RequirementSetProvider.buildSyntheticRequirements(),
				RequirementSetProvider.getTreeRequirementsCached());
	}
}
