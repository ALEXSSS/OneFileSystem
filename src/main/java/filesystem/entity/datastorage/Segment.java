package filesystem.entity.datastorage;


/**
 * The segment to consider for memory allocation (practically some amount of pages of this file system)
 */
public class Segment implements Comparable<Segment> {
    private int start;
    private int end;

    /**
     * Constructor to create segment (some amount of contiguous pages)
     *
     * @param  start initial page of segment (where metaData will be stored)
     * @param  end last page of segment
     * @see SegmentMetaData
     */
    public Segment(int start, int end) {
        this.start = start;
        this.end = end;
    }


    /**
     * @see Segment#Segment
     */
    public static Segment of(int start, int end) {
        return new Segment(start, end);
    }

    @Override
    public int compareTo(Segment other) {
        return Integer.compare(end - start, other.end - other.start);
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public int getSize() {
        return end - start + 1;
    }
}
