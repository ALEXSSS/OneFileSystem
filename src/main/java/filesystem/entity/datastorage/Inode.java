package filesystem.entity.datastorage;

import filesystem.entity.ByteRepresentable;
import filesystem.entity.filesystem.FileType;

import java.nio.ByteBuffer;

/*
 * Inode (in this file system) will look like
 * ------------------
 * |      used*     | not in the structure, but in memory, without this field inode doesn't have sense
 * |     segment    | = the first segment in memory where file is located
 * |      size      | = the size of the file
 * |     fileType   | = type of file
 * |     counter    | = num of file references
 * |   lastSegment  | = last segment in sequence of segments (by default equals segment) used for caching
 * ------------------
 */
public class Inode implements ByteRepresentable {
    private int segment;
    private long size;
    private FileType fileType;
    private int counter;
    private int lastSegment;

    /**
     * Constructor to create inode
     *
     * @param segment  the first segment in memory where file is located
     * @param size     the size of the file
     * @param fileType type of file
     * @param counter  num of file references
     */
    public Inode(int segment, long size, FileType fileType, int counter) {
        this.segment = segment;
        this.lastSegment = segment;
        this.size = size;
        this.fileType = fileType;
        this.counter = counter;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public Inode(int segment, long size, FileType fileType, int counter, int lastSegment) {
        this.segment = segment;
        this.lastSegment = lastSegment;
        this.size = size;
        this.fileType = fileType;
        this.counter = counter;
    }

    public static int getSizeOfStructure() {
        return 8 + 4 * 3 + 1; // long + int + int + int + fileType
    }

    @Override
    public byte[] toByteArray() {
        byte[] result = new byte[getSizeOfStructure()];

        // write offset
        result[0] = (byte) ((segment >>> 24) & 0xFF);
        result[1] = (byte) ((segment >>> 16) & 0xFF);
        result[2] = (byte) ((segment >>> 8) & 0xFF);
        result[3] = (byte) (segment & 0xFF);

        //write size
        result[4] = (byte) ((size >>> 56) & 0xFF);
        result[5] = (byte) ((size >>> 48) & 0xFF);
        result[6] = (byte) ((size >>> 40) & 0xFF);
        result[7] = (byte) ((size >>> 32) & 0xFF);
        result[8] = (byte) ((size >>> 24) & 0xFF);
        result[9] = (byte) ((size >>> 16) & 0xFF);
        result[10] = (byte) ((size >>> 8) & 0xFF);
        result[11] = (byte) (size & 0xFF);

        // write filetype
        result[12] = (byte) fileType.getValue();

        // write counter
        result[13] = (byte) ((counter >>> 24) & 0xFF);
        result[14] = (byte) ((counter >>> 16) & 0xFF);
        result[15] = (byte) ((counter >>> 8) & 0xFF);
        result[16] = (byte) (counter & 0xFF);

        // write counter
        result[17] = (byte) ((lastSegment >>> 24) & 0xFF);
        result[18] = (byte) ((lastSegment >>> 16) & 0xFF);
        result[19] = (byte) ((lastSegment >>> 8) & 0xFF);
        result[20] = (byte) (lastSegment & 0xFF);

        return result;
    }


    public static Inode fromByteArray(byte[] from) {
        ByteBuffer buffer = ByteBuffer.wrap(from);
        return new Inode(buffer.getInt(), buffer.getLong(), FileType.getFileTypeFromInt(buffer.get()), buffer.getInt(), buffer.getInt());
    }

    public int getSegment() {
        return segment;
    }

    public long getSize() {
        return size;
    }

    public void addSize(long addition) {
        size += addition;
    }

    public FileType getFileType() {
        return fileType;
    }

    public int getCounter() {
        return counter;
    }

    public void incrementCounter() {
        this.counter++;
    }

    public void decrementCounter() {
        this.counter--;
    }

    public void setSegment(int segment) {
        this.segment = segment;
    }

    public int getLastSegment() {
        return lastSegment;
    }

    public void setLastSegment(int lastSegment) {
        this.lastSegment = lastSegment;
    }
}
