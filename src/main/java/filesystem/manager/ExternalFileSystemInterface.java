package filesystem.manager;

import java.io.InputStream;
import java.io.OutputStream;


/**
 * It enables users to copy from and in one-file-system from their os specific file system
 */
public interface ExternalFileSystemInterface {

    /**
     * @param pathToFile to file in one-file-system
     * @param in         to copy data from
     */
    void writeToFileFromInputStream(String pathToFile, InputStream in);

    /**
     * @param pathToFile to file in one-file-system
     * @param out        to copy data in
     */
    void copyDataFromFileToOutputStream(String pathToFile, OutputStream out);
}
