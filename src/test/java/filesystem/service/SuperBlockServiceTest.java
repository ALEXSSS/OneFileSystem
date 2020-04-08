package filesystem.service;

import filesystem.entity.datastorage.Inode;
import filesystem.entity.exception.SuperBlockException;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.stream.IntStream;

import static filesystem.entity.filesystem.FileType.DIRECTORY;
import static filesystem.entity.filesystem.FileType.FILE;
import static filesystem.service.SuperBlockService.getInodeOffsetByIndex;
import static org.junit.Assert.assertEquals;

public class SuperBlockServiceTest {

    private static int DEFAULT_SIZE_OF_PAGE = 4096;
    private static int NUM_OF_INODES = 10;

    private File originalFile;
    private SuperBlockService superBlockService;

    @Before
    public void init() throws IOException {
        originalFile = File.createTempFile("test", "test");

        try (RandomAccessFile file = new RandomAccessFile(originalFile, "rw")) {
            file.setLength(DEFAULT_SIZE_OF_PAGE * 20);
        }

        superBlockService = new SuperBlockService(NUM_OF_INODES, DEFAULT_SIZE_OF_PAGE, originalFile);

        originalFile.deleteOnExit();
    }

    @Test
    public void initialiseSuperBlockTest() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(originalFile, "rw")) {
            // check fields
            assertEquals("Error of inodeNumInitialisation!", NUM_OF_INODES, superBlockService.getNumOfInodes());

            assertEquals("Super block offset after initialisation",
                    4 + 4 + NUM_OF_INODES * (Inode.getSizeOfStructure() + 1), superBlockService.getSuperBlockOffset());

            assertEquals("Inodes should be the same as during initialisation!", NUM_OF_INODES, file.readInt());

            /*
             * Inode (in this file system) will look like
             * ------------------
             * |      used*     | not in the structure, but in memory, without this field inode doesn't have sense
             * |     segment    | = the first segment in memory where file is located
             * |      size      | = the size of the file
             * |     fileType   | = type of file
             * |     counter    | = num of file references
             * |   lastSegment  | = last segment in the sequence of segments
             * ------------------
             */
            for (int i = 0; i < NUM_OF_INODES; i++) {
                file.seek(4 + i * (Inode.getSizeOfStructure() + 1));

                assertEquals("Not used", 0, file.read());
                assertEquals("Without next segment", -1, file.readInt());
                assertEquals("By default should be pageSize", DEFAULT_SIZE_OF_PAGE, file.readLong());
                assertEquals("Should be FILE type", FILE.getValue(), file.read());
                assertEquals("Counter should be -1", -1, file.readInt());
                assertEquals("Last should be as segment", -1, file.readInt());
            }

            assertEquals("Page size should be saved after all inodes", DEFAULT_SIZE_OF_PAGE, file.readInt());
        }
    }

    @Test
    public void initialiseSuperBlockFromFileTest() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(originalFile, "rw")) {

            Inode inode = new Inode(1, Long.MAX_VALUE, FILE, 1);

            for (int i = 0; i < 5; i++) {
                superBlockService.acquireInode(inode, file);
            }

            // --------------------------------------------

            // reads it again from file
            SuperBlockService superBlockServiceFromFile = new SuperBlockService(originalFile);

            assertEquals("numOfInodes", superBlockServiceFromFile.getNumOfInodes(), NUM_OF_INODES);
            assertEquals("pageSize", superBlockServiceFromFile.getPageSize(), DEFAULT_SIZE_OF_PAGE);

            // try to allocate new inode
            Inode dummyInode = new Inode(13, 113, FILE, 3, 333);
            int index = superBlockServiceFromFile.acquireInode(dummyInode, file);

            file.seek(getInodeOffsetByIndex(index));

            assertEquals("index should be min one", index, 5);
            assertEquals("used", 1, file.read());
            assertEquals("segment", dummyInode.getSegment(), file.readInt());
            assertEquals("size", dummyInode.getSize(), file.readLong());
            assertEquals("file type", dummyInode.getFileType().getValue(), file.read());
            assertEquals("counter", dummyInode.getCounter(), file.readInt());
            assertEquals("lastSegment", dummyInode.getLastSegment(), file.readInt());
        }
    }

    @Test
    public void removeInodeTest() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(originalFile, "rw")) {

            Inode inode = new Inode(1000, 1001, DIRECTORY, 1002);
            int index = superBlockService.acquireInode(inode, file);
            file.seek(getInodeOffsetByIndex(index));

            // super block should allocate the first free inode (=> with 0 index)
            assertEquals("should be zero as the first inode in the empty super block", 0, index);
            assertEquals("used", 1, file.read());
            assertEquals("segment", inode.getSegment(), file.readInt());
            assertEquals("size", inode.getSize(), file.readLong());
            assertEquals("should be FILE type", inode.getFileType().getValue(), file.read());
            assertEquals("counter", inode.getCounter(), file.readInt());

            Inode inode1 = new Inode(2000, 2001, FILE, 2002);
            int index1 = superBlockService.acquireInode(inode1, file);

            // super block should allocate the first free inode (=> with 1 index)
            file.seek(getInodeOffsetByIndex(index1));

            assertEquals("should be one as the second inode in the empty super block", 1, index1);
            assertEquals("used", 1, file.read());
            assertEquals("segment", inode1.getSegment(), file.readInt());
            assertEquals("size", inode1.getSize(), file.readLong());
            assertEquals("should be FILE type", inode1.getFileType().getValue(), file.read());
            assertEquals("counter", inode1.getCounter(), file.readInt());

            // remove allocated inodes and check that them are free now
            superBlockService.removeInode(1, file);

            file.seek(getInodeOffsetByIndex(index1));
            assertEquals("Inode should be marked as unused!", 0, file.read());

            superBlockService.removeInode(0, file);
            file.seek(getInodeOffsetByIndex(index));
            assertEquals("Inode should be marked as unused!", 0, file.read());

            assertEquals("All inodes are free!", superBlockService.getNumOfFreeInodes(), superBlockService.getNumOfInodes());
        }
    }

    @Test
    public void acquireInodeTest() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(originalFile, "rw")) {
            int numTakenFirstInodes = NUM_OF_INODES - 3;

            // allocate inodes
            Inode dummyInode = new Inode(1, 11, FILE, 111);
            for (int i = 0; i < numTakenFirstInodes; i++) {
                superBlockService.acquireInode(dummyInode, file);
            }
            /*
             * Inode (in this file system) will look like
             * ------------------
             * |      used*     | not in the structure, but in memory, without this field inode doesn't have sense
             * |     segment    | = the first segment in memory where file is located
             * |      size      | = the size of the file
             * |     fileType   | = type of file
             * |     counter    | = num of file references
             * |   lastSegment  | = last segment in sequence
             * ------------------
             */
            // check that they are represented in memory
            for (int i = 0; i < numTakenFirstInodes; i++) {
                file.seek(4 + i * (Inode.getSizeOfStructure() + 1));

                assertEquals("used", 1, file.read());
                assertEquals("next segment", dummyInode.getSegment(), file.readInt());
                assertEquals("size", dummyInode.getSize(), file.readLong());
                assertEquals("Should be FILE type", dummyInode.getFileType().getValue(), file.read());
                assertEquals("counter", dummyInode.getCounter(), file.readInt());
            }

            assertEquals("Taken inodes are registered", NUM_OF_INODES - numTakenFirstInodes, superBlockService.getNumOfFreeInodes());
        }
    }


    @Test
    public void readInodeTest() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(originalFile, "rw")) {

            int numTakenFirstInodes = NUM_OF_INODES - 3;

            for (int i = 0; i < numTakenFirstInodes; i++) {
                superBlockService.acquireInode(new Inode(i, Integer.MAX_VALUE + i, FILE, i, i - 1), file);
            }

            for (int i = 0; i < numTakenFirstInodes; i++) {
                Inode inode = superBlockService.readInode(i, file);

                assertEquals("next segment", i, inode.getSegment());
                assertEquals("size", Integer.MAX_VALUE + i, inode.getSize());
                assertEquals("Should be FILE type", FILE.getValue(), inode.getFileType().getValue());
                assertEquals("counter", i, inode.getCounter());
                assertEquals("lastSegment", i - 1, inode.getLastSegment());
            }
        }
    }

    @Test
    public void updateInodeTest() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(originalFile, "rw")) {

            for (int i = 0; i < 5; i++) {
                Inode inode = new Inode(i, Integer.MAX_VALUE + i, FILE, i);
                superBlockService.acquireInode(inode, file);
            }


            Inode updatedInode = new Inode(3333, 33333, DIRECTORY, 33333, 3333333);
            superBlockService.updateInode(3, updatedInode, file);

            Inode inode = superBlockService.readInode(3, file);

            // didn't want to use equals on all fields (if it is will be generated for another usage then replace it)
            assertEquals("segments should be the same", inode.getSegment(), updatedInode.getSegment());
            assertEquals("size should be the same", inode.getSize(), updatedInode.getSize());
            assertEquals("counter should be the same", inode.getCounter(), updatedInode.getCounter());
            assertEquals("file type should be the same", inode.getFileType(), updatedInode.getFileType());
            assertEquals("lastSegment type should be the same", inode.getLastSegment(), updatedInode.getLastSegment());
        }
    }

    @Test(expected = SuperBlockException.class)
    public void readOutOfBoundInodeTest() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(originalFile, "rw")) {

            superBlockService.readInode(NUM_OF_INODES, file);
        }
    }

    @Test(expected = SuperBlockException.class)
    public void updateOutOfBoundInodeTest() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(originalFile, "rw")) {

            superBlockService.updateInode(NUM_OF_INODES, new Inode(1, 1, FILE, 1), file);
        }
    }

    @Test(expected = SuperBlockException.class)
    public void acquireMoreThanPossibleInodesTest() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(originalFile, "rw")) {
            IntStream.range(0, NUM_OF_INODES + 1).forEach(it -> superBlockService.acquireInode(new Inode(1, 1, FILE, 1), file));
        }
    }

    @Test(expected = SuperBlockException.class)
    public void initialiseSuperBlockWithSmallAmountOfInodesTest() {
        new SuperBlockService(1, DEFAULT_SIZE_OF_PAGE, originalFile);
    }
}