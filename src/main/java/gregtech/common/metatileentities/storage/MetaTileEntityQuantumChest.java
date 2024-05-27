package gregtech.common.metatileentities.storage;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.GuiScreenWrapper;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.value.sync.GuiSyncManager;

import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.layout.Column;

import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IActiveOutputSide;
import gregtech.api.cover.CoverRayTracer;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.IFastRenderMetaTileEntity;
import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTTransferUtils;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.custom.QuantumStorageRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.ColourMultiplier;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

import static gregtech.api.capability.GregtechDataCodes.*;

public class MetaTileEntityQuantumChest extends MetaTileEntity
                                        implements ITieredMetaTileEntity, IActiveOutputSide, IFastRenderMetaTileEntity {

    private final int tier;
    private boolean autoOutputItems;
    private EnumFacing outputFacing;
    private boolean allowInputFromOutputSide = false;
    private static final String NBT_ITEMSTACK = "ItemStack";
    private static final String NBT_PARTIALSTACK = "PartialStack";
    private static final String NBT_ITEMCOUNT = "ItemAmount";
    private static final String IS_VOIDING = "IsVoiding";
    private final QuantumChestItemHandler internalInventory;

//    protected IItemHandler outputItemInventory;
//    private ItemHandlerList combinedInventory;
    protected ItemStack previousStack;
    protected long previousStackSize;

    public MetaTileEntityQuantumChest(ResourceLocation metaTileEntityId, int tier, long maxStoredItems) {
        super(metaTileEntityId);
        this.tier = tier;
        this.internalInventory = new QuantumChestItemHandler(maxStoredItems);
    }

    @Override
    public int getTier() {
        return tier;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityQuantumChest(metaTileEntityId, tier, internalInventory.getSlotLimit());
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        Textures.QUANTUM_STORAGE_RENDERER.renderMachine(renderState, translation,
                ArrayUtils.add(pipeline,
                        new ColourMultiplier(GTUtility.convertRGBtoOpaqueRGBA_CL(getPaintingColorForRendering()))),
                this);
        Textures.QUANTUM_CHEST_OVERLAY.renderSided(EnumFacing.UP, renderState, translation, pipeline);
        if (outputFacing != null) {
            Textures.PIPE_OUT_OVERLAY.renderSided(outputFacing, renderState, translation, pipeline);
            if (isAutoOutputItems()) {
                Textures.ITEM_OUTPUT_OVERLAY.renderSided(outputFacing, renderState, translation, pipeline);
            }
        }
    }

    @Override
    public void renderMetaTileEntity(double x, double y, double z, float partialTicks) {
        QuantumStorageRenderer.renderChestStack(x, y, z, this, internalInventory.virtualItemStack, internalInventory.itemsStoredInside, partialTicks);
    }

    @Override
    public Pair<TextureAtlasSprite, Integer> getParticleTexture() {
        return Pair.of(Textures.VOLTAGE_CASINGS[tier].getParticleSprite(), getPaintingColorForRendering());
    }

    @Override
    public void update() {
        super.update();
        EnumFacing currentOutputFacing = getOutputFacing();
        if (!getWorld().isRemote) {
//            if (itemsStoredInside < maxStoredItems) {
//                ItemStack inputStack = importItems.getStackInSlot(0);
//                ItemStack outputStack = exportItems.getStackInSlot(0);
//                if (!inputStack.isEmpty() &&
//                        (virtualItemStack.isEmpty() || areItemStackIdentical(outputStack, inputStack))) {
//                    GTTransferUtils.moveInventoryItems(importItems, combinedInventory);
//
//                    markDirty();
//                }
//            }
//            if (itemsStoredInside > 0 && !virtualItemStack.isEmpty()) {
//                ItemStack outputStack = exportItems.getStackInSlot(0);
//                int maxStackSize = virtualItemStack.getMaxStackSize();
//                if (outputStack.isEmpty() || (areItemStackIdentical(virtualItemStack, outputStack) &&
//                        outputStack.getCount() < maxStackSize)) {
//                    GTTransferUtils.moveInventoryItems(itemInventory, exportItems);
//
//                    markDirty();
//                }
//            }

            if (isAutoOutputItems()) {
                pushItemsIntoNearbyHandlers(currentOutputFacing);
            }

//            if (internalInventory.isVoiding() && !importItems.getStackInSlot(0).isEmpty()) {
//                importItems.setStackInSlot(0, ItemStack.EMPTY);
//            }

//            if (previousStack == null || !areItemStackIdentical(previousStack, virtualItemStack)) {
//                writeCustomData(UPDATE_ITEM, buf -> buf.writeItemStack(virtualItemStack));
//                previousStack = virtualItemStack;
//            }
//            if (previousStackSize != itemsStoredInside) {
//                writeCustomData(UPDATE_ITEM_COUNT, buf -> buf.writeLong(itemsStoredInside));
//                previousStackSize = itemsStoredInside;
//            }
        }
    }

    protected static boolean areItemStackIdentical(ItemStack first, ItemStack second) {
        return ItemStack.areItemsEqual(first, second) &&
                ItemStack.areItemStackTagsEqual(first, second);
    }

//    protected void addDisplayInformation(List<ITextComponent> textList) {
//        textList.add(new TextComponentTranslation("gregtech.machine.quantum_chest.items_stored"));
//        textList.add(new TextComponentString(String.format("%,d", this.maxCapacity)));
//        ItemStack export = internalInventory.getVirtualItemStack();
//        if (!export.isEmpty()) {
//            textList.add(
//                    new TextComponentString(TextFormattingUtil.formatStringWithNewlines(export.getDisplayName(), 14)));
//        }
//    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.quantum_chest.tooltip"));
        tooltip.add(I18n.format("gregtech.universal.tooltip.item_storage_total", internalInventory.getSlotLimit()));

        NBTTagCompound compound = stack.getTagCompound();
        if (compound != null) {
            String translationKey = null;
            long count = 0;
            int exportCount = 0;
            if (compound.hasKey(NBT_ITEMSTACK)) {
                count = compound.getLong(NBT_ITEMCOUNT);
            }
            if (compound.hasKey(NBT_PARTIALSTACK)) {
                ItemStack tempStack = new ItemStack(compound.getCompoundTag(NBT_PARTIALSTACK));
                translationKey = tempStack.getDisplayName();
                exportCount = tempStack.getCount();
            }
            if (translationKey != null) {
                tooltip.add(I18n.format("gregtech.universal.tooltip.item_stored",
                        I18n.format(translationKey), count, exportCount));
            }
        }
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.auto_output_covers"));
        tooltip.add(I18n.format("gregtech.tool_action.wrench.set_facing"));
        super.addToolUsages(stack, world, tooltip, advanced);
    }

