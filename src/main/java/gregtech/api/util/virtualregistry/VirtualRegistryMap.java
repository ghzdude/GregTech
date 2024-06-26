package gregtech.api.util.virtualregistry;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class VirtualRegistryMap implements INBTSerializable<NBTTagCompound> {

    private final Map<EntryTypes<?>, Map<String, VirtualEntry>> registryMap = new HashMap<>();

    public VirtualRegistryMap(NBTTagCompound tag) {
        deserializeNBT(tag);
    }

    public VirtualRegistryMap() {}

    @SuppressWarnings("unchecked")
    public <T extends VirtualEntry> T getEntry(EntryTypes<T> type, String name) {
        return (T) registryMap.get(type).get(name);
    }

    public <T extends VirtualEntry> void addEntry(String name, T entry) {
        registryMap.computeIfAbsent(entry.getType(), k -> new HashMap<>())
                .put(name, entry);
    }

    public <T extends VirtualEntry> boolean contains(EntryTypes<T> type, String name) {
        if (!registryMap.containsKey(type))
            return false;

        return registryMap.get(type).containsKey(name);
    }

    public void deleteEntry(EntryTypes<?> type, String name) {
        registryMap.get(type).remove(name);
    }

    public void clear() {
        registryMap.clear();
    }

    public Set<String> getEntryNames(EntryTypes<?> type) {
        return registryMap.get(type).keySet();
    }

    @Override
    public @NotNull NBTTagCompound serializeNBT() {
        var tag = new NBTTagCompound();
        for (var type : registryMap.keySet()) {
            var entriesTag = new NBTTagCompound();
            var entries = registryMap.get(type);
            for (var name : entries.keySet()) {
                entriesTag.setTag(name, entries.get(name).serializeNBT());
            }
            tag.setTag(type.toString(), entriesTag);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        for (var entryType : nbt.getKeySet()) {
            var type = EntryTypes.fromString(entryType);
            if (type == null) continue;

            var virtualEntries = nbt.getCompoundTag(entryType);
            for (var name : virtualEntries.getKeySet()) {
                var entry = virtualEntries.getCompoundTag(name);
                addEntry(name, type.createInstance(entry));
            }
        }
    }
}
