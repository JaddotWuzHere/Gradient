package jaddot.gradient.sim;

import jaddot.gradient.world.RegionKey;
import jaddot.gradient.world.WaterRegionManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import java.util.HashMap;
import java.util.Map;

public class WaterSimState extends PersistentState {
    private final Map<RegionKey, RegionSnapshot> snapshots = new HashMap<>();

    private final WaterRegionManager manager;

    /* -------------------------------------------- */
    /*                 constructors                 */
    /* -------------------------------------------- */

    // first run
    public WaterSimState() {
        this.manager = new WaterRegionManager(this);
    }

    // relog
    public WaterSimState(Map<RegionKey, RegionSnapshot> initialSnapshots) {
        this.snapshots.putAll(initialSnapshots);
        this.manager = new WaterRegionManager(this);
        this.manager.bootstrapFromSnapshots();
    }

    /* -------------------------------------------- */
    /*                setters/getters               */
    /* -------------------------------------------- */

    public static WaterSimState get(ServerWorld world) {
        PersistentStateManager psm = world.getPersistentStateManager();

        return psm.getOrCreate(
                WaterSimState::fromNbt,
                WaterSimState::new,
                "gradient_water_sim"
        );
    }

    public WaterRegionManager getManager() {
        return manager;
    }

    public RegionSnapshot getSnapshot(RegionKey key) {
        return snapshots.get(key);
    }

    public void putSnapshot(RegionKey key, RegionSnapshot snapshot) {
        snapshots.put(key, snapshot);
        this.markDirty();
    }

    public Iterable<RegionKey> getSnapshotKeys() {
        return snapshots.keySet();
    }

    /* -------------------------------------------- */
    /*               nbt serialization              */
    /* -------------------------------------------- */

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList regionsList = new NbtList();

        for (Map.Entry<RegionKey, RegionSnapshot> entry : snapshots.entrySet()) {
            RegionKey key = entry.getKey();
            RegionSnapshot snap = entry.getValue();

            NbtCompound regionTag = new NbtCompound();

            regionTag.putInt("rx", key.rx);
            regionTag.putInt("ry", key.ry);
            regionTag.putInt("rz", key.rz);
            regionTag.putByteArray("levels", snap.getLevels());

            regionsList.add(regionTag);
        }

        nbt.put("regions", regionsList);
        return nbt;
    }

    public static WaterSimState fromNbt(NbtCompound nbt) {
        Map<RegionKey, RegionSnapshot> loaded = new HashMap<>();

        if (nbt.contains("regions")) {
            NbtList regionsList = nbt.getList("regions", NbtElement.COMPOUND_TYPE);

            for (int i = 0; i < regionsList.size(); i++) {
                NbtCompound regionTag = regionsList.getCompound(i);

                int rx = regionTag.getInt("rx");
                int ry = regionTag.getInt("ry");
                int rz = regionTag.getInt("rz");
                RegionKey key = new RegionKey(rx, ry, rz);

                byte[] levels = regionTag.getByteArray("levels");
                RegionSnapshot snapshot = new RegionSnapshot(levels);

                loaded.put(key, snapshot);
            }
        }

        return new WaterSimState(loaded);
    }

    public static class RegionSnapshot {
        private final byte[] levels;

        public RegionSnapshot (byte[] levels) {
            this.levels = levels;
        }

        public byte[] getLevels() {
            return levels;
        }
    }
}
