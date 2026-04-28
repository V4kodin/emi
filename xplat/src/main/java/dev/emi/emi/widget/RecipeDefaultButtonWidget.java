package dev.emi.emi.widget;

import java.util.List;

import com.google.common.collect.Lists;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.bom.BoM.DefaultStatus;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.HelpLevel;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiHistory;
import dev.emi.emi.screen.RecipeScreen;
import dev.emi.emi.screen.tooltip.IngredientTooltipComponent;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;

public class RecipeDefaultButtonWidget extends RecipeButtonWidget {
	private static final int SLOT_SIZE = 18;
	private static final int MAX_COL = 6;
	private static final int GAP = 4;

	private boolean showPicker = false;

	public RecipeDefaultButtonWidget(int x, int y, EmiRecipe recipe) {
		super(x, y, 48, 0, recipe);
	}

	private List<EmiStack> outputs() {
		return recipe.getOutputs();
	}

	private boolean multiOutput() {
		return outputs().size() > 1;
	}

	private int numCols() {
		return (outputs().size() + MAX_COL - 1) / MAX_COL;
	}

	private int numRows() {
		return Math.min(outputs().size(), MAX_COL);
	}

	private int pickerW() {
		return numCols() * SLOT_SIZE;
	}

	private int pickerH() {
		return numRows() * SLOT_SIZE;
	}

	private int pickerX() {
		return x + 12 + GAP;
	}

	private int pickerY() {
		return Math.max(0, y + 6 - pickerH() / 2);
	}

	private boolean inPicker(int mx, int my) {
		int py = pickerY();
		// Extend hover zone leftward to button's right edge to cover the gap
		return mx >= x + 12 && mx < pickerX() + pickerW() + 2
				&& my >= py - 2 && my < py + pickerH() + 2;
	}

	private EmiStack outputAt(int mx, int my) {
		List<EmiStack> outs = outputs();
		int px = pickerX(), py = pickerY();
		for (int i = 0; i < outs.size(); i++) {
			int col = i / MAX_COL, row = i % MAX_COL;
			int ix = px + col * SLOT_SIZE, iy = py + row * SLOT_SIZE;
			if (mx >= ix && mx < ix + 16 && my >= iy && my < iy + 16) {
				return outs.get(i);
			}
		}
		return null;
	}

	@Override
	public Bounds getBounds() {
		if (showPicker && multiOutput()) {
			int py = Math.min(pickerY() - 2, y);
			int bottom = Math.max(pickerY() + pickerH() + 2, y + 12);
			int right = pickerX() + pickerW() + 2;
			return new Bounds(x, py, right - x, bottom - py);
		}
		return new Bounds(x, y, 12, 12);
	}

	@Override
	public int getTextureOffset(int mouseX, int mouseY) {
		boolean hovered = mouseX >= x && mouseX < x + 12 && mouseY >= y && mouseY < y + 12;
		int offset = hovered ? 12 : 0;
		offset += switch (BoM.getRecipeStatus(recipe)) {
			case EMPTY -> 0;
			case PARTIAL -> 60;
			case FULL -> 36;
		};
		return offset;
	}

	@Override
	public void render(DrawContext raw, int mouseX, int mouseY, float delta) {
		EmiDrawContext context = EmiDrawContext.wrap(raw);
		context.resetColor();
		context.drawTexture(EmiRenderHelper.BUTTONS, x, y, 12, 12, u, v + getTextureOffset(mouseX, mouseY), 12, 12, 256, 256);

		boolean mainHovered = mouseX >= x && mouseX < x + 12 && mouseY >= y && mouseY < y + 12;
		showPicker = (mainHovered || (showPicker && inPicker(mouseX, mouseY))) && multiOutput();

		if (!showPicker) {
			return;
		}

		List<EmiStack> outs = outputs();
		int px = pickerX(), py = pickerY();

		context.fill(px - 2, py - 2, pickerW() + 4, pickerH() + 4, 0xAA000000);

		for (int i = 0; i < outs.size(); i++) {
			EmiStack output = outs.get(i);
			int col = i / MAX_COL, row = i % MAX_COL;
			int ix = px + col * SLOT_SIZE, iy = py + row * SLOT_SIZE;
			boolean active = BoM.getRecipe(output) == recipe;
			boolean hovered = mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16;
			int border = active ? (hovered ? 0xFF5a9060 : 0xFF3a6040) : (hovered ? 0xFF505050 : 0xFF303030);
			context.fill(ix - 1, iy - 1, 18, 18, border);
			context.fill(ix, iy, 16, 16, 0xFF111111);
			output.render(raw, ix, iy, delta, EmiIngredient.RENDER_ICON);
		}
	}

	@Override
	public List<TooltipComponent> getTooltip(int mouseX, int mouseY) {
		if (showPicker) {
			EmiStack hovered = outputAt(mouseX, mouseY);
			if (hovered != null) {
				List<TooltipComponent> list = Lists.newArrayList();
				list.addAll(hovered.getTooltip());
				boolean active = BoM.getRecipe(hovered) == recipe;
				list.add(TooltipComponent.of(EmiPort.ordered(EmiPort.translatable(
						active ? "tooltip.emi.unset_default" : "tooltip.emi.set_default"))));
				return list;
			}
		}

		List<TooltipComponent> list = Lists.newArrayList();
		switch (BoM.getRecipeStatus(recipe)) {
			case PARTIAL:
				List<EmiStack> active = Lists.newArrayList();
				for (EmiStack s : outputs()) {
					if (BoM.getRecipe(s) == recipe) active.add(s);
				}
				if (!active.isEmpty()) {
					list.add(TooltipComponent.of(EmiPort.ordered(EmiPort.translatable("tooltip.emi.set_default"))));
					list.add(TooltipComponent.of(EmiPort.ordered(EmiPort.translatable("tooltip.emi.current_defaults"))));
					list.add(new IngredientTooltipComponent(active));
					break;
				}
			case EMPTY:
				list.add(TooltipComponent.of(EmiPort.ordered(EmiPort.translatable("tooltip.emi.set_default"))));
				break;
			case FULL:
				list.add(TooltipComponent.of(EmiPort.ordered(EmiPort.translatable("tooltip.emi.unset_default"))));
				break;
		}
		if (multiOutput() && EmiConfig.helpLevel.has(HelpLevel.NORMAL) && EmiConfig.defaultStack.isBound()) {
			list.add(TooltipComponent.of(EmiPort.ordered(EmiPort.translatable("tooltip.emi.set_default_stack", EmiConfig.defaultStack.getBindText()))));
		}
		return list;
	}

	@Override
	public boolean mouseClicked(int mouseX, int mouseY, int button) {
		if (showPicker && button == 0) {
			EmiStack hovered = outputAt(mouseX, mouseY);
			if (hovered != null) {
				if (BoM.isDefaultRecipe(hovered, recipe)) {
					BoM.removeRecipe(hovered, recipe);
				} else {
					BoM.addRecipe(hovered, recipe);
				}
				playButtonSound();
				return true;
			}
		}

		if (mouseX >= x && mouseX < x + 12 && mouseY >= y && mouseY < y + 12) {
			if (BoM.getRecipeStatus(recipe) == DefaultStatus.FULL) {
				BoM.removeRecipe(recipe);
			} else {
				BoM.addRecipe(recipe);
			}
			playButtonSound();
			if (RecipeScreen.resolve != null) {
				EmiHistory.pop();
			}
			return true;
		}
		return false;
	}
}