//    @Override
//    protected void initializeInventory() {
//        super.initializeInventory();
//        this.itemInventory = new QuantumChestItemHandler();
//        List<IItemHandler> temp = new ArrayList<>();
//        temp.add(this.exportItems);
//        temp.add(this.itemInventory);
//        this.combinedInventory = new ItemHandlerList(temp);
//        this.outputItemInventory = new ItemHandlerProxy(new GTItemStackHandler(this, 0), combinedInventory);
//    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return new GTItemStackHandler(this, 1) {

            @NotNull
            @Override
            public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                return GTTransferUtils.insertItem(internalInventory, stack, simulate);
            }
        };
    }

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        return new GTItemStackHandler(this, 1);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        NBTTagCompound tagCompound = super.writeToNBT(data);
        data.setInteger("OutputFacing", getOutputFacing().getIndex());
        data.setBoolean("AutoOutputItems", autoOutputItems);
        data.setBoolean("AllowInputFromOutputSide", allowInputFromOutputSide);
        this.internalInventory.writeToNBT(tagCompound);
        return tagCompound;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.outputFacing = EnumFacing.VALUES[data.getInteger("OutputFacing")];
        this.autoOutputItems = data.getBoolean("AutoOutputItems");
        this.allowInputFromOutputSide = data.getBoolean("AllowInputFromOutputSide");
        var export = getExportItems().extractItem(0, 64, false);
        this.internalInventory.readFromNBT(data);
        if (!export.isEmpty()) GTTransferUtils.insertItem(internalInventory, export, false);
    }

    @Override
    public void initFromItemStackData(NBTTagCompound itemStack) {
        super.initFromItemStackData(itemStack);
        if (itemStack.hasKey(NBT_ITEMSTACK, NBT.TAG_COMPOUND)) {
            this.internalInventory.virtualItemStack = new ItemStack(itemStack.getCompoundTag(NBT_ITEMSTACK));
            if (!this.internalInventory.virtualItemStack.isEmpty()) {
                this.internalInventory.itemsStoredInside = itemStack.getLong(NBT_ITEMCOUNT);
            }
        }
        if (itemStack.getBoolean(IS_VOIDING)) {
            setVoiding(true);
        }
        if (itemStack.hasKey(NBT_PARTIALSTACK, NBT.TAG_COMPOUND)) {
            // todo handle export slot stack here
            // exportItems.setStackInSlot(0, new ItemStack(itemStack.getCompoundTag(NBT_PARTIALSTACK)));
        }
    }

    @Override
    public void writeItemStackData(NBTTagCompound itemStack) {
        super.writeItemStackData(itemStack);
        if (!internalInventory.getVirtualStack().isEmpty()) {
            itemStack.setTag(NBT_ITEMSTACK, internalInventory.getVirtualStack().writeToNBT(new NBTTagCompound()));
            itemStack.setLong(NBT_ITEMCOUNT, internalInventory.itemsStoredInside);
        }

        if (internalInventory.isVoiding()) {
            itemStack.setBoolean(IS_VOIDING, true);
        }

        internalInventory.virtualItemStack = ItemStack.EMPTY;
        internalInventory.itemsStoredInside = 0;
//        exportItems.setStackInSlot(0, ItemStack.EMPTY);
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, GuiSyncManager guiSyncManager) {

        return GTGuis.createPanel(this, 176, 186)
                .padding(4)
                .child(new Column()
                        .padding(6)
                        .child(IKey.dynamic(() -> IKey.lang(internalInventory.virtualItemStack.getDisplayName()).get())
                                .alignment(Alignment.Center)
                                .asWidget()
                                .widthRel(0.9f))
                        .child(IKey.dynamic(() -> String.valueOf(internalInventory.itemsStoredInside))
                                .alignment(Alignment.Center)
                                .asWidget()
                                .widthRel(0.9f))
                        .child(new QuantumSlot(this.internalInventory)
                                .size(18)
                                .background(GTGuiTextures.SLOT)
                                .leftRel(0.5f)
                                .topRel(0.5f))
                        .background(GTGuiTextures.DISPLAY)
                        .widthRel(1.0f)
                        .height(100))
                .bindPlayerInventory();
    }

    private static class QuantumSlot extends Widget<QuantumSlot> implements Interactable {
        private final QuantumChestItemHandler handler;

        private QuantumSlot(QuantumChestItemHandler handler) {
            this.handler = handler;
            setSyncHandler(this.handler);
        }

        @Override
        public void draw(GuiContext context, WidgetTheme widgetTheme) {
            GuiScreenWrapper guiScreen = getScreen().getScreenWrapper();
            ItemStack virtualItemStack = this.handler.getVirtualStack();
            if (virtualItemStack.isEmpty()) return;

            guiScreen.setZ(100f);
            guiScreen.getItemRenderer().zLevel = 100.0F;

            int cachedCount = virtualItemStack.getCount();
            virtualItemStack.setCount(1); // required to not render the amount overlay

            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.pushMatrix();
            RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
            renderItem.renderItemAndEffectIntoGUI(virtualItemStack, 1, 1);
            renderItem.renderItemOverlayIntoGUI(Minecraft.getMinecraft().fontRenderer, virtualItemStack, 1, 1, null);
            GlStateManager.popMatrix();
            RenderHelper.enableStandardItemLighting();
            GlStateManager.disableLighting();
            virtualItemStack.setCount(cachedCount);

            guiScreen.getItemRenderer().zLevel = 0.0F;
            guiScreen.setZ(0f);
        }

        @NotNull
        @Override
        public Result onMousePressed(int mouseButton) {
            if (mouseButton == 0) {
                var data = MouseData.create(mouseButton);
                this.handler.syncToServer(1, data::writeToPacket);
                return Result.SUCCESS;
            }
            return Result.IGNORE;
        }
    }

    private static class QuantumModularSlot extends ModularSlot {

        public QuantumModularSlot(IItemHandler itemHandler) {
            super(itemHandler, 0);
        }

        @Override
        public boolean isItemValid(@NotNull ItemStack stack) {
            return getItemHandler().isItemValid(this.slotNumber, stack);
        }

        @Override
        public void putStack(@NotNull ItemStack stack) {
            if (isItemValid(stack)) {
                var ret = getItemHandler().insertItem(this.slotNumber, stack, false);
                stack.setCount(ret.getCount());
            }
        }

        @Override
        public ItemStack getStack() {
            return ItemStack.EMPTY;
        }
    }

