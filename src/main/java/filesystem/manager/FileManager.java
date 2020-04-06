package filesystem.manager;

import filesystem.entity.ByteStream;
import filesystem.entity.config.FileSystemConfiguration;
import filesystem.entity.datastorage.Inode;
import filesystem.entity.exception.FileManagerException;
import filesystem.entity.filesystem.BaseFileInf;
import filesystem.entity.filesystem.DEntry;
import filesystem.entity.filesystem.Directory;
import filesystem.entity.filesystem.DirectoryReadResult;
import filesystem.service.SegmentAllocatorService;
import filesystem.service.SuperBlockService;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static filesystem.entity.filesystem.FileType.DIRECTORY;
import static filesystem.entity.filesystem.FileType.FILE;
import static filesystem.utils.FileSystemUtils.addToPath;
import static filesystem.utils.FileSystemUtils.checkThatDirectoryAncestor;
import static filesystem.utils.FileSystemUtils.getFileNameByPath;
import static filesystem.utils.FileSystemUtils.getFileParent;
import static filesystem.utils.FileSystemUtils.pathToSteps;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;


/**
 * Class which provides api to work with oneFileSystem.
 * Not thread-safe!!!
 *
 * @see FileManagerSynchronized
 */
public class FileManager implements OneFileSystem {
    private final FileSystemConfiguration fileSystemConfiguration;
    private final SuperBlockService superBlockService;
    private final SegmentAllocatorService segmentAllocatorService;


    /**
     * Creates and configures fileSystem
     *
     * @param fileSystemConfiguration object to take settings from
     */
    public FileManager(FileSystemConfiguration fileSystemConfiguration) {
        this.fileSystemConfiguration = fileSystemConfiguration;
        superBlockService = new SuperBlockService();
        superBlockService.initialiseSuperBlock(
                fileSystemConfiguration.getNumOfInodes(),
                fileSystemConfiguration.getPageSize(),
                fileSystemConfiguration.getFile()
        );

        int segmentsAmount = getSegmentsAmount(fileSystemConfiguration, superBlockService.getSuperBlockOffset());

        segmentAllocatorService = new SegmentAllocatorService(
                superBlockService.getSuperBlockOffset(),
                segmentsAmount,
                fileSystemConfiguration.getPageSize(),
                fileSystemConfiguration.getFile()
        );
        initialiseRoot();
    }

    /**
     * To initialise file system based on file.
     *
     * @param file with already initialised file system
     */
    public FileManager(File file) {
        superBlockService = new SuperBlockService();
        superBlockService.initialiseSuperBlockFromFile(file);

        fileSystemConfiguration = FileSystemConfiguration.of(
                file.getTotalSpace(),
                superBlockService.getPageSize(),
                superBlockService.getNumOfInodes(),
                file,
                false
        );

        int segmentsAmount = getSegmentsAmount(fileSystemConfiguration, superBlockService.getSuperBlockOffset());

        segmentAllocatorService = new SegmentAllocatorService(
                superBlockService.getSuperBlockOffset(),
                segmentsAmount,
                fileSystemConfiguration.getPageSize(),
                file
        );
    }

    // external api

    /**
     * This method mostly used to interact with files of external os system. Copying files in this file
     * system can be made more easily.
     *
     * @param pathToFile in file system
     * @param in         input stream to take file from
     * @throws FileManagerException read IO error occurred
     */
    @Override
    public void writeToFileFromInputStream(String pathToFile, InputStream in) {
        int fileInodeNum = getFileInodeByPath(pathToFile);
        Inode fileInode = superBlockService.readInode(fileInodeNum);

        if (fileInode.getFileType() == DIRECTORY) {
            throw new FileManagerException("Cannot write to the directory!");
        }
        byte[] data = new byte[1024];
        try {
            while (true) {
                int read = in.read(data, 0, data.length);
                if (read == -1) break;
                writeDataByInode(fileInodeNum, data, read);
            }
        } catch (Exception e) {
            throw new FileManagerException("Some IO error occurred!", e);
        }
    }

