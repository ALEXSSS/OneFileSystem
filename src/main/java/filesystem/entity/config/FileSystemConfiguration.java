package filesystem.entity.config;

import filesystem.entity.exception.OneFileSystemException;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;


/**
 * File system configuration, where all needed settings have to be filled before sending to FileManager
 */
public class FileSystemConfiguration {
    private long size; // size of file system
    private int pageSize; // pageSize (and default segment size)
    private int numOfInodes; // regulates how many files could be created ( will be initially filled in super-block)
    private File file; // file to put file system in


    /**
     * Constructor creates configuration object for file system
     *
     * @param  size size of file system
     * @param  pageSize pageSize (and default segment size)
     * @param  numOfInodes regulates how many files could be created ( will be initially filled in super-block)
     * @param  file file to put file system in
     * @throws OneFileSystemException if file cannot be modified
     */
    public FileSystemConfiguration(long size, int pageSize, int numOfInodes, File file, boolean newFile) {
        if (newFile) {
            try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw")) {
                file.createNewFile();
                randomAccessFile.setLength(size);
            } catch (IOException e) {
                throw new OneFileSystemException("File system configuration failed, due to file modification!", e);
            }
        }
        this.size = size;
        this.pageSize = pageSize;
        this.numOfInodes = numOfInodes;
        this.file = file;
    }

    public static FileSystemConfiguration of(long size, int pageSize, int numOfInodes, File file, boolean newFile) {
        return new FileSystemConfiguration(size, pageSize, numOfInodes, file, newFile);
    }

    public long getSize() {
        return size;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getNumOfInodes() {
        return numOfInodes;
    }

    public File getFile() {
        return file;
    }
}