//    @Override
//    protected ModularUI createUI(EntityPlayer entityPlayer) {
//        Builder builder = ModularUI.defaultBuilder();
//        builder.image(7, 16, 81, 46, GuiTextures.DISPLAY);
//        builder.widget(new AdvancedTextWidget(11, 20, this::addDisplayInformation, 0xFFFFFF));
//        builder.label(6, 6, getMetaFullName())
//                .widget(new SlotWidget(importItems, 0, 90, 17, true, true)
//                        .setBackgroundTexture(GuiTextures.SLOT, GuiTextures.IN_SLOT_OVERLAY))
//                .widget(new SlotWidget(exportItems, 0, 90, 44, true, false)
//                        .setBackgroundTexture(GuiTextures.SLOT, GuiTextures.OUT_SLOT_OVERLAY))
//                .widget(new ToggleButtonWidget(7, 64, 18, 18,
//                        GuiTextures.BUTTON_ITEM_OUTPUT, this::isAutoOutputItems, this::setAutoOutputItems)
//                                .shouldUseBaseBackground()
//                                .setTooltipText("gregtech.gui.item_auto_output.tooltip"))
//                .widget(new ToggleButtonWidget(25, 64, 18, 18,
//                        GuiTextures.BUTTON_ITEM_VOID, internalInventory::isVoiding, this::setVoiding)
//                                .setTooltipText("gregtech.gui.item_voiding.tooltip")
//                                .shouldUseBaseBackground())
//                .bindPlayerInventory(entityPlayer.inventory);
//
//        return builder.build(getHolder(), entityPlayer);
//    }

    public EnumFacing getOutputFacing() {
        return outputFacing == null ? frontFacing.getOpposite() : outputFacing;
    }

    public void setOutputFacing(EnumFacing outputFacing) {
        this.outputFacing = outputFacing;
        if (!getWorld().isRemote) {
            notifyBlockUpdate();
            writeCustomData(UPDATE_OUTPUT_FACING, buf -> buf.writeByte(outputFacing.getIndex()));
            markDirty();
        }
    }

    @Override
    public boolean onWrenchClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                 CuboidRayTraceResult hitResult) {
        if (!playerIn.isSneaking()) {
            if (getOutputFacing() == facing || getFrontFacing() == facing) {
                return false;
            }
            if (!getWorld().isRemote) {
                setOutputFacing(facing);
            }
            return true;
        }
        return super.onWrenchClick(playerIn, hand, facing, hitResult);
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeByte(getOutputFacing().getIndex());
        buf.writeBoolean(autoOutputItems);
        this.internalInventory.writeInitialSyncData(buf);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.outputFacing = EnumFacing.VALUES[buf.readByte()];
        this.autoOutputItems = buf.readBoolean();
        try {
            this.internalInventory.receiveInitialSyncData(buf);
        } catch (IOException ignored) {
            GTLog.logger.warn("Failed to load item from NBT in a quantum chest at " + this.getPos() +
                    " on initial server/client sync");
        }
    }

    @Override
    public boolean isValidFrontFacing(EnumFacing facing) {
        // use direct outputFacing field instead of getter method because otherwise
        // it will just return SOUTH for null output facing
        return super.isValidFrontFacing(facing) && facing != outputFacing;
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == UPDATE_OUTPUT_FACING) {
            this.outputFacing = EnumFacing.VALUES[buf.readByte()];
            scheduleRenderUpdate();
        } else if (dataId == UPDATE_AUTO_OUTPUT_ITEMS) {
            this.autoOutputItems = buf.readBoolean();
            scheduleRenderUpdate();
        } else if (dataId == UPDATE_ITEM) {
            try {
                this.internalInventory.virtualItemStack = buf.readItemStack();
            } catch (IOException e) {
                GTLog.logger.error("Failed to read item stack in a quantum chest!");
            }
        } else if (dataId == UPDATE_ITEM_COUNT) {
            this.internalInventory.itemsStoredInside = buf.readLong();
        } else if (dataId == UPDATE_IS_VOIDING) {
            setVoiding(buf.readBoolean());
        }
    }

    public void setAutoOutputItems(boolean autoOutputItems) {
        this.autoOutputItems = autoOutputItems;
        if (!getWorld().isRemote) {
            writeCustomData(UPDATE_AUTO_OUTPUT_ITEMS, buf -> buf.writeBoolean(autoOutputItems));
            markDirty();
        }
    }

    protected void setVoiding(boolean isVoiding) {
        this.internalInventory.setVoiding(isVoiding);
        if (!getWorld().isRemote) {
            writeCustomData(UPDATE_IS_VOIDING, buf -> buf.writeBoolean(this.internalInventory.voiding));
            markDirty();
        }
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == GregtechTileCapabilities.CAPABILITY_ACTIVE_OUTPUT_SIDE) {
            if (side == getOutputFacing()) {
                return GregtechTileCapabilities.CAPABILITY_ACTIVE_OUTPUT_SIDE.cast(this);
            }
            return null;
        } else if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(internalInventory);
            // for TOP/Waila