    /**
     * This method mostly used to work with files of external file system.
     *
     * @param pathToFile to file in file system to copy data from
     * @param out        stream to copy data to
     * @throws FileManagerException when something wrong with underlying outputStream
     */
    @Override
    public void copyDataFromFileToOutputStream(String pathToFile, OutputStream out) {
        int fileInodeNum = getFileInodeByPath(pathToFile);
        Inode fileInode = superBlockService.readInode(fileInodeNum);

        if (fileInode.getFileType() == DIRECTORY) {
            throw new FileManagerException(pathToFile + " is directory!");
        }
        ByteStream stream = segmentAllocatorService
                .readDataFromSegmentByByteStream(fileInode.getSegment());

        stream.getString(); // at the start of file (it's name is stored)

        byte[] data = new byte[1024];
        try {
            while (stream.hasNext()) {
                int read = stream.getArr(data);
                out.write(data, 0, read);
            }
        } catch (Exception e) {
            throw new FileManagerException("Some IO error occurred!", e);
        }
    }

    // internal api

    /**
     * @param pathToFile where to write
     * @param data       array of bytes to write
     */
    @Override
    public void writeToFile(String pathToFile, byte[] data) {
        int inodeNum = getFileInodeByPath(pathToFile);
        Inode inode = superBlockService.readInode(inodeNum);

        if (inode.getFileType() != FILE) {
            throw new FileManagerException(pathToFile + " isn't a file!");
        }

        writeDataByInode(inodeNum, data);
    }

    /**
     * @param pathToFile to create byteStream
     * @return byte stream to read data from
     */
    @Override
    public ByteStream readFileByByteStream(String pathToFile) {
        int inodeNum = getFileInodeByPath(pathToFile);
        Inode inode = superBlockService.readInode(inodeNum);

        if (inode.getFileType() != FILE) {
            throw new FileManagerException(pathToFile + " isn't a file!");
        }

        return segmentAllocatorService.readDataFromSegmentByByteStream(inode.getSegment());
    }

    /**
     * @param pathToFileParent directory where file is located
     * @param whereToMove      directory to place file in
     * @param fileName         name of file to move (file could be directory type)
     */
    @Override
    public void moveFileToDirectory(String pathToFileParent, String whereToMove, String fileName) {
        createHardLink(addToPath(pathToFileParent, fileName), whereToMove, fileName);
        removeFile(pathToFileParent, fileName);
    }

    /**
     * @param path     where file is located
     * @param withSize boolean if you need to know size as well (please, remember, that this will take some extra time)
     * @return list of files' descriptions
     */
    @Override
    public List<DirectoryReadResult> getFilesInDirectory(String path, boolean withSize) {
        return getContentInDirectory(path).stream()
                .map(
                        dEntry -> {
                            Inode inode = superBlockService.readInode(dEntry.getInode());
                            long fileSize = withSize ? getFileSize(dEntry.getInode()) : 0;
                            return DirectoryReadResult.of(dEntry.getName(), inode.getFileType(), fileSize);
                        })
                .collect(toList());
    }

    /**
     * withSize by default false
     *
     * @see #getFilesInDirectory(String, boolean)
     */
    public List<DirectoryReadResult> getFilesInDirectory(String path) {
        return getFilesInDirectory(path, false);
    }


    /**
     * return only names
     *
     * @see #getFilesInDirectory(String, boolean)
     */
    public List<String> getFilesNamesInDirectory(String path) {
        return getContentInDirectory(path).stream().map(DEntry::getName).collect(toList());
    }

    /**
     * @param pathToFileParent directory where file should be located
     * @param fileName         name of new directory
     */
    @Override
    public void createDirectory(String pathToFileParent, String fileName) {
        checkFileName(fileName);
        int parentInode = getFileInodeByPath(pathToFileParent);
        Directory newDirectory = new Directory(fileName, DEntry.of("..", parentInode), emptyList());
        int inodeOfNewDirectoryNum = allocateNewDirectory(newDirectory);

        addDEntryToDirectory(parentInode, DEntry.of(fileName, inodeOfNewDirectoryNum));
    }

    /**
     * @param pathToFileParent directory where file should be located
     * @param fileName         name of new file
     * @param size             size of file
     */
    @Override
    public void createFile(String pathToFileParent, String fileName, long size) {
        checkFileName(fileName);
        int fileInodeNum = allocateNewBaseFileInf(size, fileName);
        addDEntryToDirectory(getFileInodeByPath(pathToFileParent), DEntry.of(fileName, fileInodeNum));
    }

    /**
     * @param pathToFileParent directory where file should be located
     * @param fileName         name of new file
     */
    @Override
    public void createFile(String pathToFileParent, String fileName) {
        createFile(pathToFileParent, fileName, 1);
    }


