package filesystem.entity.filesystem;

import org.junit.Test;

import static org.junit.Assert.*;

public class FileTypeTest {

    @Test
    public void fileTypeTest() {
        // all possible values of enum are represented in one byte
        assertTrue(FileType.values().length <= 255);
    }
}