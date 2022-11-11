package mrtjp.projectred.fabrication.editor;

import mrtjp.fengine.TileCoord;
import net.minecraft.nbt.CompoundNBT;

public class EditorDataUtils {

    // ICEditor
    public static final String KEY_FORMAT = "format"; // int
    public static final String KEY_ACTIVE = "active"; // boolean
    public static final String KEY_IC_NAME = "ic_name"; // String
    public static final String KEY_TILE_MAP = "tile_map"; // CompoundNBT
    public static final String KEY_TILE_COUNT = "tile_count"; // int
    public static final String KEY_IS_BUILT = "is_built"; // boolean
    public static final String KEY_IO_RS = "rsmask"; // CompoundNBT
    public static final String KEY_IO_BUNDLED = "bmask"; // CompoundNBT

    // ICEditorStateMachine
    public static final String KEY_COMP_STATE = "state"; // byte
    public static final String KEY_FLAT_MAP = "flat_map"; // String
    public static final String KEY_SIMULATION = "sim_cont"; // CompoundNBT
    public static final String KEY_COMPILER_LOG = "compiler_log"; // CompoundNBT

    public static int getFormat(CompoundNBT tag) {
        return tag.getInt(KEY_FORMAT);
    }

    public static boolean hasEditorData(CompoundNBT tag) {
        return tag != null &&
                tag.contains(KEY_FORMAT) &&
                tag.contains(KEY_ACTIVE) &&
                tag.contains(KEY_TILE_MAP);
    }

    // Minimum subset of data required to fabricate gate (i.e. create photomask)
    public static boolean hasFabricationTarget(CompoundNBT tag) {
        return tag != null &&
                tag.contains(KEY_IS_BUILT) &&
                tag.contains(KEY_FLAT_MAP);
    }

    public static boolean canFabricate(CompoundNBT tag) {
        return hasFabricationTarget(tag) && tag.getBoolean(KEY_IS_BUILT);

        //TODO check errors
    }

    // Creates copy of editor tag with only the data required to fabricate a gate
    public static CompoundNBT createFabricationCopy(CompoundNBT editorTag) {
        CompoundNBT copy = new CompoundNBT();
        copy.putBoolean(KEY_IS_BUILT, editorTag.getBoolean(KEY_IS_BUILT));
        copy.putString(KEY_IC_NAME, editorTag.getString(KEY_IC_NAME));
        copy.putInt(KEY_TILE_COUNT, editorTag.getInt(KEY_TILE_COUNT));
        //TODO handle other types of IO
        copy.putByte(KEY_IO_BUNDLED, editorTag.getByte(KEY_IO_BUNDLED));
        copy.putString(KEY_FLAT_MAP, editorTag.getString(KEY_FLAT_MAP));
        return copy;
    }

    public static void saveTileCoord(CompoundNBT tag, String key, TileCoord coord) {
        tag.putByte(key + "_x", (byte) coord.x);
        tag.putByte(key + "_y", (byte) coord.y);
        tag.putByte(key + "_z", (byte) coord.z);
    }

    public static TileCoord loadTileCoord(CompoundNBT tag, String key) {
        return new TileCoord(
                tag.getByte(key + "_x"),
                tag.getByte(key + "_y"),
                tag.getByte(key + "_z")
        );
    }

    public static CompoundNBT tileCoordToNBT(TileCoord coord) {
        CompoundNBT tag = new CompoundNBT();
        saveTileCoord(tag, "", coord);
        return tag;
    }

    public static TileCoord tileCoordFromNBT(CompoundNBT tag) {
        return loadTileCoord(tag, "");
    }
}