    /**
     * @param pathToFile     path to file
     * @param whereToAdd     directory to add hardlink
     * @param nameOfHardLink name of this hardlink
     */
    @Override
    public void createHardLink(String pathToFile, String whereToAdd, String nameOfHardLink) {
        checkFileName(nameOfHardLink);
        if (checkCyclicReferences(pathToFile, whereToAdd)) {
            throw new FileManagerException("Cyclic reference creation!");
        }
        int fileInode = getFileInodeByPath(pathToFile);
        int directoryInodeNum = getFileInodeByPath(whereToAdd);
        addDEntryToDirectory(directoryInodeNum, DEntry.of(nameOfHardLink, fileInode));

        Inode inode = superBlockService.readInode(fileInode);
        inode.incrementCounter();
        superBlockService.updateInode(fileInode, inode);
    }

    /**
     * @param pathToFile  path to be copied file
     * @param whereToCopy directory to copy file in
     * @param withName    new name of file
     */
    @Override
    public void copyFileToDirectory(String pathToFile, String whereToCopy, String withName) {
        int inodeNum = getFileInodeByPath(pathToFile);
        Inode fileInode = superBlockService.readInode(inodeNum);

        createFile(whereToCopy, withName, fileInode.getSize());

        int copiedFileInodeNum = getFileInodeByPath(addToPath(whereToCopy, withName));

        ByteStream stream = segmentAllocatorService
                .readDataFromSegmentByByteStream(fileInode.getSegment());

        byte[] data = new byte[1024];
        stream.getString();
        while (stream.hasNext()) {
            int read = stream.getArr(data);
            writeDataByInode(copiedFileInodeNum, data, read);
        }
    }

    /**
     * @param pathToFile path to file
     */
    @Override
    public void removeFile(String pathToFile) {
        String fileName = getFileNameByPath(pathToFile);
        removeFile(getFileParent(pathToFile), fileName);
    }


    /**
     * @return free num of pages in the file system
     * @see #getSize()
     */
    @Override
    public int getSizeInPages() {
        return segmentAllocatorService.getRemainingCapacity();
    }

    /**
     * @return free num of bytes in file system
     * @see #getSizeInPages()
     */
    @Override
    public long getSize() {
        return segmentAllocatorService.getRemainingCapacity() * fileSystemConfiguration.getPageSize();
    }

    /**
     * @param pathToFile path to file to evaluate size of
     * @return size of file (for directories size of all files inside without repetitions due to hard links)
     */
    @Override
    public long getFileSize(String pathToFile) {
        return getFileSize(getFileInodeByPath(pathToFile), new HashSet<>(), 0);
    }

    // some useful methods --------------------------------------------------------------------------
    private long getFileSize(int inodeNum, Set<Integer> consideredInodes, long accumulated) {
        if (consideredInodes.contains(inodeNum)) {
            return accumulated;
        }

        Inode inode = superBlockService.readInode(inodeNum);
        if (inode.getFileType() == FILE) {
            consideredInodes.add(inodeNum);
            return inode.getSize() + accumulated;
        }

        Directory directory = readDirectory(inodeNum);
        for (DEntry dEntry : directory.getdEntries()) {
            accumulated = getFileSize(dEntry.getInode(), consideredInodes, accumulated);
        }

        return accumulated;
    }

    public long getFileSize(int inodeNum) {
        return getFileSize(inodeNum, new HashSet<>(), 0);
    }

    private boolean checkCyclicReferences(String ancestor, String directoryPathToCheck) {
        if (checkThatDirectoryAncestor(ancestor, directoryPathToCheck)) {
            Inode fileInode = superBlockService.readInode(getFileInodeByPath(ancestor));
            return fileInode.getFileType() == DIRECTORY;
        }
        return false;
    }

    private void removeFile(String pathToFileParent, String fileName) {
        int parentInodeNum = getFileInodeByPath(pathToFileParent);
        removeDEntryFromDirectory(parentInodeNum, DEntry.of(fileName, -1));
    }

    private List<DEntry> getContentInDirectory(String path) {
        int inode = getFileInodeByPath(path);
        return readDirectory(inode).getdEntries();
    }


    private void checkFileName(String str) {
        if (str.isEmpty() || str.equals(".") || str.equals("..")) {
            throw new FileManagerException("Illegal file name!");
        }
    }

    private Directory readDirectory(int inodeNum) {
        Inode inode = superBlockService.readInode(inodeNum);
        if (inode.getFileType() != DIRECTORY)
            throw new FileManagerException("File isn't directory");
        return Directory.of(
                segmentAllocatorService.readDataFromSegmentByByteStream(inode.getSegment())
        );
    }

