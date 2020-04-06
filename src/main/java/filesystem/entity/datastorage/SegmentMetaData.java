package filesystem.entity.datastorage;


import filesystem.entity.ByteRepresentable;

import java.nio.ByteBuffer;

/*
 * Structure in memory which describes memory allocation for some amount of contiguous blocks
 * ----------------
 * |  numOfBlocks | = how many pages in this segment, by default could be 1
 * |  nextSegment | = next segment to continue read file from
 * |   occupied   | = is used to calculate free memory for writing
 * ----------------
 */
public class SegmentMetaData implements ByteRepresentable {
    private final int numsOfContinuousBlocks;
    private final int nextSegment;
    private int occupied;

    /**
     * Constructor to create segmentMetaData
     *
     * @param numsOfContinuousBlocks how many(blocks == pages) will be in the segment
     * @param nextSegment            next start of segment
     * @param occupied               num of occupied bytes in this segment
     */
    public SegmentMetaData(int numsOfContinuousBlocks, int nextSegment, int occupied) {
        this.numsOfContinuousBlocks = numsOfContinuousBlocks;
        this.nextSegment = nextSegment;
        this.occupied = occupied;
    }

    /**
     * @see SegmentMetaData#SegmentMetaData(int, int, int)
     */
    public static SegmentMetaData of(int numsOfContinuousBlocks, int nextSegment, int occupied) {
        return new SegmentMetaData(numsOfContinuousBlocks, nextSegment, occupied);
    }


    public static int getSizeOfStructure() {
        return 4 * 3;
    }

    @Override
    public byte[] toByteArray() {
        byte[] result = new byte[getSizeOfStructure()];

        // write numsOfContinuousBlocks
        result[0] = (byte) ((numsOfContinuousBlocks >>> 24) & 0xFF);
        result[1] = (byte) ((numsOfContinuousBlocks >>> 16) & 0xFF);
        result[2] = (byte) ((numsOfContinuousBlocks >>> 8) & 0xFF);
        result[3] = (byte) (numsOfContinuousBlocks & 0xFF);

        //write nextSegment
        result[4] = (byte) ((nextSegment >>> 24) & 0xFF);
        result[5] = (byte) ((nextSegment >>> 16) & 0xFF);
        result[6] = (byte) ((nextSegment >>> 8) & 0xFF);
        result[7] = (byte) (nextSegment & 0xFF);


        // write occupied
        result[8] = (byte) ((occupied >>> 24) & 0xFF);
        result[9] = (byte) ((occupied >>> 16) & 0xFF);
        result[10] = (byte) ((occupied >>> 8) & 0xFF);
        result[11] = (byte) (occupied & 0xFF);

        return result;
    }

    public static SegmentMetaData fromByteArray(byte[] from) {
        ByteBuffer buffer = ByteBuffer.wrap(from);
        return new SegmentMetaData(buffer.getInt(), buffer.getInt(), buffer.getInt());
    }

    public int getNumsOfContinuousBlocks() {
        return numsOfContinuousBlocks;
    }

    public int getNextSegment() {
        return nextSegment;
    }

    public int getOccupied() {
        return occupied;
    }

    public void setOccupied(int occupied) {
        this.occupied = occupied;
    }

    public boolean isContinued() {
        return nextSegment != -1;
    }

    @Override
    public String toString() {
        return "SegmentMetaData{" +
                "numsOfContinuousBlocks=" + numsOfContinuousBlocks +
                ", nextSegment=" + nextSegment +
                ", occupied=" + occupied +
                '}';
    }
}
