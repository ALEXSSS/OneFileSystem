package filesystem.entity.filesystem;

import filesystem.entity.ByteRepresentable;
import filesystem.entity.ByteStream;
import filesystem.entity.memorymarks.IgnoreFromMemoryChecking;
import filesystem.utils.ByteArrayConverterUtils;

import java.util.Map.Entry;
import java.util.Objects;

import static filesystem.utils.ByteArrayConverterUtils.mergeByteArrays;

@IgnoreFromMemoryChecking
public class DEntry implements ByteRepresentable {
    private String name;
    private int inode;


    public DEntry(String name, int inode) {
        this.name = name;
        this.inode = inode;
    }

    public static DEntry of(ByteStream byteStream){
        return new DEntry(byteStream.getString(), byteStream.getInt());
    }

    public static DEntry of(Entry<String, Integer> entry){
        return new DEntry(entry.getKey(), entry.getValue());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getInode() {
        return inode;
    }

    public void setInode(int inode) {
        this.inode = inode;
    }

    public static DEntry of(String name, int inode){
        return new DEntry(name, inode);
    }

    @Override
    public byte[] toByteArray(){
        byte[] sizeOfNameBytes = ByteArrayConverterUtils.getByteArrayFromInt(name.length());
        byte[] nameBytes = name.getBytes();
        byte[] numOfDEntriesBytes = ByteArrayConverterUtils.getByteArrayFromInt(inode);

        return mergeByteArrays(sizeOfNameBytes, nameBytes, numOfDEntriesBytes);
    }

    public boolean isRoot(){
        return inode == -1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DEntry dEntry = (DEntry) o;
        return name.equals(dEntry.name);
    }

    // only name as dEntries compared within one directory
    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "DEntry{" +
                "name='" + name + '\'' +
                ", inode=" + inode +
                '}';
    }
}
