package filesystem.entity.config;

import org.junit.Test;

import java.io.File;

public class FileSystemConfigurationTest {
    @Test(expected = IllegalArgumentException.class)
    public void FileSystemConfigurationWithSmallPageTest() {
        FileSystemConfiguration.of(1000000, 1023, 1000, new File(""), true, 10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void FileSystemConfigurationWithSmallSizeTest() {
        FileSystemConfiguration.of(1025 * 10, 1025, 10, new File(""), true, 10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void FileSystemConfigurationWithSmallAmountOfInodesTest() {
        FileSystemConfiguration.of(1025 * 10 + 1, 1025, 1, new File(""), true, 10);
    }
}