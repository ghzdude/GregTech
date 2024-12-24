package gregtech.api.capability.impl;

import gregtech.api.capability.IMultipleTankHandler2;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FluidTankList2 implements IMultipleTankHandler2 {

    private final boolean allowSameFluidFill;
    private Entry[] tanks = new Entry[0];

    public FluidTankList2(boolean allowSameFluidFill, IFluidTank... fluidTanks) {
        if (!ArrayUtils.isEmpty(fluidTanks)) {
            tanks = new Entry[fluidTanks.length];
            Arrays.setAll(tanks, value -> wrap(fluidTanks[value]));
        }
        this.allowSameFluidFill = allowSameFluidFill;
    }

    public FluidTankList2(boolean allowSameFluidFill, @NotNull List<? extends IFluidTank> fluidTanks) {
        this(allowSameFluidFill, fluidTanks.toArray(new IFluidTank[0]));
    }

    @Override
    public IFluidTankProperties[] getTankProperties() {
        return this.tanks;
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        if (resource == null || resource.amount <= 0)
            return 0;

        FluidStack copy = resource.copy();

        int totalInserted = 0;

        var fluidTanks = this.tanks.clone();
        Arrays.sort(fluidTanks, ENTRY_COMPARATOR);

        // search for tanks with same fluid type first
        for (Entry tank : fluidTanks) {
            if (!copy.isFluidEqual(tank.getFluid()))
                continue;

            // if the fluid to insert matches the tank, insert the fluid
            int inserted = tank.fill(copy, doFill);
            if (inserted <= 0) continue;

            totalInserted += inserted;
            copy.amount -= inserted;
            if (copy.amount <= 0) {
                return totalInserted;
            }
        }

        // if we still have fluid to insert, loop through empty tanks until we find one that can accept the fluid
        for (Entry tank : fluidTanks) {
            // if the tank uses distinct fluid fill (allowSameFluidFill disabled) and another distinct tank had
            // received the fluid, skip this tank
            if (tank.getFluidAmount() > 0 || !tank.canFillFluidType(copy))
                continue;

            int inserted = tank.fill(copy, doFill);
            if (inserted <= 0) continue;

            totalInserted += inserted;
            copy.amount -= inserted;
            if (copy.amount <= 0) {
                return totalInserted;
            }
        }
        // return the amount of fluid that was inserted
        return totalInserted;
    }

    @Nullable
    @Override
    public FluidStack drain(FluidStack resource, boolean doDrain) {
        if (resource == null || resource.amount <= 0) {
            return null;
        }
        int amountLeft = resource.amount;
        FluidStack totalDrained = null;
        for (IFluidTank handler : tanks) {
            if (!resource.isFluidEqual(handler.getFluid())) {
                continue;
            }
            FluidStack drain = handler.drain(amountLeft, doDrain);
            if (drain != null) {
                if (totalDrained == null) {
                    totalDrained = drain;
                } else {
                    totalDrained.amount += drain.amount;
                }
                amountLeft -= drain.amount;
                if (amountLeft <= 0) {
                    return totalDrained;
                }
            }
        }
        return totalDrained;
    }

    @Nullable
    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        if (maxDrain <= 0) {
            return null;
        }
        FluidStack totalDrained = null;
        for (IFluidTank handler : tanks) {
            if (totalDrained == null) {
                var drained = handler.drain(maxDrain, doDrain);
                if (drained != null) {
                    totalDrained = drained.copy();
                    maxDrain -= totalDrained.amount;
                }
            } else {
                if (!totalDrained.isFluidEqual(handler.getFluid())) {
                    continue;
                }
                FluidStack drain = handler.drain(maxDrain, doDrain);
                if (drain != null) {
                    totalDrained.amount += drain.amount;
                    maxDrain -= drain.amount;
                }
            }
            if (maxDrain <= 0) {
                return totalDrained;
            }
        }
        return totalDrained;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound fluidInventory = new NBTTagCompound();
        NBTTagList tanks = new NBTTagList();
        for (Entry tank : this.tanks) {
            tanks.appendTag(tank.serializeNBT());
        }
        fluidInventory.setTag("Tanks", tanks);
        return fluidInventory;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        NBTTagList tanks = nbt.getTagList("Tanks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tanks.tagCount(); i++) {
            this.tanks[i].deserializeNBT(tanks.getCompoundTagAt(i));
        }
    }

    @Override
    public @NotNull List<Entry> getFluidTanks() {
        return Collections.unmodifiableList(Arrays.asList(this.tanks));
    }

    @Override
    public int getTanks() {
        return tanks.length;
    }

    @Override
    public @NotNull IMultipleTankHandler2.Entry getTankAt(int index) {
        return tanks[index];
    }

    @Override
    public boolean allowSameFluidFill() {
        return allowSameFluidFill;
    }

    @Override
    public String toString() {
        return toString(false);
    }

    public String toString(boolean lineBreak) {
        StringBuilder stb = new StringBuilder("FluidTankList[").append(this.tanks.length).append(";");
        for (int i = 0; i < this.tanks.length; i++) {
            if (i != 0) stb.append(',');
            stb.append(lineBreak ? "\n  " : " ");

            FluidStack fluid = this.tanks[i].getFluid();
            if (fluid == null || fluid.amount == 0) {
                stb.append("None 0 / ").append(this.tanks[i].getCapacity());
            } else {
                stb.append(fluid.getFluid().getName()).append(' ').append(fluid.amount)
                        .append(" / ").append(this.tanks[i].getCapacity());
            }
        }
        if (lineBreak) stb.append('\n');
        return stb.append(']').toString();
    }

    private TankWrapper wrap(IFluidTank tank) {
        return tank instanceof TankWrapper ? (TankWrapper) tank : new TankWrapper(tank, this);
    }

    public static class TankWrapper implements Entry {

        private final IFluidTank tank;
        private final IMultipleTankHandler2 parent;
//        private final IFluidTankProperties[] props;

        private TankWrapper(IFluidTank tank, IMultipleTankHandler2 parent) {
            this.tank = tank;
            this.parent = parent;
//            this.props = new IFluidTankProperties[] { this };
        }

        @Override
        public @NotNull IMultipleTankHandler2 getParentHandler() {
            return parent;
        }

        @Override
        public @NotNull IFluidTank getDelegate() {
            return tank;
        }

//        @Override
//        public IFluidTankProperties[] getTankProperties() {
//            return this.props;
//        }

        @Override
        public boolean canFillFluidType(FluidStack fluidStack) {
            if (allowSameFluidFill() || fluidStack == null) return true;
            int i = parent.getIndexOfFluid(fluidStack);
            var tank = parent.getTankAt(i);
            return tank.getFluidAmount() < tank.getCapacity();
        }

        @Override
        public boolean canDrainFluidType(FluidStack fluidStack) {
            return true;
        }
    }
}
