package filesystem.service;


import filesystem.entity.datastorage.Inode;
import filesystem.entity.exception.SuperBlockException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.PriorityQueue;
import java.util.Queue;

import static filesystem.entity.filesystem.FileType.FILE;

/*
 * SuperBlockService looks like:
 * ------------------------
 * |     numOfInodes      |
 * |----------------------|
 * | inode structure here |
 * |----------------------|
 * | inode structure here |
 * |----------------------|
 * |  ..................  |
 * |  ..................  |
 * |----------------------|
 * | inode structure here |
 * |----------------------|
 * |      page size       |
 * ------------------------
 */
public class SuperBlockService {
    private final int numOfInodes;
    private final File file;
    private final Queue<Integer> freeInodes;
    private final int pageSize;

    /**
     * @param numOfInodes the amount of inodes
     * @param pageSize    size of page (as well the minimum size of segment)
     * @param file        file in which build superBlock in
     */
    public SuperBlockService(int numOfInodes, int pageSize, File file) {
        if (!file.exists())
            throw new SuperBlockException("File doesn't exist!");
        if (numOfInodes <= 1)
            throw new SuperBlockException("Number of inodes are too small!");

        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            // initialise inodes as unused
            freeInodes = new PriorityQueue<>(numOfInodes);

            out.writeInt(numOfInodes);

            byte[] dummyInodeBytes = new Inode(-1, pageSize, FILE, -1).toByteArray();
            for (int i = 0; i < numOfInodes; i++) {
                out.writeByte(0); // unused
                out.write(dummyInodeBytes);
                freeInodes.add(i); // all free initially
            }

            out.writeInt(pageSize);
        } catch (IOException e) {
            throw new SuperBlockException("SuperBlock initialisation has failed!", e);
        }
        this.file = file;
        this.pageSize = pageSize;
        this.numOfInodes = numOfInodes;
    }

    /**
     * This constructor considers that file has already initialised fileSystem (as well superBlock in it).
     * It will read this superBlock and will fill all needed fields.
     *
     * @param file file with initialised superBlock in it
     */
    public SuperBlockService(File file) {
        if (!file.exists())
            throw new SuperBlockException("File doesn't exist!");

        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            numOfInodes = in.readInt();
            freeInodes = new PriorityQueue<>(numOfInodes);

            if (numOfInodes <= 1)
                throw new SuperBlockException("Number of inodes are too small!");

            for (int i = 0; i < numOfInodes; i++) {
                int used = in.readByte();
                if (used == 0)
                    freeInodes.add(i);

                in.skipBytes(Inode.getSizeOfStructure());
            }
            pageSize = in.readInt();
        } catch (IOException e) {
            throw new SuperBlockException("File writing went wrong during initialisation!", e);
        }
        this.file = file;
    }

    /**
     * It will acquire min free inode's index in superBlock.
     *
     * @param inode Inode class instance
     * @return inodeNum (or index) of acquired inode
     * @see Inode
     */
    public int acquireInode(Inode inode, RandomAccessFile rFile) {
        if (freeInodes.isEmpty())
            throw new SuperBlockException("All inodes are taken!");

        int inodeNum = freeInodes.poll();
        int offset = getInodeOffsetByIndex(inodeNum);

        try {
            rFile.seek(offset);
            rFile.write(1);
            rFile.write(inode.toByteArray());
            return inodeNum;
        } catch (IOException e) {
            throw new SuperBlockException("File writing went wrong during acquiring of inode!", e);
        }
    }

    /**
     * Method will write new inode by index
     *
     * @param inodeNum to write inode instance to
     * @param inode    instance of Inode class to write
     * @see Inode
     */
    public void updateInode(int inodeNum, Inode inode, RandomAccessFile rFile) {
        if (numOfInodes <= inodeNum || inodeNum < 0)
            throw new SuperBlockException("Not correct inodeNum");

        int offset = getInodeOffsetByIndex(inodeNum) + 1;

        try {
            rFile.seek(offset);
            rFile.write(inode.toByteArray());
        } catch (IOException e) {
            throw new SuperBlockException("File writing went wrong during update of inode!", e);
        }
    }

    /**
     * Method will read inode by index. Be cautious and remember, that this is low-level method enables user
     * to read uninitialised(free) inodes as well.
     *
     * @param inodeNum index of read inode
     * @return Inode class instance under given index
     */
    public Inode readInode(int inodeNum, RandomAccessFile rFile) {
        if (numOfInodes <= inodeNum || inodeNum < 0)
            throw new SuperBlockException("Not correct inodeNum");
        try {
            rFile.seek(getInodeOffsetByIndex(inodeNum));
            rFile.read();
            byte[] result = new byte[Inode.getSizeOfStructure()];
            rFile.readFully(result);
            return Inode.fromByteArray(result);
        } catch (IOException e) {
            throw new SuperBlockException("File reading went wrong during reading of inode!", e);
        }
    }

    /**
     * Method to release occupied inode by inodeNum (index)
     *
     * @param inodeNum index of occupied inode
     */
    public void removeInode(int inodeNum, RandomAccessFile rFile) {
        if (numOfInodes <= inodeNum || inodeNum < 0)
            throw new SuperBlockException("Not correct inodeNum");

        int offset = getInodeOffsetByIndex(inodeNum);

        try {
            rFile.seek(offset);
            if (rFile.read() == 0) {
                throw new SuperBlockException("Inode was already released!");
            }
            rFile.seek(offset);
            rFile.write(0);
        } catch (IOException e) {
            throw new SuperBlockException("File writing went wrong during writing to inode!", e);
        }
        freeInodes.add(inodeNum);
    }


    public long getSuperBlockOffset() {
        return numOfInodes * (Inode.getSizeOfStructure() + 1) + 4 + 4;
    }

    public int getNumOfInodes() {
        return numOfInodes;
    }

    public static int getInodeOffsetByIndex(int inodeNum) {
        return 4 + (1 + Inode.getSizeOfStructure()) * inodeNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getNumOfFreeInodes() {
        return freeInodes.size();
    }
}
