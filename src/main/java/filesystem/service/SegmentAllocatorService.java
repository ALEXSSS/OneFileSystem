package filesystem.service;

import filesystem.entity.ByteStream;
import filesystem.entity.datastorage.Segment;
import filesystem.entity.datastorage.SegmentMetaData;
import filesystem.entity.datastorage.SegmentReadResult;
import filesystem.entity.exception.SegmentAllocatorException;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

import static filesystem.utils.ByteArrayConverterUtils.intFromByteArray;
import static java.lang.Math.ceil;
import static java.lang.Math.min;
import static java.util.Comparator.comparingInt;

/**
 * Page - subsequent array of bytes which size is specified by fileSystem.
 * <p>
 * Segment - one or more pages with metaData (humOfPages).
 * <p>
 * Service to allocate segments by. It provides all needed methods for file system
 * to allocate and free pages.
 */
public class SegmentAllocatorService {
    private final long initialOffset; // equals to super block size
    private final int capacity;
    private final int pageSize;
    private volatile int remainingCapacity;
    private final NavigableSet<Segment> freeSegments;
    private final NavigableSet<Segment> freeSegmentsPosition;
    private final File file;


    /**
     * @param initialOffset   where start to allocate segments in file (to free space for superBlock)
     * @param capacityInPages how many pages will file system have
     * @param pageSize        size of page
     * @param file            file to allocate segments in
     */
    public SegmentAllocatorService(long initialOffset, int capacityInPages, int pageSize, File file) {
        this.capacity = capacityInPages;
        this.pageSize = pageSize;
        this.remainingCapacity = capacityInPages;
        this.initialOffset = initialOffset;
        if (!file.exists())
            throw new IllegalArgumentException("File doesn't exist!");
        this.file = file;

        freeSegments = new TreeSet<>();
        freeSegmentsPosition = new TreeSet<>(comparingInt(Segment::getStart));
        Segment initialSegment = Segment.of(0, capacityInPages - 1);
        freeSegments.add(initialSegment);
        freeSegmentsPosition.add(initialSegment);
    }

    /**
     * @see SegmentAllocatorService#allocateSegments(int)
     */
    public int allocateSegmentsInBytes(long numBytes) {
        return allocateSegments(neededBytesToSegments(numBytes));
    }

    /**
     * This method tries to allocate sequence of segments and returns the first index from them
     *
     * @param amountOfSegments to allocate
     * @return index of first segment in sequence
     */
    public int allocateSegments(int amountOfSegments) {
        if (remainingCapacity < amountOfSegments)
            throw new SegmentAllocatorException("File doesn't have enough free memory!");

        Segment fitWithinSegment = freeSegments.ceiling(Segment.of(0, amountOfSegments - 1));
        int answer;
        if (fitWithinSegment != null) {
            removeFromSegments(fitWithinSegment);
            writeMetaDataToSegment(fitWithinSegment.getStart(), amountOfSegments, -1);
            Segment partToReturn = Segment.of(
                    fitWithinSegment.getStart() + amountOfSegments, fitWithinSegment.getEnd()
            );
            if (partToReturn.getSize() != 0) {
                addToSegments(partToReturn);
            }
            answer = fitWithinSegment.getStart();
        } else {
            List<Segment> availableSegments = new LinkedList<>();

            int leftToAllocate = amountOfSegments;

            Segment higher = freeSegments.last();

            while (true) {
                if (higher == null) {
                    throw new IllegalStateException("Out of pages!");
                }
                removeFromSegments(higher);
                availableSegments.add(higher);
                leftToAllocate -= higher.getSize();

                if (leftToAllocate != 0) {
                    higher = freeSegments.ceiling(Segment.of(0, leftToAllocate - 1));
                    if (higher == null) {
                        higher = freeSegments.last();
                    }
                } else {
                    break;
                }
            }

            for (int i = 0; i < availableSegments.size() - 1; i++) {
                Segment segment = availableSegments.get(i);
                writeMetaDataToSegment(
                        segment.getStart(), segment.getSize(), availableSegments.get(i + 1).getSize()
                );
            }

            Segment last = availableSegments.get(availableSegments.size() - 1);
            writeMetaDataToSegment(last.getStart(), last.getSize(), -1);

            answer = availableSegments.get(0).getStart();
        }
        return answer;
    }


