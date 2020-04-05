package filesystem.manager;

import filesystem.entity.ByteStream;

import java.util.List;

/**
 * Api to work with files in oneFileSystem
 */
public interface InternalFileSystemInterface {

    /**
     * write bytes of data to the file
     * @param pathToFile where to write
     * @param data array of bytes to write
     */
    void writeToFile(String pathToFile, byte[] data);

    /**
     * Remember, that by convention of this file system, the first bytes are related to name of file.
     * Use {@code stream.getString()}. Though one file can have many different names (links), the first name preserved
     * in this file system.
     *
     * @param pathToFile to create byteStream
     * @return byteStream to read content from
     * @see ByteStream
     */
    ByteStream readFileByByteStream(String pathToFile);

    /**
     * It moves DEntry(any type of file) from one directory to another one.
     *
     * @param pathToFileParent directory where file is located
     * @param whereToMove directory to place file in
     * @param fileName name of file to move (file could be directory type)
     */
    void moveFileToDirectory(String pathToFileParent, String whereToMove, String fileName);

    /**
     *
     * @param path where file is located
     * @return list of file's names
     * @throws filesystem.entity.exception.FileManagerException if directory doesn't exist
     */
    List<String> getFileNamesInDirectory(String path);

    /**
     *
     * @param pathToFileParent directory where file should be located
     * @param fileName name of new directory
     */
    void createDirectory(String pathToFileParent, String fileName);

    /**
     * Consider to use this method instead of alternative method without size of file.
     * As this file system will make allocation more effective based on this information.
     * @param pathToFileParent directory where file should be located
     * @param fileName name of new file
     * @param size size of file
     */
    void createFile(String pathToFileParent, String fileName, long size);

    /**
     * If you now the size of file, then don't use this method, as version with size more effective
     * @param pathToFileParent directory where file should be located
     * @param fileName name of new file
     * @see #createFile(String, String, long)
     */
    void createFile(String pathToFileParent, String fileName);

    /**
     * Internally will add dEntry mapping to directory.
     *
     * @param pathToFile path to file
     * @param whereToAdd directory to add hardlink
     * @param nameOfHardLink name of this hardlink
     */
    void createHardLink(String pathToFile, String whereToAdd, String nameOfHardLink);

    /**
     * Method will copy file to directory. In comparison with creation of hardlink, it will be another file.
     * @param pathToFile path to be copied file
     * @param whereToCopy directory to copy file in
     * @param withName new name of file
     */
    void copyFileToDirectory(String pathToFile, String whereToCopy, String withName);

    /**
     * Removes file
     *
     * @param pathToFile path to file
     */
    void removeFile(String pathToFile);


    /**
     *
     * @return available num of pages in file system
     * @see #getSize()
     */
    int getSizeInPages();

    /**
     *
     * @return available num of bytes in file system
     * @see #getSizeInPages()
     */
    long getSize();
}
