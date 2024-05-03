package gregtech.common.mui.widget.workbench;

import gregtech.api.mui.GTGuiTextures;
import gregtech.client.utils.RenderUtil;
import gregtech.common.metatileentities.storage.CraftingRecipeMemory;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.GuiScreenWrapper;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ItemSlot;
import org.jetbrains.annotations.NotNull;

public class RecipeMemorySlot extends Widget<RecipeMemorySlot> implements Interactable {

    private final CraftingRecipeMemory memory;
    private final int index;

    public RecipeMemorySlot(CraftingRecipeMemory memory, int index) {
        this.memory = memory;
        this.index = index;
    }

    @Override
    public void onInit() {
        size(ItemSlot.SIZE);
        background(GTGuiTextures.SLOT);
    }

    @Override
    public void draw(GuiContext context, WidgetTheme widgetTheme) {
        GuiScreenWrapper guiScreen = getScreen().getScreenWrapper();
        ItemStack itemstack = this.memory.getRecipeOutputAtIndex(this.index);
        if (itemstack.isEmpty()) return;

        guiScreen.setZ(100f);
        guiScreen.getItemRenderer().zLevel = 100.0F;

        int cachedCount = itemstack.getCount();
        itemstack.setCount(1); // required to not render the amount overlay
        RenderUtil.renderItemGUI(itemstack, 1, 1);
        itemstack.setCount(cachedCount);

        guiScreen.getItemRenderer().zLevel = 0.0F;
        guiScreen.setZ(0f);

        if (this.memory.getRecipeAtIndex(this.index).isRecipeLocked())
            GTGuiTextures.RECIPE_LOCK.draw(context, 10, 1, 8, 8, widgetTheme);
    }

    @NotNull
    @Override
    public Result onMousePressed(int mouseButton) {
        var recipe = memory.getRecipeAtIndex(this.index);
        if (recipe == null)
            return Result.IGNORE;

        var data = MouseData.create(mouseButton);
        this.memory.syncToServer(2, buffer -> {
            buffer.writeByte(this.index);
            data.writeToPacket(buffer);
        });

        if (data.shift && data.mouseButton == 0) {
            recipe.setRecipeLocked(!recipe.isRecipeLocked());
        }

        return Result.ACCEPT;
    }
}