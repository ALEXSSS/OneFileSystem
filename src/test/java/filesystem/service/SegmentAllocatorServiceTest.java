package filesystem.service;

import filesystem.entity.datastorage.SegmentMetaData;
import filesystem.entity.datastorage.SegmentReadResult;
import filesystem.entity.exception.SegmentAllocatorException;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class SegmentAllocatorServiceTest {

    private final static int DEFAULT_SIZE_OF_PAGE = 4096;
    private final static int NUM_OF_PAGES = 200;
    private final static int INITIAL_OFFSET = 100;

    private static SegmentAllocatorService segmentAllocatorService;
    private static File originalFile;

    @Before
    public void init() throws IOException {
        originalFile = File.createTempFile("test", "test");
        try (RandomAccessFile file = new RandomAccessFile(originalFile, "rw")) {
            file.setLength(DEFAULT_SIZE_OF_PAGE * NUM_OF_PAGES);
            segmentAllocatorService = new SegmentAllocatorService(INITIAL_OFFSET, NUM_OF_PAGES, DEFAULT_SIZE_OF_PAGE, originalFile);
        }
        originalFile.deleteOnExit();
    }


    @Test
    public void allocateSegmentsTest() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(originalFile, "rw")) {
            int toAllocate = 20;

            int segment = segmentAllocatorService.allocateSegments(toAllocate, file);
            assertEquals(NUM_OF_PAGES - toAllocate, segmentAllocatorService.getRemainingCapacity());

            file.seek(segmentAllocatorService.getInitialOffset());

            assertEquals(20, file.readInt());
            assertEquals(-1, file.readInt());
            assertEquals(0, file.readInt());


            segmentAllocatorService.writeDataToSegment(segment, new byte[]{1, 2, 3, 4, 5, 6, 7, 8}, file);

            file.seek(segmentAllocatorService.getInitialOffset() + segment + SegmentMetaData.getSizeOfStructure());

            assertEquals(1, file.read());
            assertEquals(2, file.read());
            assertEquals(3, file.read());
            assertEquals(4, file.read());
            assertEquals(5, file.read());
            assertEquals(6, file.read());
            assertEquals(7, file.read());
            assertEquals(8, file.read());


            //meta check

            SegmentMetaData meta = readSegmentMetaDataPublicly(segment, file);

            assertEquals(20, meta.getNumsOfContinuousBlocks());
            assertEquals(8, meta.getOccupied());
            assertEquals(-1, meta.getNextSegment());

            //allocate new data

            segmentAllocatorService.allocateSegments(1, file);

            byte[] ones = new byte[DEFAULT_SIZE_OF_PAGE * 20 - SegmentMetaData.getSizeOfStructure()];
            Arrays.fill(ones, (byte) 1);
            ones[ones.length - 1] = 5;
            segmentAllocatorService.writeDataToSegment(segment, ones, file);

            //meta check

            meta = readSegmentMetaDataPublicly(segment, file);

            assertEquals(20, meta.getNumsOfContinuousBlocks());
            assertEquals(DEFAULT_SIZE_OF_PAGE * 20 - SegmentMetaData.getSizeOfStructure(), meta.getOccupied());
            assertEquals(21, meta.getNextSegment());

            meta = readSegmentMetaDataPublicly(20, file);

            assertEquals(1, meta.getNumsOfContinuousBlocks());
            assertEquals(0, meta.getOccupied());
            assertEquals(-1, meta.getNextSegment());

            meta = readSegmentMetaDataPublicly(21, file);

            assertEquals(1, meta.getNumsOfContinuousBlocks());
            assertEquals(8, meta.getOccupied());
            assertEquals(-1, meta.getNextSegment());

            file.seek(getDataOffsetPublicly(21));
            assertEquals(1, file.read());
            assertEquals(1, file.read());
            assertEquals(1, file.read());
            assertEquals(1, file.read());
            assertEquals(1, file.read());
            assertEquals(1, file.read());
            assertEquals(1, file.read());
            assertEquals(5, file.read());
        }
    }

    @Test
    public void readSegmentMetaDataTest() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(originalFile, "rw")) {
            int toAllocate = 20;

            int segment = segmentAllocatorService.allocateSegments(toAllocate, file);
            assertEquals(NUM_OF_PAGES - toAllocate, segmentAllocatorService.getRemainingCapacity());

            file.seek(segmentAllocatorService.getInitialOffset() + segment);

            assertEquals(20, file.readInt());
            assertEquals(-1, file.readInt());
            assertEquals(0, file.readInt());

            SegmentMetaData metaData = readSegmentMetaDataPublicly(segment, file);

            assertEquals(20, metaData.getNumsOfContinuousBlocks());
            assertEquals(0, metaData.getOccupied());
            assertEquals(-1, metaData.getNextSegment());
        }
    }

    @Test
    public void readDataFromSegmentTest() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(originalFile, "rw")) {

            int segment = segmentAllocatorService.allocateSegments(20, file);

            segmentAllocatorService.allocateSegments(2, file);


            byte[] ones = new byte[DEFAULT_SIZE_OF_PAGE * 22 - SegmentMetaData.getSizeOfStructure()];
            Arrays.fill(ones, (byte) 1);
            ones[ones.length - 1] = 5;
            segmentAllocatorService.writeDataToSegment(segment, ones, file);

            segmentAllocatorService.allocateSegments(2, file);

            byte[] twos = new byte[DEFAULT_SIZE_OF_PAGE * 3 - SegmentMetaData.getSizeOfStructure()];
            Arrays.fill(twos, (byte) 2);
            twos[twos.length - 1] = 6;
            segmentAllocatorService.writeDataToSegment(segment, twos, file);

            SegmentReadResult readResult = segmentAllocatorService.readDataFromSegment(segment, file);
            SegmentReadResult readResult1 = segmentAllocatorService.readDataFromSegment(readResult.getNextSegment(), file);
            SegmentReadResult readResult2 = segmentAllocatorService.readDataFromSegment(readResult1.getNextSegment(), file);

            byte[] actual = new byte[readResult.getArr().length + readResult1.getArr().length + readResult2.getArr().length];

            ByteBuffer buffActual = ByteBuffer.wrap(actual);
            buffActual.put(readResult.getArr());
            buffActual.put(readResult1.getArr());
            buffActual.put(readResult2.getArr());

            actual = buffActual.array();

            byte[] expected = new byte[ones.length + twos.length];

            ByteBuffer buffExpected = ByteBuffer.wrap(expected);
            buffExpected.put(ones);
            buffExpected.put(twos);

            expected = buffExpected.array();
            assertArrayEquals(expected, actual);
        }
    }

    @Test(expected = SegmentAllocatorException.class)
    public void tryAllocateMoreThanPossiblePages() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(originalFile, "rw")) {
            segmentAllocatorService.allocateSegments(NUM_OF_PAGES + 1, file);
        }
    }

    @Test
    public void tryAllocateAllPossiblePagesTest() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(originalFile, "rw")) {

            int initialSize = segmentAllocatorService.getRemainingCapacity();
            int segment = segmentAllocatorService.allocateSegments(NUM_OF_PAGES, file);
            segmentAllocatorService.releaseSegment(segment, file);
            assertEquals("Size should be the same!", initialSize, segmentAllocatorService.getRemainingCapacity());
        }
    }

    @Test
    public void releaseSegmentsTest() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(originalFile, "rw")) {

            byte[] ones = new byte[DEFAULT_SIZE_OF_PAGE * 20 - SegmentMetaData.getSizeOfStructure()];
            byte[] ones1 = new byte[DEFAULT_SIZE_OF_PAGE - SegmentMetaData.getSizeOfStructure()];
            byte[] ones2 = new byte[DEFAULT_SIZE_OF_PAGE * 2 - SegmentMetaData.getSizeOfStructure()];
            byte[] ones3 = new byte[DEFAULT_SIZE_OF_PAGE * 3 - SegmentMetaData.getSizeOfStructure()];

            List<Integer> owners = Arrays.asList(
                    segmentAllocatorService.allocateSegments(20, file),
                    segmentAllocatorService.allocateSegments(20, file),
                    segmentAllocatorService.allocateSegments(20, file));

            segmentAllocatorService.writeDataToSegment(owners.get(0), ones, file);
            segmentAllocatorService.writeDataToSegment(owners.get(1), ones, file);
            segmentAllocatorService.writeDataToSegment(owners.get(2), ones, file);

            for (int i = 0; i < 10; i++) {
                segmentAllocatorService.writeDataToSegment(owners.get(0), ones1, file);
                segmentAllocatorService.writeDataToSegment(owners.get(1), ones2, file);
                segmentAllocatorService.writeDataToSegment(owners.get(2), ones3, file);
            }

            segmentAllocatorService.releaseSegment(owners.get(0), file);
            segmentAllocatorService.releaseSegment(owners.get(1), file);
            segmentAllocatorService.releaseSegment(owners.get(2), file);

            assertEquals(NUM_OF_PAGES, segmentAllocatorService.getRemainingCapacity());
        }
    }


    private static SegmentMetaData readSegmentMetaDataPublicly(int segment, RandomAccessFile file) {
        try {
            Method method = Arrays.stream(segmentAllocatorService.getClass()
                    .getDeclaredMethods()).filter(it -> it.getName().equals("readSegmentMetaData"))
                    .collect(Collectors.toList()).get(0);
            method.setAccessible(true);
            Object result = method.invoke(segmentAllocatorService, segment, file);
            return (SegmentMetaData) result;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("this is test!", e);
        }
    }

    private static long getDataOffsetPublicly(int segment) {
        try {
            Method method = Arrays.stream(segmentAllocatorService.getClass()
                    .getDeclaredMethods()).filter(it -> it.getName().equals("getDataOffset"))
                    .collect(Collectors.toList()).get(0);
            method.setAccessible(true);
            Object result = method.invoke(segmentAllocatorService, segment);
            return (long) result;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("this is test!");
        }
    }
}