package gregtech.common.covers.filter.readers;

import gregtech.api.util.IDirtyNotifiable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.INBTSerializable;

import org.jetbrains.annotations.NotNull;

public abstract class BaseFilterReader implements FilterReader, INBTSerializable<NBTTagCompound> {

    protected final ItemStack container;
    private IDirtyNotifiable dirtyNotifiable;
    private final int size;
    protected int maxTransferRate = 1;
    protected static final String BLACKLIST = "IsBlacklist";
    protected static final String FILTER_CONTENTS = "FilterSlots";

    public BaseFilterReader(ItemStack container, int slots) {
        this.container = container;
        this.size = slots;
    }

    public ItemStack getContainer() {
        return this.container;
    }

    public @NotNull NBTTagList getInventoryNbt() {
        var nbt = getStackTag();
        if (!nbt.hasKey(FILTER_CONTENTS)) {
            NBTTagList list = new NBTTagList();
            for (int i = 0; i < getSize(); i++) {
                list.appendTag(new NBTTagCompound());
            }
            nbt.setTag(FILTER_CONTENTS, list);
        }
        return nbt.getTagList(FILTER_CONTENTS, Constants.NBT.TAG_COMPOUND);
    }

    public @NotNull NBTTagCompound getStackTag() {
        NBTTagCompound nbt = this.container.getTagCompound();
        if (nbt == null) {
            nbt = new NBTTagCompound();
            this.container.setTagCompound(nbt);
        }
        return nbt;
    }

    @Override
    public int getSize() {
        return this.size;
    }

    public final void setDirtyNotifiable(IDirtyNotifiable dirtyNotifiable) {
        this.dirtyNotifiable = dirtyNotifiable;
    }

    public final void markDirty() {
        if (dirtyNotifiable != null) {
            dirtyNotifiable.markAsDirty();
        }
    }

    public void onTransferRateChange() {}

    public final void setBlacklistFilter(boolean blacklistFilter) {
        if (getStackTag().getBoolean(BLACKLIST) != blacklistFilter) {
            if (blacklistFilter)
                getStackTag().setBoolean(BLACKLIST, true);
            else
                getStackTag().removeTag(BLACKLIST);
            onTransferRateChange();
            markDirty();
        }
    }

    public final boolean isBlacklistFilter() {
        return getStackTag().getBoolean(BLACKLIST);
    }

    public void setMaxTransferRate(int transferRate) {
        transferRate = MathHelper.clamp(transferRate, 1, Integer.MAX_VALUE);
        if (this.maxTransferRate != transferRate) {
            this.maxTransferRate = transferRate;
            onTransferRateChange();
        }
    }

    public int getMaxTransferRate() {
        return isBlacklistFilter() ? 1 : this.maxTransferRate;
    }

    @Override
    public boolean validateSlotIndex(int slot) {
        return slot >= 0 && slot < getSize();
    }

    @Override
    public NBTTagCompound serializeNBT() {
        return getStackTag();
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        if (nbt.hasKey(BLACKLIST))
            setBlacklistFilter(nbt.getBoolean(BLACKLIST));
    }
}