    /**
     * This method writes data to given segment (traversing farther segments of the segments sequence if it is needed)
     * Automatically will allocate new segments if it is required.
     *
     * @param segment to write data in
     * @param toWrite byte array with data
     * @param length  how many bytes to read
     */
    public int writeDataToSegment(int segment, byte[] toWrite, int length) {

        int currentSegment = segment;
        int cursorInData = 0;

        while (true) {
            SegmentMetaData metaData = readSegmentMetaData(currentSegment);
            if (SegmentMetaData.getSizeOfStructure() + metaData.getOccupied() <
                    metaData.getNumsOfContinuousBlocks() * pageSize) {
                int freeToWrite = metaData.getNumsOfContinuousBlocks() * pageSize -
                        (SegmentMetaData.getSizeOfStructure() + metaData.getOccupied());
                int possibleToWrite = min(freeToWrite, length - cursorInData);

                try (RandomAccessFile file = new RandomAccessFile(this.file, "rw")) {
                    byte[] temp = new byte[possibleToWrite];
                    System.arraycopy(toWrite, cursorInData, temp, 0, possibleToWrite);
                    file.seek(getDataOffset(currentSegment) + metaData.getOccupied());
                    file.write(temp);
                    metaData.setOccupied(metaData.getOccupied() + possibleToWrite);
                    writeMetaDataToSegment(currentSegment, metaData);
                } catch (IOException e) {
                    throw new SegmentAllocatorException(
                            "File writing went wrong during writing to the segment!", e
                    );
                }

                cursorInData += possibleToWrite;

                if (cursorInData == length) {
                    break;
                }
            }
            if (metaData.getNextSegment() == -1) {
                int neededNewSegments = neededBytesToSegments(length - cursorInData);
                currentSegment = expandSegment(currentSegment, neededNewSegments, metaData).getNextSegment();
            } else {
                currentSegment = metaData.getNextSegment();
            }
        }

        return currentSegment;
    }

    public int writeDataToSegment(int segment, byte[] toWrite) {
        return writeDataToSegment(segment, toWrite, toWrite.length);
    }

    /**
     * This method releases taken before segments, merging splitted segments if it is possible
     *
     * @param segment to release
     */
    public void releaseSegment(int segment) {
        Set<Segment> releasedSegments = new HashSet<>();

        int currSegment = segment;

        SegmentMetaData segmentMetaData;
        do {
            segmentMetaData = readSegmentMetaData(currSegment);
            releasedSegments.add(
                    Segment.of(currSegment, currSegment + segmentMetaData.getNumsOfContinuousBlocks() - 1)
            );
            currSegment = segmentMetaData.getNextSegment();
        } while (segmentMetaData.isContinued());

        releasedSegments.forEach(this::addToSegments);

        while (!releasedSegments.isEmpty()) {

            Segment someSegment = releasedSegments.iterator().next();

            int start = someSegment.getStart();
            int end = someSegment.getEnd();

            Segment left = freeSegmentsPosition.floor(Segment.of(someSegment.getStart() - 1, 0));
            if (left != null && left.getEnd() == someSegment.getStart() - 1) {
                start = left.getStart();
                removeFromSegments(left);
                releasedSegments.remove(left);
            }

            Segment right = freeSegmentsPosition.ceiling(Segment.of(someSegment.getEnd() + 1, 0));
            if (right != null && right.getStart() == someSegment.getEnd() + 1) {
                end = right.getEnd();
                removeFromSegments(right);
                releasedSegments.remove(right);
            }

            if (start != someSegment.getStart() || end != someSegment.getEnd()) {
                removeFromSegments(someSegment);
                Segment mergedSegment = Segment.of(start, end);
                addToSegments(mergedSegment);
                releasedSegments.add(mergedSegment);
            }

            releasedSegments.remove(someSegment);
        }
    }

    /**
     * This method will read maximum pageSize of data.
     *
     * @param segment           segment to read byte array from
     * @param positionInSegment position in segment
     * @return SegmentReadResult
     */
    public SegmentReadResult readDataFromSegmentInPages(int segment, int positionInSegment) {
        SegmentMetaData segmentMetaData = readSegmentMetaData(segment);
        int toRead = segmentMetaData.getOccupied() - positionInSegment;

        byte[] result = new byte[min(pageSize, toRead)];
        try (RandomAccessFile file = new RandomAccessFile(this.file, "rw")) {
            file.seek(getDataOffset(segment) + positionInSegment);
            file.readFully(result);
        } catch (IOException e) {
            throw new SegmentAllocatorException("File reading went wrong during reading of segments' data!", e);
        }

        if (toRead > pageSize) {
            return SegmentReadResult.of(result, segment, positionInSegment + pageSize);
        }
        return SegmentReadResult.of(result, segmentMetaData.getNextSegment(), 0);
    }

    /**
     * Less efficient method than readDataFromSegmentInPages, as it will try to read all contiguous pages in segment
     *
     * @param segment to read from
     * @return SegmentReadResult
     */
    public SegmentReadResult readDataFromSegment(int segment) {
        SegmentMetaData segmentMetaData = readSegmentMetaData(segment);

        byte[] result = new byte[segmentMetaData.getOccupied()];
        try (RandomAccessFile file = new RandomAccessFile(this.file, "rw")) {
            file.seek(getDataOffset(segment));
            file.readFully(result);
        } catch (IOException e) {
            throw new SegmentAllocatorException(
                    "File reading went wrong during reading of segments' data!", e
            );
        }

        return SegmentReadResult.of(result, segmentMetaData.getNextSegment(), 0);
    }

    public ByteStream readDataFromSegmentByByteStream(int segment) {
        return new ByteStreamBasedOnSegments(segment, this);
    }

    public long getMetaDataOffset(long segment) {
        return initialOffset + segment * pageSize;
    }