    private void addDEntryToDirectory(int inodeOfParent, DEntry dEntry) {
        Directory parentDirectory = readDirectory(inodeOfParent);

        if (!parentDirectory.addDEntry(dEntry)) {
            throw new FileManagerException("File already exists! " + dEntry.getName());
        }

        reallocateSegments(inodeOfParent);
        writeDataByInode(inodeOfParent, parentDirectory.toByteArray());
    }


    private void removeDEntryFromDirectory(int inodeOfParent, DEntry dEntry) {
        Directory parentDirectory = readDirectory(inodeOfParent);

        reallocateSegments(inodeOfParent);

        DEntry removedFileDEntry = parentDirectory.getDEntry(dEntry);

        if (removedFileDEntry == null) {
            throw new FileManagerException("File doesn't exists! " + dEntry.getName());
        }

        parentDirectory.removeDEntry(removedFileDEntry);
        writeDataByInode(inodeOfParent, parentDirectory.toByteArray());

        int removedFileInodeNum = removedFileDEntry.getInode();
        Inode removedFileInode = superBlockService.readInode(removedFileInodeNum);
        removedFileInode.decrementCounter();

        if (removedFileInode.getCounter() == 0) {
            if (removedFileInode.getFileType() == DIRECTORY) {
                Directory directory = readDirectory(removedFileInodeNum);
                directory.getdEntries().forEach(dEntryInside -> removeDEntryFromDirectory(removedFileInodeNum, dEntryInside));
            }

            removedFileInode = superBlockService.readInode(removedFileInodeNum);
            superBlockService.removeInode(removedFileInodeNum);
            segmentAllocatorService.releaseSegment(removedFileInode.getSegment());
        } else {
            superBlockService.updateInode(removedFileInodeNum, removedFileInode);
        }
    }

    private void writeDataByInode(int inodeNum, byte[] data, int length) {
        Inode inode = superBlockService.readInode(inodeNum);
        int lastSegment = segmentAllocatorService.writeDataToSegment(inode.getLastSegment(), data, length);
        inode.setLastSegment(lastSegment);
        inode.addSize(data.length);
        superBlockService.updateInode(inodeNum, inode);
    }

    private void writeDataByInode(int inodeNum, byte[] data) {
        writeDataByInode(inodeNum, data, data.length);
    }

    private int getSegmentsAmount(FileSystemConfiguration configuration, long superBlockOffset) {
        return (int) ((configuration.getSize() - superBlockOffset) / configuration.getPageSize());
    }

    private int allocateNewDirectory(Directory directory) {
        int segment = segmentAllocatorService.allocateSegments(1);
        int inode = superBlockService.acquireInode(new Inode(segment, 0, DIRECTORY, 1));

        writeDataByInode(inode, directory.toByteArray());
        return inode;
    }

    private int allocateNewBaseFileInf(long size, String name) {
        int segment = segmentAllocatorService.allocateSegmentsInBytes(size);
        int inode = superBlockService.acquireInode(new Inode(segment, 0, FILE, 1));

        writeDataByInode(inode, BaseFileInf.of(name).toByteArray());
        return inode;
    }

    private void initialiseRoot() {
        allocateNewDirectory(new Directory("", DEntry.of("", -1), emptyList()));
    }

    private void reallocateSegments(int inodeNum) {
        Inode inode = superBlockService.readInode(inodeNum);
        segmentAllocatorService.releaseSegment(inode.getSegment());
        int newSegment = segmentAllocatorService.allocateSegmentsInBytes(inode.getSize());
        inode.setSegment(newSegment);
        inode.setLastSegment(newSegment);
        inode.setSize(0);
        superBlockService.updateInode(inodeNum, inode);
    }

    private int getFileInodeByPath(String path) {
        List<String> steps = pathToSteps(path);

        int curr = 0;
        Directory curDirectory = readDirectory(curr);

        for (int i = 0; i < steps.size() - 1; i++) {
            List<DEntry> dEntries = curDirectory.getdEntries();
            int neededDirectory = dEntries.indexOf(DEntry.of(steps.get(i), 0));
            if (neededDirectory == -1) {
                throw new FileManagerException("There isn't such path! " + path);
            }

            curr = dEntries.get(neededDirectory).getInode();
            curDirectory = readDirectory(curr);
        }

        String lastStep = steps.get(steps.size() - 1);
        if (lastStep.equals("")) {
            return curr;
        }
        int neededFile = curDirectory.getdEntries().indexOf(DEntry.of(lastStep, 0));
        if (neededFile == -1) {
            throw new FileManagerException("There isn't such file! " + path);
        }

        return curDirectory.getdEntries().get(neededFile).getInode();
    }

}
