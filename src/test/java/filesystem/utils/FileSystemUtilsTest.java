package filesystem.utils;

import filesystem.entity.config.FileSystemConfiguration;
import org.junit.Test;

import java.util.List;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class FileSystemUtilsTest {

    @Test
    public void pathToStepsTest() {
        List<String> expected = asList("a", "b", "d");
        assertThat(FileSystemUtils.pathToSteps("a/b/c/../d"), is(expected));
        assertThat(FileSystemUtils.pathToSteps("/a/b/c/../d"), is(expected));
        assertThat(FileSystemUtils.pathToSteps("./a/b/c/../d"), is(expected));
        assertThat(FileSystemUtils.pathToSteps("./a/b/c/../d/"), is(expected));
        assertThat(FileSystemUtils.pathToSteps("/./a/b/c/../d/"), is(expected));
        assertThat(FileSystemUtils.pathToSteps("/./a/b/c/c1/../../d/"), is(expected));
        assertThat(FileSystemUtils.pathToSteps("/./a/b/c2/c1/../../d/"), is(expected));
    }

    @Test
    public void getFileNameByPathTest() {
        assertEquals(FileSystemUtils.getFileNameByPath("./no/no/yes"), "yes");
        assertEquals(FileSystemUtils.getFileNameByPath("./yes"), "yes");
        assertEquals(FileSystemUtils.getFileNameByPath("yes"), "yes");
    }

    @Test
    public void getFileParentTest() {
        assertEquals(FileSystemUtils.getFileParent("./yes/no"), "./yes");
        assertEquals(FileSystemUtils.getFileParent("./yes"), ".");
        assertEquals(FileSystemUtils.getFileParent("/yes"), "");
        assertEquals(FileSystemUtils.getFileParent("yes"), "");
        assertEquals(FileSystemUtils.getFileParent(""), "");
    }

    @Test
    public void addToPathTest() {
        assertEquals(FileSystemUtils.addToPath("./no/no/", "yes"), "no/no/yes");
        assertEquals(FileSystemUtils.addToPath("./no/no", "yes"), "no/no/yes");
    }

    @Test
    public void checkThatDirectoryAncestor() {
        assertTrue(FileSystemUtils.checkThatDirectoryAncestor("./first", "./first/second/third"));
        assertTrue(FileSystemUtils.checkThatDirectoryAncestor("./first", "/first/second/third"));
        assertTrue(FileSystemUtils.checkThatDirectoryAncestor("/first", "./first/second/third"));
        assertTrue(FileSystemUtils.checkThatDirectoryAncestor("first", "./first/second/third"));
        assertTrue(FileSystemUtils.checkThatDirectoryAncestor("first", "/first/second/third"));
        assertTrue(FileSystemUtils.checkThatDirectoryAncestor("first", "first/second/third"));
        assertTrue(FileSystemUtils.checkThatDirectoryAncestor("first", "first/second/./third"));
        assertTrue(FileSystemUtils.checkThatDirectoryAncestor("first/./", "first/second/./third"));
        assertTrue(FileSystemUtils.checkThatDirectoryAncestor("first/././", "first/second/./third"));
        assertTrue(FileSystemUtils.checkThatDirectoryAncestor("././first/././", "first/second/./third"));
        assertTrue(FileSystemUtils.checkThatDirectoryAncestor("", "."));
    }

    @Test
    public void cleanFileNameTest() {
        assertEquals("first", FileSystemUtils.cleanFileName("/first"));
        assertEquals("first", FileSystemUtils.cleanFileName("/first/"));
        assertEquals("first", FileSystemUtils.cleanFileName("first"));
    }
}