    public int getRemainingCapacity() {
        return remainingCapacity;
    }

    public long getInitialOffset() {
        return initialOffset;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getPageSize() {
        return pageSize;
    }

    private long getDataOffset(long segment) {
        return initialOffset + SegmentMetaData.getSizeOfStructure() + segment * pageSize;
    }

    private SegmentMetaData expandSegment(
            long segment, int neededAmountOfBlocks, SegmentMetaData metaData
    ) {
        int next = allocateSegments(neededAmountOfBlocks);
        SegmentMetaData newMetaData = new SegmentMetaData(
                metaData.getNumsOfContinuousBlocks(),
                next,
                metaData.getNumsOfContinuousBlocks() * pageSize - SegmentMetaData.getSizeOfStructure()
        );
        writeMetaDataToSegment(segment, newMetaData);
        return newMetaData;
    }

    private void removeFromSegments(Segment segment) {
        remainingCapacity -= segment.getSize();
        freeSegments.remove(segment);
        freeSegmentsPosition.remove(segment);
    }

    private void addToSegments(Segment segment) {
        remainingCapacity += segment.getSize();
        freeSegments.add(segment);
        freeSegmentsPosition.add(segment);
    }

    private int neededBytesToSegments(long numBytes) {
        int amount = (int) ceil(numBytes / (double) pageSize);

        int haveToBeAllocated = amount * pageSize - SegmentMetaData.getSizeOfStructure();
        if (haveToBeAllocated >= numBytes) {
            return amount;
        }
        return amount + 1;
    }

    private SegmentMetaData readSegmentMetaData(long segment) {
        try (RandomAccessFile file = new RandomAccessFile(this.file, "rw")) {
            file.seek(getMetaDataOffset(segment));
            return SegmentMetaData.of(file.readInt(), file.readInt(), file.readInt());
        } catch (IOException e) {
            throw new SegmentAllocatorException("File reading went wrong!", e);
        }
    }


    private void writeMetaDataToSegment(long segment, SegmentMetaData metaData) {
        try (RandomAccessFile file = new RandomAccessFile(this.file, "rw")) {
            byte[] metaBytes = metaData.toByteArray();
            file.seek(getMetaDataOffset(segment));
            file.write(metaBytes);
        } catch (IOException e) {
            throw new SegmentAllocatorException(
                    "File writing went wrong during writing of meta data to the segment!", e
            );
        }
    }

    private void writeMetaDataToSegment(int start, int size, int next) {
        writeMetaDataToSegment(start, new SegmentMetaData(size, next, 0));
    }


    /**
     * Iterates over sequence of segments, reading not more than pageSize of data.
     */
    static class ByteStreamBasedOnSegments implements ByteStream {

        private SegmentAllocatorService segmentAllocatorService;
        private SegmentReadResult segmentReadResult;
        private int currPosition;

        public ByteStreamBasedOnSegments(int segment, SegmentAllocatorService segmentAllocatorService) {
            this.segmentAllocatorService = segmentAllocatorService;
            segmentReadResult = segmentAllocatorService.readDataFromSegmentInPages(segment, 0);
            currPosition = 0;
        }

        @Override
        public boolean hasNext() {
            return segmentReadResult.getArr().length != currPosition
                    || segmentReadResult.getNextSegment() != -1;
        }

        @Override
        public byte getByte() {
            if (currPosition < segmentReadResult.getArr().length) {
                return segmentReadResult.getArr()[currPosition++];
            }
            if (segmentReadResult.getNextSegment() == -1) {
                throw new IllegalStateException("Cannot read farther!");
            }
            segmentReadResult = segmentAllocatorService.readDataFromSegmentInPages(
                    segmentReadResult.getNextSegment(),
                    segmentReadResult.getPositionInSegment()
            );
            currPosition = 1;
            return segmentReadResult.getArr()[0];
        }

        @Override
        public int getInt() {
            return intFromByteArray(new byte[]{getByte(), getByte(), getByte(), getByte()});
        }

        @Override
        public int getArr(byte[] arr) {
            if (currPosition == segmentReadResult.getArr().length) {
                if (segmentReadResult.getNextSegment() == -1)
                    throw new IllegalStateException("Cannot read farther!");

                segmentReadResult = segmentAllocatorService.readDataFromSegmentInPages(
                        segmentReadResult.getNextSegment(),
                        segmentReadResult.getPositionInSegment()
                );
                currPosition = 0;
            }
            int toRead = min(segmentReadResult.getArr().length - currPosition, arr.length);
            for (int i = 0; i < toRead; i++) {
                arr[i] = segmentReadResult.getArr()[currPosition + i];
            }
            currPosition += toRead;
            return toRead;
        }


        /**
         * Strings are stored like size and bytes(not \0 byte)
         *
         * @return String from byte stream
         */
        @Override
        public String getString() {
            int size = getInt();
            byte[] buff = new byte[size];
            for (int i = 0; i < buff.length; i++) {
                buff[i] = getByte();
            }
            return new String(buff);
        }
    }
}
