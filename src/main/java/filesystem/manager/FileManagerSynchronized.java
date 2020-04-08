package filesystem.manager;

import filesystem.entity.ByteStream;
import filesystem.entity.config.FileSystemConfiguration;
import filesystem.entity.exception.FileManagerException;
import filesystem.entity.filesystem.DirectoryReadResult;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * That is attempt to make FileManager synchronised properly.
 * <p>
 * It would take a lot of time to test it, if I would choose fine-grained approach,
 * by that I mean locking by inode num, taking lock from the map of locks.
 * Due to lack of time and a huge desire to implement something like that, I need to mention that this seems to be possible.
 * <p>
 * However, here I'll write simpler solution based on a straight-forward idea of synchronisation of everything (a little smarter).
 * <p>
 * ReadWriteReentrant lock looks like a perfect match for this, as it practically used when reading
 * outnumbers writing operations, as we have here. Though, as I've said, it would be better to make more fine-grained locking,
 * blocking per inode or directory, but I wouldn't be able to give strong guarantees that it will work properly,
 * taking into account caching operations made here.
 */
public class FileManagerSynchronized implements OneFileSystem {
    private final FileManager fileManager;
    private final Lock readLock;
    private final Lock writeLock;

    {
        ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        readLock = readWriteLock.readLock();
        writeLock = readWriteLock.writeLock();
    }

    /**
     * Creates and configures fileSystem
     *
     * @param fileSystemConfiguration object to take settings from
     */
    public FileManagerSynchronized(FileSystemConfiguration fileSystemConfiguration) {
        this.fileManager = new FileManager(fileSystemConfiguration);
    }

    /**
     * To initialise file system based on file.
     *
     * @param file with already initialised file system
     */
    public FileManagerSynchronized(File file, int concurrencyLevel) {
        this.fileManager = new FileManager(file, concurrencyLevel);
    }


    /**
     * @param pathToFile to file in one-file-system
     * @param in         to copy data from
     */
    @Override
    public void writeToFileFromInputStream(String pathToFile, InputStream in) {
        try {
            writeLock.lock();
            fileManager.writeToFileFromInputStream(pathToFile, in);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * @param pathToFile to file in one-file-system
     * @param out        to copy data in
     */
    @Override
    public void copyDataFromFileToOutputStream(String pathToFile, OutputStream out) {
        try {
            readLock.lock();
            fileManager.copyDataFromFileToOutputStream(pathToFile, out);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * @param pathToFile where to write
     * @param data       array of bytes to write
     */
    @Override
    public void writeToFile(String pathToFile, byte[] data) {
        try {
            writeLock.lock();
            fileManager.writeToFile(pathToFile, data);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Not thread safe!
     * <p>
     * Byte stream can be changed after returning from this method!
     * Or even be used by another file. Use external Interface to copy files.
     *
     * @see ExternalFileSystemInterface#copyDataFromFileToOutputStream(String, OutputStream)
     */
    @Override
    public ByteStream readFileByByteStream(String pathToFile) {
        throw new FileManagerException("Cannot use this method from synchronized instance!");
    }


    /**
     * @param pathToFileParent directory where file is located
     * @param whereToMove      directory to place file in
     * @param fileName         name of file to move (file could be directory type)
     */
    @Override
    public void moveFileToDirectory(String pathToFileParent, String whereToMove, String fileName) {
        try {
            writeLock.lock();
            fileManager.moveFileToDirectory(pathToFileParent, whereToMove, fileName);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * @param path     where file is located
     * @param withSize if you want to include size about file to returned list
     * @return names of files in directory
     */
    @Override
    public List<DirectoryReadResult> getFilesInDirectory(String path, boolean withSize) {
        try {
            readLock.lock();
            return fileManager.getFilesInDirectory(path, withSize);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * @param pathToFileParent directory where file should be located
     * @param fileName         name of new directory
     */
    @Override
    public void createDirectory(String pathToFileParent, String fileName) {
        try {
            writeLock.lock();
            fileManager.createDirectory(pathToFileParent, fileName);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * @param pathToFileParent directory where file should be located
     * @param fileName         name of new file
     * @param size             size of file
     */
    @Override
    public void createFile(String pathToFileParent, String fileName, long size) {
        try {
            writeLock.lock();
            fileManager.createFile(pathToFileParent, fileName, size);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * @param pathToFileParent directory where file should be located
     * @param fileName         name of new file
     */
    @Override
    public void createFile(String pathToFileParent, String fileName) {
        try {
            writeLock.lock();
            fileManager.createFile(pathToFileParent, fileName);
        } finally {
            writeLock.unlock();
        }
    }


    /**
     * @param pathToFile     path to file
     * @param whereToAdd     directory to add hardlink
     * @param nameOfHardLink name of this hardlink
     */
    @Override
    public void createHardLink(String pathToFile, String whereToAdd, String nameOfHardLink) {
        try {
            writeLock.lock();
            fileManager.createHardLink(pathToFile, whereToAdd, nameOfHardLink);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * @param pathToFile  path to be copied file
     * @param whereToCopy directory to copy file in
     * @param withName    new name of file
     */
    @Override
    public void copyFileToDirectory(String pathToFile, String whereToCopy, String withName) {
        try {
            writeLock.lock();
            fileManager.copyFileToDirectory(pathToFile, whereToCopy, withName);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * @param pathToFile path to file
     */
    @Override
    public void removeFile(String pathToFile) {
        try {
            writeLock.lock();
            fileManager.removeFile(pathToFile);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * @return num of free pages in file system
     */
    @Override
    public int getSizeInPages() {
        try {
            readLock.lock();
            return fileManager.getSizeInPages();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * @return num of free bytes in file system
     */
    @Override
    public long getSize() {
        try {
            readLock.lock();
            return fileManager.getSize();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * @param pathToFile path to file to evaluate size of
     * @return file size, where size of file its content plus name stored in it. For directories it all files covered by them.
     */
    @Override
    public long getFileSize(String pathToFile) {
        try {
            readLock.lock();
            return fileManager.getFileSize(pathToFile);
        } finally {
            readLock.unlock();
        }
    }
}
