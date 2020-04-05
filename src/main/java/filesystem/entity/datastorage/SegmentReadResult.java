package filesystem.entity.datastorage;

/*
 * All read results in this file system are practically read from such structure
 * | segment1 | ------> | segment2 | --> .... ---> | segmentLast |
 * Where each segment (1,2 ... last) are read as byte arrays (possibly splitted for memory efficiency)
 */
public class SegmentReadResult {
    byte[] arr;
    int nextSegment;
    int positionInSegment;

    /**
     * Constructor for SegmentReadResult
     *
     * @param arr         byte array of current segment
     * @param nextSegment nextSegment to continue reading from
     * @param positionInSegment to start read in nextSegment
     */
    public SegmentReadResult(byte[] arr, int nextSegment, int positionInSegment) {
        this.arr = arr;
        this.nextSegment = nextSegment;
        this.positionInSegment = positionInSegment;
    }

    public static SegmentReadResult of(byte[] arr, int nextSegment, int positionInSegment) {
        return new SegmentReadResult(arr, nextSegment, positionInSegment);
    }

    public byte[] getArr() {
        return arr;
    }

    public int getNextSegment() {
        return nextSegment;
    }

    public int getPositionInSegment(){
        return positionInSegment;
    }
}
