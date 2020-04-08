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
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static filesystem.entity.filesystem.FileType.DIRECTORY;
import static filesystem.entity.filesystem.FileType.FILE;
import static filesystem.utils.FileSystemUtils.addToPath;
import static filesystem.utils.FileSystemUtils.checkThatDirectoryAncestor;
import static filesystem.utils.FileSystemUtils.cleanFileName;
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


    private final SilentBlockingQueue<RandomAccessFile> poolOfFiles;

    /**
     * Creates and configures fileSystem
     *
     * @param fileSystemConfiguration object to take settings from
     */
    public FileManager(FileSystemConfiguration fileSystemConfiguration) {
        this.fileSystemConfiguration = fileSystemConfiguration;
        superBlockService = new SuperBlockService(
                fileSystemConfiguration.getNumOfInodes(),
                fileSystemConfiguration.getPageSize(),
                fileSystemConfiguration.getFile()
        );

        poolOfFiles = new SilentBlockingQueue(fileSystemConfiguration.getConcurrencyLevel());
        int segmentsAmount = getSegmentsAmount(fileSystemConfiguration, superBlockService.getSuperBlockOffset());

        segmentAllocatorService = new SegmentAllocatorService(
                superBlockService.getSuperBlockOffset(),
                segmentsAmount,
                fileSystemConfiguration.getPageSize(),
                fileSystemConfiguration.getFile()
        );


        for (int i = 0; i < fileSystemConfiguration.getConcurrencyLevel(); i++) {
            try {
                poolOfFiles.put(new RandomAccessFile(fileSystemConfiguration.getFile(), "rw"));
            } catch (FileNotFoundException e) {
                throw new FileManagerException("File doesn't exist!", e);
            }
        }

        initialiseRoot();
    }

    /**
     * To initialise file system based on file.
     *
     * @param file with already initialised file system
     * @param concurrencyLevel num of concurrently working threads (on windows always one)
     */
    public FileManager(File file, int concurrencyLevel) {
        superBlockService = new SuperBlockService(file);

        fileSystemConfiguration = FileSystemConfiguration.of(
                file.getTotalSpace(),
                superBlockService.getPageSize(),
                superBlockService.getNumOfInodes(),
                file,
                false,
                concurrencyLevel
        );

        int segmentsAmount = getSegmentsAmount(fileSystemConfiguration, superBlockService.getSuperBlockOffset());


        segmentAllocatorService = new SegmentAllocatorService(
                superBlockService.getSuperBlockOffset(),
                segmentsAmount,
                fileSystemConfiguration.getPageSize(),
                file
        );

        poolOfFiles = new SilentBlockingQueue<>(fileSystemConfiguration.getConcurrencyLevel());


        for (int i = 0; i < fileSystemConfiguration.getConcurrencyLevel(); i++) {
            try {
                poolOfFiles.put(new RandomAccessFile(file, "rw"));
            } catch (FileNotFoundException e) {
                throw new FileManagerException("File doesn't exist!", e);
            }
        }
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
        RandomAccessFile file = null;
        try {
            file = poolOfFiles.take();
            int fileInodeNum = getFileInodeByPath(pathToFile, file);
            Inode fileInode = superBlockService.readInode(fileInodeNum, file);

            if (fileInode.getFileType() == DIRECTORY) {
                throw new FileManagerException("Cannot write to the directory!");
            }
            byte[] data = new byte[1024];
            try {
                while (true) {
                    int read = in.read(data, 0, data.length);
                    if (read == -1) break;
                    writeDataByInode(fileInodeNum, data, read, file);
                }
            } catch (Exception e) {
                throw new FileManagerException("Some IO error occurred!", e);
            }
        } finally {
            poolOfFiles.put(file);
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
        RandomAccessFile file = null;
        try {
            file = poolOfFiles.take();
            int fileInodeNum = getFileInodeByPath(pathToFile, file);
            Inode fileInode = superBlockService.readInode(fileInodeNum, file);

            if (fileInode.getFileType() == DIRECTORY) {
                throw new FileManagerException(pathToFile + " is directory!");
            }
            ByteStream stream = segmentAllocatorService
                    .readDataFromSegmentByByteStream(fileInode.getSegment(), file);

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
        } finally {
            poolOfFiles.put(file);
        }
    }

    // internal api

    /**
     * @param pathToFile where to write
     * @param data       array of bytes to write
     */
    @Override
    public void writeToFile(String pathToFile, byte[] data) {
        RandomAccessFile file = null;
        try {
            file = poolOfFiles.take();
            int inodeNum = getFileInodeByPath(pathToFile, file);
            Inode inode = superBlockService.readInode(inodeNum, file);

            if (inode.getFileType() != FILE) {
                throw new FileManagerException(pathToFile + " isn't a file!");
            }

            writeDataByInode(inodeNum, data, file);
        } finally {
            poolOfFiles.put(file);
        }
    }

    /**
     * @param pathToFile to create byteStream
     * @return byte stream to read data from
     */
    @Override
    public ByteStream readFileByByteStream(String pathToFile) {
        RandomAccessFile file = null;
        try {
            file = poolOfFiles.take();
            int inodeNum = getFileInodeByPath(pathToFile, file);
            Inode inode = superBlockService.readInode(inodeNum, file);

            if (inode.getFileType() != FILE) {
                throw new FileManagerException(pathToFile + " isn't a file!");
            }

            return segmentAllocatorService.readDataFromSegmentByByteStream(inode.getSegment(), file);
        } finally {
            poolOfFiles.put(file);
        }
    }

    /**
     * @param pathToFileParent directory where file is located
     * @param whereToMove      directory to place file in
     * @param fileName         name of file to move (file could be directory type)
     */
    @Override
    public void moveFileToDirectory(String pathToFileParent, String whereToMove, String fileName) {
        RandomAccessFile file = null;
        try {
            file = poolOfFiles.take();
            createHardLink(addToPath(pathToFileParent, fileName), whereToMove, fileName, file);
            removeFile(pathToFileParent, fileName, file);
        } finally {
            poolOfFiles.put(file);
        }
    }

    /**
     * @param path     where file is located
     * @param withSize boolean if you need to know size as well (please, remember, that this will take some extra time)
     * @return list of files' descriptions
     */
    @Override
    public List<DirectoryReadResult> getFilesInDirectory(String path, boolean withSize) {
        RandomAccessFile file = null;
        try {
            file = poolOfFiles.take();
            RandomAccessFile finalFile = file;
            return getContentInDirectory(path, file).stream()
                    .map(
                            dEntry -> {
                                Inode inode = superBlockService.readInode(dEntry.getInode(), finalFile);
                                long fileSize = withSize ? getFileSize(dEntry.getInode(), finalFile) : 0;
                                return DirectoryReadResult.of(dEntry.getName(), inode.getFileType(), fileSize);
                            })
                    .collect(toList());
        } finally {
            poolOfFiles.put(file);
        }
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
        RandomAccessFile file = null;
        try {
            file = poolOfFiles.take();
            return getContentInDirectory(path, file).stream().map(DEntry::getName).collect(toList());
        } finally {
            poolOfFiles.put(file);
        }
    }

    /**
     * @param pathToFileParent directory where file should be located
     * @param fileName         name of new directory
     */
    @Override
    public void createDirectory(String pathToFileParent, String fileName) {
        RandomAccessFile file = null;
        try {
            file = poolOfFiles.take();
            fileName = cleanFileName(fileName);
            checkFileName(fileName);
            int parentInode = getFileInodeByPath(pathToFileParent, file);
            Directory newDirectory = new Directory(fileName, DEntry.of("..", parentInode), emptyList());
            int inodeOfNewDirectoryNum = allocateNewDirectory(newDirectory, file);

            addDEntryToDirectory(parentInode, DEntry.of(fileName, inodeOfNewDirectoryNum), file);
        } finally {
            poolOfFiles.put(file);
        }
    }

    /**
     * @param pathToFileParent directory where file should be located
     * @param fileName         name of new file
     * @param size             size of file
     */
    @Override
    public void createFile(String pathToFileParent, String fileName, long size) {
        RandomAccessFile file = null;
        try {
            file = poolOfFiles.take();
            createFile(pathToFileParent, fileName, size, file);
        } finally {
            poolOfFiles.put(file);
        }
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
        RandomAccessFile file = null;
        try {
            file = poolOfFiles.take();
            createHardLink(pathToFile, whereToAdd, nameOfHardLink, file);
        } finally {
            poolOfFiles.put(file);
        }
    }

    /**
     * @param pathToFile  path to be copied file
     * @param whereToCopy directory to copy file in
     * @param withName    new name of file
     */
    @Override
    public void copyFileToDirectory(String pathToFile, String whereToCopy, String withName) {
        RandomAccessFile file = null;
        try {
            file = poolOfFiles.take();
            withName = cleanFileName(withName);
            int inodeNum = getFileInodeByPath(pathToFile, file);
            Inode fileInode = superBlockService.readInode(inodeNum, file);

            createFile(whereToCopy, withName, fileInode.getSize(), file);

            int copiedFileInodeNum = getFileInodeByPath(addToPath(whereToCopy, withName), file);

            ByteStream stream = segmentAllocatorService
                    .readDataFromSegmentByByteStream(fileInode.getSegment(), file);

            byte[] data = new byte[1024];
            stream.getString();
            while (stream.hasNext()) {
                int read = stream.getArr(data);
                writeDataByInode(copiedFileInodeNum, data, read, file);
            }
        } finally {
            poolOfFiles.put(file);
        }
    }

    /**
     * @param pathToFile path to file
     */
    @Override
    public void removeFile(String pathToFile) {
        RandomAccessFile file = null;
        try {
            file = poolOfFiles.take();
            String fileName = getFileNameByPath(pathToFile);
            removeFile(getFileParent(pathToFile), fileName, file);
        } finally {
            poolOfFiles.put(file);
        }
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
        return segmentAllocatorService.getRemainingCapacity() * (long) fileSystemConfiguration.getPageSize();
    }

    /**
     * @param pathToFile path to file to evaluate size of
     * @return size of file (for directories size of all files inside without repetitions due to hard links)
     */
    @Override
    public long getFileSize(String pathToFile) {
        RandomAccessFile file = null;
        try {
            file = poolOfFiles.take();
            return getFileSize(getFileInodeByPath(pathToFile, file), new HashSet<>(), 0, file);
        } finally {
            poolOfFiles.put(file);
        }
    }

    // some useful methods --------------------------------------------------------------------------

    private long getFileSize(int inodeNum, Set<Integer> consideredInodes, long accumulated, RandomAccessFile file) {
        if (consideredInodes.contains(inodeNum)) {
            return accumulated;
        }

        Inode inode = superBlockService.readInode(inodeNum, file);
        if (inode.getFileType() == FILE) {
            consideredInodes.add(inodeNum);
            return inode.getSize() + accumulated;
        }

        Directory directory = readDirectory(inodeNum, file);
        for (DEntry dEntry : directory.getdEntries()) {
            accumulated = getFileSize(dEntry.getInode(), consideredInodes, accumulated, file);
        }

        return accumulated;
    }

    public long getFileSize(int inodeNum, RandomAccessFile file) {
        return getFileSize(inodeNum, new HashSet<>(), 0, file);
    }

    private boolean checkCyclicReferences(String ancestor, String directoryPathToCheck, RandomAccessFile file) {
        if (checkThatDirectoryAncestor(ancestor, directoryPathToCheck)) {
            Inode fileInode = superBlockService.readInode(getFileInodeByPath(ancestor, file), file);
            return fileInode.getFileType() == DIRECTORY;
        }
        return false;
    }

    private void removeFile(String pathToFileParent, String fileName, RandomAccessFile file) {
        int parentInodeNum = getFileInodeByPath(pathToFileParent, file);
        removeDEntryFromDirectory(parentInodeNum, DEntry.of(fileName, -1), file);
    }

    private List<DEntry> getContentInDirectory(String path, RandomAccessFile file) {
        int inode = getFileInodeByPath(path, file);
        return readDirectory(inode, file).getdEntries();
    }


    private void checkFileName(String str) {
        if (str.isEmpty() || str.equals(".") || str.equals("..")) {
            throw new FileManagerException("Illegal file name!");
        }
    }

    private Directory readDirectory(int inodeNum, RandomAccessFile file) {
        Inode inode = superBlockService.readInode(inodeNum, file);
        if (inode.getFileType() != DIRECTORY)
            throw new FileManagerException("File isn't directory");
        return Directory.of(
                segmentAllocatorService.readDataFromSegmentByByteStream(inode.getSegment(), file)
        );
    }

    private void addDEntryToDirectory(int inodeOfParent, DEntry dEntry, RandomAccessFile file) {
        Directory parentDirectory = readDirectory(inodeOfParent, file);

        if (!parentDirectory.addDEntry(dEntry)) {
            throw new FileManagerException("File already exists! " + dEntry.getName());
        }

        reallocateSegments(inodeOfParent, file);
        writeDataByInode(inodeOfParent, parentDirectory.toByteArray(), file);
    }


    private void removeDEntryFromDirectory(int inodeOfParent, DEntry dEntry, RandomAccessFile file) {
        Directory parentDirectory = readDirectory(inodeOfParent, file);

        reallocateSegments(inodeOfParent, file);

        DEntry removedFileDEntry = parentDirectory.getDEntry(dEntry);

        if (removedFileDEntry == null) {
            throw new FileManagerException("File doesn't exists! " + dEntry.getName());
        }

        parentDirectory.removeDEntry(removedFileDEntry);
        writeDataByInode(inodeOfParent, parentDirectory.toByteArray(), file);

        int removedFileInodeNum = removedFileDEntry.getInode();
        Inode removedFileInode = superBlockService.readInode(removedFileInodeNum, file);
        removedFileInode.decrementCounter();

        if (removedFileInode.getCounter() == 0) {
            if (removedFileInode.getFileType() == DIRECTORY) {
                Directory directory = readDirectory(removedFileInodeNum, file);
                directory.getdEntries().forEach(dEntryInside -> removeDEntryFromDirectory(removedFileInodeNum, dEntryInside, file));
            }

            removedFileInode = superBlockService.readInode(removedFileInodeNum, file);
            superBlockService.removeInode(removedFileInodeNum, file);
            segmentAllocatorService.releaseSegment(removedFileInode.getSegment(), file);
        } else {
            superBlockService.updateInode(removedFileInodeNum, removedFileInode, file);
        }
    }

    private void writeDataByInode(int inodeNum, byte[] data, int length, RandomAccessFile file) {
        Inode inode = superBlockService.readInode(inodeNum, file);
        int lastSegment = segmentAllocatorService.writeDataToSegment(inode.getLastSegment(), data, length, file);
        inode.setLastSegment(lastSegment);
        inode.addSize(data.length);
        superBlockService.updateInode(inodeNum, inode, file);
    }

    private void writeDataByInode(int inodeNum, byte[] data, RandomAccessFile file) {
        writeDataByInode(inodeNum, data, data.length, file);
    }

    private int getSegmentsAmount(FileSystemConfiguration configuration, long superBlockOffset) {
        return (int) ((configuration.getSize() - superBlockOffset) / configuration.getPageSize());
    }

    private int allocateNewDirectory(Directory directory, RandomAccessFile file) {
        int segment = segmentAllocatorService.allocateSegments(1, file);
        int inode = superBlockService.acquireInode(new Inode(segment, 0, DIRECTORY, 1), file);

        writeDataByInode(inode, directory.toByteArray(), file);
        return inode;
    }

    private int allocateNewBaseFileInf(long size, String name, RandomAccessFile file) {
        int segment = segmentAllocatorService.allocateSegmentsInBytes(size, file);
        int inode = superBlockService.acquireInode(new Inode(segment, 0, FILE, 1), file);

        writeDataByInode(inode, BaseFileInf.of(name).toByteArray(), file);
        return inode;
    }

    private void initialiseRoot() {
        RandomAccessFile file = null;
        try {
            file = poolOfFiles.take();
            allocateNewDirectory(new Directory("", DEntry.of("", -1), emptyList()), file);
        } finally {
            poolOfFiles.put(file);
        }
    }

    private void reallocateSegments(int inodeNum, RandomAccessFile file) {
        Inode inode = superBlockService.readInode(inodeNum, file);
        segmentAllocatorService.releaseSegment(inode.getSegment(), file);
        int newSegment = segmentAllocatorService.allocateSegmentsInBytes(inode.getSize(), file);
        inode.setSegment(newSegment);
        inode.setLastSegment(newSegment);
        inode.setSize(0);
        superBlockService.updateInode(inodeNum, inode, file);
    }

    private int getFileInodeByPath(String path, RandomAccessFile file) {
        List<String> steps = pathToSteps(path);

        int curr = 0;
        Directory curDirectory = readDirectory(curr, file);

        for (int i = 0; i < steps.size() - 1; i++) {
            List<DEntry> dEntries = curDirectory.getdEntries();
            int neededDirectory = dEntries.indexOf(DEntry.of(steps.get(i), 0));
            if (neededDirectory == -1) {
                throw new FileManagerException("There isn't such path! " + path);
            }

            curr = dEntries.get(neededDirectory).getInode();
            curDirectory = readDirectory(curr, file);
        }

        String lastStep = steps.get(steps.size() - 1);
        if (lastStep.isEmpty()) {
            return curr;
        }
        int neededFile = curDirectory.getdEntries().indexOf(DEntry.of(lastStep, 0));
        if (neededFile == -1) {
            throw new FileManagerException("There isn't such file! " + path);
        }

        return curDirectory.getdEntries().get(neededFile).getInode();
    }

    private void createHardLink(String pathToFile, String whereToAdd, String nameOfHardLink, RandomAccessFile file) {
        nameOfHardLink = cleanFileName(nameOfHardLink);
        checkFileName(nameOfHardLink);
        if (checkCyclicReferences(pathToFile, whereToAdd, file)) {
            throw new FileManagerException("Cyclic reference creation!");
        }
        int fileInode = getFileInodeByPath(pathToFile, file);
        int directoryInodeNum = getFileInodeByPath(whereToAdd, file);
        addDEntryToDirectory(directoryInodeNum, DEntry.of(nameOfHardLink, fileInode), file);

        Inode inode = superBlockService.readInode(fileInode, file);
        inode.incrementCounter();
        superBlockService.updateInode(fileInode, inode, file);
    }

    private void createFile(String pathToFileParent, String fileName, long size, RandomAccessFile file) {
        fileName = cleanFileName(fileName);
        checkFileName(fileName);
        int fileInodeNum = allocateNewBaseFileInf(size, fileName, file);
        addDEntryToDirectory(getFileInodeByPath(pathToFileParent, file), DEntry.of(fileName, fileInodeNum), file);
    }

}
