package mrtjp.projectred.fabrication.engine;

import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import mrtjp.fengine.TileCoord;
import mrtjp.projectred.fabrication.editor.EditorDataUtils;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static mrtjp.projectred.fabrication.editor.EditorDataUtils.KEY_ERROR_COUNT;
import static mrtjp.projectred.fabrication.editor.EditorDataUtils.KEY_WARNING_COUNT;

public class ICIssuesLog {

    private final List<ICIssue> issues = new ArrayList<>();
    private int warningCount = 0;
    private int errorCount = 0;

    public void save(CompoundNBT tag) {
        ListNBT list = new ListNBT();
        for (ICIssue issue : issues) {
            CompoundNBT issueTag = new CompoundNBT();
            issueTag.putByte("_type", (byte) issue.type.getID());
            issue.save(issueTag);
            list.add(issueTag);
        }
        tag.put("issues", list);
        tag.putInt(KEY_WARNING_COUNT, warningCount);
        tag.putInt(KEY_ERROR_COUNT, errorCount);
    }

    public void load(CompoundNBT tag) {
        clear();
        ListNBT list = tag.getList("issues", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundNBT issueTag = list.getCompound(i);
            ICIssue issue = IssueType.createById(issueTag.getByte("_type") & 0xFF);
            issue.load(issueTag);
            addIssue(issue);
        }
    }

    public void writeDesc(MCDataOutput out) {
        out.writeVarInt(issues.size());
        for (ICIssue issue : issues) {
            out.writeByte(issue.type.getID());
            issue.writeDesc(out);
        }
    }

    public void readDesc(MCDataInput in) {
        clear();
        int size = in.readVarInt();
        for (int i = 0; i < size; i++) {
            ICIssue issue = IssueType.createById(in.readUByte());
            issue.readDesc(in);
            addIssue(issue);
        }
    }

    public void clear() {
        issues.clear();
        warningCount = 0;
        errorCount = 0;
    }

    public void addIssue(ICIssue issue) {
        issues.add(issue);
        switch (issue.severity) {
            case WARNING:
                warningCount++;
                break;
            case ERROR:
                errorCount++;
                break;
        }
    }

    public List<ICIssue> getIssues() {
        return issues;
    }

    public enum IssueType {
        MULTIPLE_DRIVERS(() -> new MultipleDriversIssue()),

        ;

        public static final IssueType[] VALUES = values();

        private final Supplier<ICIssue> issueSupplier;

        IssueType(Supplier<ICIssue> issueSupplier) {
            this.issueSupplier = issueSupplier;
        }

        public ICIssue newInstance() {
            return issueSupplier.get();
        }

        public int getID() {
            return ordinal();
        }

        public static ICIssue createById(int type) {
            return VALUES[type].newInstance();
        }
    }

    public enum IssueSeverity {
        ERROR,
        WARNING
    }

    public static abstract class ICIssue {

        public final IssueType type;
        public final IssueSeverity severity;

        public ICIssue(IssueType type, IssueSeverity severity) {
            this.type = type;
            this.severity = severity;
        }

        public abstract void save(CompoundNBT tag);
        public abstract void load(CompoundNBT tag);
        public abstract void writeDesc(MCDataOutput out);
        public abstract void readDesc(MCDataInput in);
    }

    public static class MultipleDriversIssue extends ICIssue {

        public TileCoord coord;
        public final List<Integer> registerList = new ArrayList<>();

        public MultipleDriversIssue() {
            super(IssueType.MULTIPLE_DRIVERS, IssueSeverity.ERROR);
        }

        public MultipleDriversIssue(TileCoord coord, List<Integer> registerList) {
            super(IssueType.MULTIPLE_DRIVERS, IssueSeverity.ERROR);
            this.coord = coord;
            this.registerList.addAll(registerList);
        }

        @Override
        public void save(CompoundNBT tag) {
            tag.put("coord", EditorDataUtils.tileCoordToNBT(coord));
            tag.putIntArray("registers", registerList.stream().mapToInt(i -> i).toArray());
        }

        @Override
        public void load(CompoundNBT tag) {
            coord = EditorDataUtils.tileCoordFromNBT(tag.getCompound("coord"));
            registerList.clear();
            for (int i : tag.getIntArray("registers")) {
                registerList.add(i);
            }
        }

        @Override
        public void writeDesc(MCDataOutput out) {
            out.writeByte(coord.x).writeByte(coord.y).writeByte(coord.z);
            out.writeShort(registerList.size());
            for (int i : registerList) {
                out.writeVarInt(i);
            }
        }

        @Override
        public void readDesc(MCDataInput in) {
            coord = new TileCoord(in.readByte(), in.readByte(), in.readByte());
            registerList.clear();
            int size = in.readUShort();
            for (int i = 0; i < size; i++) {
                registerList.add(in.readVarInt());
            }
        }
    }
}
