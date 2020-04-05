package filesystem.entity.filesystem;

import filesystem.entity.ByteRepresentable;
import filesystem.entity.ByteStream;
import filesystem.entity.memorymarks.IgnoreFromMemoryChecking;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static filesystem.utils.ByteArrayConverterUtils.getByteArrayFromInt;
import static filesystem.utils.ByteArrayConverterUtils.stringToByteArray;

@IgnoreFromMemoryChecking
public class Directory extends BaseFileInf implements ByteRepresentable {
    private DEntry parent;
    private List<DEntry> dEntries;


    public Directory(String name, DEntry parent, Collection<DEntry> dEntries) {
        super(name);
        this.parent = parent;
        this.dEntries = new ArrayList<>(dEntries);
    }

    public static Directory of(ByteStream stream) {
        String name = stream.getString();
        int numOfEntries = stream.getInt();
        int parentInode = stream.getInt();

        List<DEntry> dEntries = new ArrayList<>(numOfEntries);

        for (int i = 0; i < numOfEntries; i++) {
            dEntries.add(DEntry.of(stream));
        }

        return new Directory(name, DEntry.of("..", parentInode), dEntries);
    }

    @Override
    public byte[] toByteArray() {
        byte[] nameBytes = stringToByteArray(name);
        byte[] numOfDEntries = getByteArrayFromInt(dEntries.size());
        byte[] parentBytes = getByteArrayFromInt(parent.getInode());


        int size = nameBytes.length + numOfDEntries.length + parentBytes.length;

        byte[][] arr = new byte[dEntries.size()][];

        for (int i = 0; i < dEntries.size(); i++) {
            arr[i] = dEntries.get(i).toByteArray();
            size += arr[i].length;
        }

        byte[] result = new byte[size];

        ByteBuffer buff = ByteBuffer.wrap(result);

        buff.put(nameBytes);
        buff.put(numOfDEntries);
        buff.put(parentBytes);

        for (byte[] bytes : arr) {
            buff.put(bytes);
        }

        return buff.array();
    }

    public List<DEntry> getdEntries() {
        return dEntries;
    }

    public boolean addDEntry(DEntry newDEntry) {
        if (!dEntries.contains(newDEntry)) {
            dEntries.add(newDEntry);
            return true;
        }
        return false;
    }

    public boolean removeDEntry(DEntry dEntry) {
        return dEntries.remove(dEntry);
    }

    public DEntry getDEntry(DEntry dEntry){
        if (dEntries.contains(dEntry)){
            return dEntries.get(dEntries.indexOf(dEntry));
        }
        return null;
    }

    public DEntry getParent() {
        return parent;
    }
}