//            if (side == null) return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(combinedInventory);

            // try fix being able to insert through output hole when input on output is disabled
//            IItemHandler itemHandler = (side == getOutputFacing() && !isAllowInputFromOutputSideItems()) ?
//                    outputItemInventory : combinedInventory;
//            if (itemHandler.getSlots() > 0) {
//                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(itemHandler);
//            }
//            return null;
        }
        return super.getCapability(capability, side);
    }

//    public IItemHandler getCombinedInventory() {
//        return this.combinedInventory;
//    }
//
//    public IItemHandler getOutputItemInventory() {
//        return this.outputItemInventory;
//    }

    @Override
    public void setFrontFacing(EnumFacing frontFacing) {
        super.setFrontFacing(frontFacing);
        if (this.outputFacing == null) {
            // set initial output facing as opposite to front
            setOutputFacing(frontFacing.getOpposite());
        }
    }

    public boolean isAutoOutputItems() {
        return autoOutputItems;
    }

    @Override
    public boolean isAutoOutputFluids() {
        return false;
    }

    @Override
    public boolean isAllowInputFromOutputSideItems() {
        return allowInputFromOutputSide;
    }

    @Override
    public boolean isAllowInputFromOutputSideFluids() {
        return false;
    }

    @Override
    public void clearMachineInventory(NonNullList<ItemStack> itemBuffer) {
        clearInventory(itemBuffer, importItems);
    }

    @Override
    public boolean onScrewdriverClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                      CuboidRayTraceResult hitResult) {
        EnumFacing hitFacing = CoverRayTracer.determineGridSideHit(hitResult);
        if (facing == getOutputFacing() || (hitFacing == getOutputFacing() && playerIn.isSneaking())) {
            if (!getWorld().isRemote) {
                if (isAllowInputFromOutputSideItems()) {
                    setAllowInputFromOutputSide(false);
                    playerIn.sendStatusMessage(
                            new TextComponentTranslation("gregtech.machine.basic.input_from_output_side.disallow"),
                            true);
                } else {
                    setAllowInputFromOutputSide(true);
                    playerIn.sendStatusMessage(
                            new TextComponentTranslation("gregtech.machine.basic.input_from_output_side.allow"), true);
                }
            }
            return true;
        }
        return super.onScrewdriverClick(playerIn, hand, facing, hitResult);
    }

    public void setAllowInputFromOutputSide(boolean allowInputFromOutputSide) {
        this.allowInputFromOutputSide = allowInputFromOutputSide;
        if (!getWorld().isRemote) {
            markDirty();
        }
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(getPos());
    }

    private static class QuantumChestItemHandler extends SyncHandler implements IItemHandlerModifiable {

        private final long maxStoredItems;
        
        /** The ItemStack that the Quantum Chest is storing */
        private ItemStack virtualItemStack = ItemStack.EMPTY;
        public long itemsStoredInside = 0L;
        private boolean voiding;

        private QuantumChestItemHandler(long maxStoredItems) {
            this.maxStoredItems = maxStoredItems;
        }

        @Override
        @SuppressWarnings({ "UnstableApiUsage", "OverrideOnly" })
        public void init(String key, GuiSyncManager syncManager) {
            super.init(key, syncManager);
            var modularSlot = new QuantumModularSlot(this)
                    .singletonSlotGroup(-100);
            syncManager.registerSlotGroup(modularSlot.getSlotGroup());
            syncManager.getContainer().registerSlot(modularSlot);
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            ItemStack itemStack = this.virtualItemStack;
            long itemsStored = this.itemsStoredInside;

            if (itemStack.isEmpty() || itemsStored == 0L) {
                return ItemStack.EMPTY;
            }

            ItemStack resultStack = itemStack.copy();
            resultStack.setCount((int) itemsStored);
            return resultStack;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            this.virtualItemStack = stack;
            this.itemsStoredInside = stack.getCount();
        }

        public ItemStack getVirtualStack() {
            return getStackInSlot(0);
        }

        public void setVirtualStack(ItemStack stack) {
            setStackInSlot(0, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return (int) this.maxStoredItems;
        }

        public int getSlotLimit() {
            return getSlotLimit(0);
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int extractedAmount = (int) Math.min(amount, itemsStoredInside);
            if (virtualItemStack.isEmpty() || extractedAmount == 0) {
                return ItemStack.EMPTY;
            }

            ItemStack extractedStack = virtualItemStack.copy();
            extractedStack.setCount(extractedAmount);

            if (!simulate) {
                this.itemsStoredInside -= extractedAmount;
                if (itemsStoredInside == 0L) {
                    this.virtualItemStack = ItemStack.EMPTY;
                }
            }
            return extractedStack;
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack insertedStack, boolean simulate) {
            if (insertedStack.isEmpty() || !isItemValid(slot, insertedStack)) {
                return ItemStack.EMPTY;
            }

            // If there is a virtualized stack and the stack to insert does not match it, do not insert anything
            if (itemsStoredInside > 0L &&
                    !virtualItemStack.isEmpty() &&
                    !areItemStackIdentical(virtualItemStack, insertedStack)) {
                return insertedStack;
            }

//            ItemStack exportItems = getExportItems().getStackInSlot(0);
//
//            // if there is an item in the export slot and the inserted stack does not match, do not insert
//            if (!exportItems.isEmpty() && !areItemStackIdentical(exportItems, insertedStack)) {
//                return insertedStack;
//            }

            ItemStack remainingStack = insertedStack.copy();

            // Virtualize the items
            long amountLeftInChest = maxStoredItems - itemsStoredInside;
            int maxPotentialVirtualizedAmount = insertedStack.getCount();

            int actualVirtualizedAmount = (int) Math.min(maxPotentialVirtualizedAmount, amountLeftInChest);

            // if there are any items left over, shrink it by the amount virtualized,
            // which should always be between 0 and the amount left in chest
            remainingStack.shrink(actualVirtualizedAmount);

            if (!simulate) {
                if (actualVirtualizedAmount > 0) {
                    if (virtualItemStack.isEmpty()) {
                        ItemStack virtualStack = insertedStack.copy();

                        // set the virtual stack to 1, since it's mostly for display
                        virtualStack.setCount(1);
                        this.virtualItemStack = virtualStack;
                        this.itemsStoredInside = actualVirtualizedAmount;
                    } else {
                        this.itemsStoredInside += actualVirtualizedAmount;
                    }
                }
            }

            if (isVoiding() && remainingStack.getCount() > 0) {
                return ItemStack.EMPTY;
            } else {
                return remainingStack;
            }
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            NBTTagCompound compound = stack.getTagCompound();
            var virtualItemStack = getVirtualStack();

            if (compound == null) return true;
            if (!virtualItemStack.isEmpty() && areItemStackIdentical(getVirtualStack(), stack))
                return true;

            return !(compound.hasKey(NBT_ITEMSTACK, NBT.TAG_COMPOUND) ||
                    compound.hasKey("Fluid", NBT.TAG_COMPOUND)); // prevents inserting items with NBT to the Quantum
            // Chest
        }

        public void writeToNBT(NBTTagCompound data) {
            if (!virtualItemStack.isEmpty() && itemsStoredInside > 0L) {
                data.setTag(NBT_ITEMSTACK, virtualItemStack.writeToNBT(new NBTTagCompound()));
                data.setLong(NBT_ITEMCOUNT, itemsStoredInside);
            }
            data.setBoolean(IS_VOIDING, voiding);
        }

        public void readFromNBT(NBTTagCompound data) {
            if (data.hasKey("ItemStack", NBT.TAG_COMPOUND)) {
                this.virtualItemStack = new ItemStack(data.getCompoundTag("ItemStack"));
                if (!virtualItemStack.isEmpty()) {
                    this.itemsStoredInside = data.getLong(NBT_ITEMCOUNT);
                }
            }
            // todo handle export stack here as well
            if (data.hasKey(IS_VOIDING)) {
                this.voiding = data.getBoolean(IS_VOIDING);
            }
        }

        public void writeInitialSyncData(PacketBuffer buf) {
            buf.writeLong(this.itemsStoredInside);
            buf.writeBoolean(this.voiding);
            buf.writeItemStack(this.virtualItemStack);
        }

        public void receiveInitialSyncData(PacketBuffer buf) throws IOException {
            this.itemsStoredInside = buf.readLong();
            this.voiding = buf.readBoolean();
            this.virtualItemStack = buf.readItemStack();
        }

        @Override
        public void readOnClient(int id, PacketBuffer buf) throws IOException {
            if (id == 2) {
                virtualItemStack = buf.readItemStack();
                itemsStoredInside = buf.readInt();
            }
        }

        @Override
        public void readOnServer(int id, PacketBuffer buf) {
            if (id == 1) {
                var data = MouseData.readPacket(buf);
                if (data.mouseButton == 0) {
                    var stack = extractItem(0, virtualItemStack.getMaxStackSize(), false);
                    GTTransferUtils.insertItem(getSyncManager().getPlayerInventory(), stack, false);
                    syncToClient(2, buffer -> {
                        buffer.writeItemStack(virtualItemStack);
                        buffer.writeInt((int) itemsStoredInside);
                    });
                }
            }
        }

        public boolean isVoiding() {
            return this.voiding;
        }

        private void setVoiding(boolean isVoiding) {
            this.voiding = isVoiding;
        }
    }

    @Override
    public boolean needsSneakToRotate() {
        return true;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public int getLightOpacity() {
        return 0;
    }
}
