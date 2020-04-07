package filesystem.manager;

import filesystem.entity.ByteStream;
import filesystem.entity.config.FileSystemConfiguration;
import filesystem.entity.exception.FileManagerException;
import filesystem.entity.filesystem.BaseFileInf;
import filesystem.entity.filesystem.DirectoryReadResult;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static filesystem.entity.filesystem.FileType.DIRECTORY;
import static filesystem.entity.filesystem.FileType.FILE;
import static junitx.framework.FileAssert.assertBinaryEquals;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class FileManagerTest {

    private static int DEFAULT_SIZE_OF_PAGE = 4096;
    private static FileSystemConfiguration fileSystemConfiguration;
    private static FileManager fileManager;
    ClassLoader classLoader = getClass().getClassLoader();

    @Before
    public void init() throws IOException {
        long size = DEFAULT_SIZE_OF_PAGE * 100000;
        File originalFile = File.createTempFile("test", "test");
        originalFile.createNewFile();
        try (RandomAccessFile file = new RandomAccessFile(originalFile, "rw")) {
            file.setLength(size);
        }
        fileSystemConfiguration = FileSystemConfiguration.of(size, DEFAULT_SIZE_OF_PAGE, 100, originalFile, true);
        fileManager = new FileManager(fileSystemConfiguration);
        originalFile.deleteOnExit();
    }

    @Test
    public void fileManagerImageTest() throws IOException {
        assertThat(fileManager.getFilesNamesInDirectory("."), is(empty()));
        fileManager.createDirectory("", "first");
        fileManager.createDirectory("", "second");
        assertThat(fileManager.getFilesNamesInDirectory(""),
                containsInAnyOrder("first", "second"));

        fileManager.createDirectory("first/", "third");
        assertThat(fileManager.getFilesNamesInDirectory("first/"),
                containsInAnyOrder("third"));
        fileManager.createFile("second/", "smallFile", 500);

        assertThat(fileManager.getFilesNamesInDirectory("second/"),
                containsInAnyOrder("smallFile"));


        // it will copy image in oneFileSystem and back to another file
        try (InputStream in = classLoader.getResourceAsStream("test.jpg");) {
            fileManager.writeToFileFromInputStream("second/smallFile", in);
        }

        File copiedJpg = File.createTempFile("copied", "copied");
        try (OutputStream out = new FileOutputStream(copiedJpg)) {
            fileManager.copyDataFromFileToOutputStream("second/smallFile", out);
        }
        copiedJpg.deleteOnExit();

        assertBinaryEquals(new File(classLoader.getResource("test.jpg").getFile()), copiedJpg);
    }

    @Test
    public void copyFileImageTest() throws IOException {
        int initialSize = fileManager.getSizeInPages();
        fileManager.createDirectory("", "firstFolder");
        fileManager.createDirectory("", "secondFolder");

        fileManager.createFile("firstFolder/", "image", 1000);

        try (InputStream in = classLoader.getResourceAsStream("test.jpg");) {
            fileManager.writeToFileFromInputStream("firstFolder/image", in);
        }

        int sizeBeforeCopying = fileManager.getSizeInPages();

        fileManager.copyFileToDirectory("firstFolder/image", "secondFolder", "image");

        File copiedJpg = File.createTempFile("copied", "copied");
        try (OutputStream out = new FileOutputStream(copiedJpg)) {
            fileManager.copyDataFromFileToOutputStream("secondFolder/image", out);
        }
        copiedJpg.deleteOnExit();

        assertBinaryEquals(new File(classLoader.getResource("test.jpg").getFile()), copiedJpg);

        fileManager.removeFile("secondFolder/image");
        assertEquals("Memory leak", sizeBeforeCopying, fileManager.getSizeInPages());
        fileManager.removeFile("/firstFolder");
        fileManager.removeFile("/secondFolder");
        assertEquals("Memory leak", initialSize, fileManager.getSizeInPages());
    }

    @Test
    public void allocateComplexFileTree() throws IOException {
        File file = File.createTempFile("test2", "test");
        FileSystemConfiguration fileSystemConfiguration = FileSystemConfiguration.of(4096 * 4096 * 100, 1024, 1111, file, true);
        FileManager newFileManager = new FileManager(fileSystemConfiguration);

        long initialSize = newFileManager.getSizeInPages();
        for (int i = 0; i < 10; i++) {
            StringBuilder startDirectory = new StringBuilder("dir" + i);
            newFileManager.createDirectory(".", startDirectory.toString());
            for (int j = 0; j < 10; j++) {
                String newDirectory = "/dir" + i + "" + j;
                newFileManager.createDirectory("./" + startDirectory, newDirectory);
                for (int k = 0; k < 10; k++) {
                    newFileManager.createFile("./" + startDirectory, "file" + k, k * 1024);
                    newFileManager.writeToFile("./" + startDirectory + "/" + "file" + k, new byte[]{1,2,3,4,5});
                }
                startDirectory.append(newDirectory);
            }
        }

        newFileManager.removeFile("./dir0");
        newFileManager.removeFile("./dir1");
        newFileManager.removeFile("./dir2");
        newFileManager.removeFile("./dir3");
        newFileManager.removeFile("./dir4");
        newFileManager.removeFile("./dir5");
        newFileManager.removeFile("./dir6");
        newFileManager.removeFile("./dir7");
        newFileManager.removeFile("./dir8");
        newFileManager.removeFile("./dir9");

        assertEquals("Memory Leak", newFileManager.getSizeInPages(), initialSize);
    }


    @Test(expected = FileManagerException.class)
    public void fileTreeCreationTest() {
        fileManager.createDirectory("", "first");
        fileManager.createDirectory("first", "second");
        fileManager.createFile("first/second/", "someFile", 0);

        assertThat("Content checking", fileManager.getFilesNamesInDirectory("./first"),
                containsInAnyOrder("second"));

        assertThat("Content checking", fileManager.getFilesNamesInDirectory("./first/second"),
                containsInAnyOrder("someFile"));

        // will throw exception as someFile isn't directory
        fileManager.createFile("first/second/someFile", "someAnotherFile", 0);
    }

    @Test
    public void createHardLinkTest() throws IOException {
        fileManager.createDirectory("", "first");
        fileManager.createDirectory("first", "second");
        fileManager.createFile("first/second/", "someFile", 100);

        byte[] data = "Hello world!".getBytes();
        fileManager.writeToFileFromInputStream("first/second/someFile", new ByteArrayInputStream(data));

        fileManager.createDirectory("", "third");
        fileManager.createHardLink("first/second/someFile", "third/", "anotherName");

        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        fileManager.copyDataFromFileToOutputStream("third/anotherName", out);
        out.flush();
        assertArrayEquals("Content should be read from the same Inode", data, out.toByteArray());
    }

    @Test
    public void dontRemoveFileBeforeAllHardLinksAreFreeTest() {
        int initialSize = fileManager.getSizeInPages();

        fileManager.createFile(".", "popularFile", 500000);
        fileManager.createDirectory(".", "copy");
        fileManager.createHardLink("./popularFile", "./copy", "0");
        fileManager.createHardLink("./popularFile", "./copy", "1");
        fileManager.createHardLink("./popularFile", "./copy", "2");
        fileManager.createHardLink("./popularFile", "./copy", "3");
        fileManager.createHardLink("./popularFile", "./copy", "4");
        fileManager.createHardLink("./popularFile", "./copy", "5");
        fileManager.createHardLink("./popularFile", "./copy", "6");
        fileManager.createHardLink("./popularFile", "./copy", "7");
        fileManager.createHardLink("./popularFile", "./copy", "8");
        fileManager.createHardLink("./popularFile", "./copy", "9");

        assertThat("Content checking", fileManager.getFilesNamesInDirectory("./copy"),
                containsInAnyOrder("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"));

        int afterCreationSize = fileManager.getSizeInPages();

        fileManager.removeFile("./copy/0");
        fileManager.removeFile("./copy/1");
        fileManager.removeFile("./copy/2");
        fileManager.removeFile("./copy/3");
        fileManager.removeFile("./copy/4");
        fileManager.removeFile("./copy/5");
        fileManager.removeFile("./copy/6");
        fileManager.removeFile("./copy/7");
        fileManager.removeFile("./copy/8");

        assertEquals(afterCreationSize, fileManager.getSizeInPages());

        assertThat("Content checking", fileManager.getFilesNamesInDirectory("./copy"),
                containsInAnyOrder("9"));

        fileManager.removeFile("./copy/9");
        fileManager.removeFile("/copy");
        fileManager.removeFile("/popularFile");

        assertEquals("Memory leak", initialSize, fileManager.getSizeInPages());
    }

    @Test
    public void removeFileTest() {
        int size = fileManager.getSizeInPages();

        fileManager.createDirectory("", "first");
        fileManager.createDirectory("first", "second");

        fileManager.createFile("first/second/", "someFile", 100);

        for (int i = 0; i < 1000; i++) {
            assertThat(fileManager.getFilesNamesInDirectory("first/second/"),
                    containsInAnyOrder("someFile"));

            fileManager.removeFile("first/second/someFile");

            assertThat(fileManager.getFilesNamesInDirectory("first/second/"),
                    is(empty()));

            fileManager.createFile("first/second/", "someFile", 100);
        }

        fileManager.removeFile("first/second/someFile");
        fileManager.removeFile("first/second");
        fileManager.removeFile("/first");
        assertEquals("Size hasn't changed!", size, fileManager.getSizeInPages());
    }

    @Test
    public void removeDeepFilesStructureTest() {
        int size = fileManager.getSizeInPages();

        String currentPath = ".";
        for (int i = 0; i < 49; i++) {
            String newAddedDirectory = Integer.valueOf(i).toString();
            fileManager.createDirectory(currentPath, newAddedDirectory);
            if (i != 0) {
                fileManager.createDirectory(currentPath, newAddedDirectory + i);
            }
            currentPath += ("/" + newAddedDirectory);
        }
        fileManager.removeFile("./0");

        // inodes will be freed and again
        currentPath = ".";
        for (int i = 0; i < 49; i++) {
            String newAddedDirectory = Integer.valueOf(i).toString();
            fileManager.createDirectory(currentPath, newAddedDirectory);
            if (i != 0) {
                fileManager.createDirectory(currentPath, newAddedDirectory + i);
            }
            currentPath += ("/" + newAddedDirectory);
        }
        fileManager.removeFile("./0");


        assertThat(fileManager.getFilesNamesInDirectory("."),
                is(empty()));

        assertEquals("Size should be the same", size, fileManager.getSizeInPages());
    }

    @Test
    public void replaceContentOfDirectoriesTest() {
        int initialSize = fileManager.getSizeInPages();
        int numOfFiles = 45;
        fileManager.createDirectory(".", "first");
        fileManager.createDirectory("./first", "sub1");

        fileManager.createDirectory(".", "second");
        fileManager.createDirectory("./second", "sub2");

        String patternFirst = "first";
        String patternSecond = "second";

        List<String> firstNames = new ArrayList<>(numOfFiles);
        List<String> secondNames = new ArrayList<>(numOfFiles);
        for (int i = 0; i < numOfFiles; i++) {
            String fileNameFirst = patternFirst + i;
            String fileNameSecond = patternSecond + i;
            fileManager.createFile("./first/sub1", fileNameFirst, 500);
            fileManager.createFile("./second/sub2", patternSecond + i, 500);
            firstNames.add(fileNameFirst);
            secondNames.add(fileNameSecond);
        }

        int sizeAfterCreation = fileManager.getSizeInPages();

        assertThat(fileManager.getFilesNamesInDirectory("./first/sub1"),
                is(firstNames));
        assertThat(fileManager.getFilesNamesInDirectory("./second/sub2"),
                is(secondNames));

        fileManager.moveFileToDirectory("./first", "./second", "sub1");
        fileManager.moveFileToDirectory("./second", "./first", "sub2");

        assertThat(fileManager.getFilesNamesInDirectory("./first/sub2"),
                is(secondNames));
        assertThat(fileManager.getFilesNamesInDirectory("./second/sub1"),
                is(firstNames));

        assertThat(fileManager.getFilesNamesInDirectory("./first"),
                containsInAnyOrder("sub2"));
        assertThat(fileManager.getFilesNamesInDirectory("./second"),
                containsInAnyOrder("sub1"));

        assertEquals("Memory leak", sizeAfterCreation, fileManager.getSizeInPages());

        fileManager.removeFile("./first");
        fileManager.removeFile("./second");

        assertEquals("Memory leak", initialSize, fileManager.getSizeInPages());
    }

    @Test
    public void moveFileToDirectoryTest() {
        int startSize = fileManager.getSizeInPages();
        fileManager.createDirectory(".", "first");
        fileManager.createFile("./first", "someFile", 5000);
        fileManager.createDirectory(".", "second");
        fileManager.moveFileToDirectory("./first", "./second", "someFile");

        int sizeAllocated = fileManager.getSizeInPages();
        for (int i = 0; i < 1000; i++) {
            fileManager.moveFileToDirectory("./second", "./first", "someFile");
            fileManager.moveFileToDirectory("./first", "./second", "someFile");
        }

        assertEquals(sizeAllocated, fileManager.getSizeInPages());

        fileManager.removeFile("/first");
        fileManager.removeFile("/second");

        assertEquals("Memory leak", startSize, fileManager.getSizeInPages());
    }

    @Test
    public void writeToFileTest() {
        fileManager.createFile("", "first");
        byte[] data = {1, 2, 3, 4, 5, 6};
        byte[] toReadIn = {1, 2, 3, 4, 5, 6};
        fileManager.writeToFile("first", data);
        ByteStream stream = fileManager.readFileByByteStream("first");

        assertEquals("File name checking!", "first", stream.getString());

        stream.getArr(toReadIn);
        assertArrayEquals("Data checking", toReadIn, data);
    }

    @Test
    public void getFilesInDirectoryTest() {
        fileManager.createDirectory("", "first"); // will create first directory in root
        fileManager.createDirectory(".", "second"); // will create second directory in root
        fileManager.createDirectory("./", "third"); // will create third directory in root
        fileManager.createDirectory("./first", "fourth"); // will create fourth directory in first
        fileManager.createFile("/first", "fifth"); // will create fifth directory in first

        assertThat(fileManager.getFilesInDirectory("./first", false),
                containsInAnyOrder(DirectoryReadResult.of("fourth", DIRECTORY, 0), DirectoryReadResult.of("fifth", FILE, 0)));
    }

    @Test
    public void getFileSizeTest() {
        byte[] data = new byte[5000];

        String firstFileName = "someFile";
        String secondFileName = "anotherFile";
        int nameSizeFirst = BaseFileInf.of(firstFileName).toByteArray().length;
        int nameSizeSecond = BaseFileInf.of(secondFileName).toByteArray().length;

        fileManager.createDirectory("", "first");
        fileManager.createDirectory("first", "second");
        fileManager.createDirectory("first/second", "third");
        fileManager.createFile("first/second/third/", firstFileName);
        fileManager.writeToFile("first/second/third/" + firstFileName, data);

        assertEquals(data.length + nameSizeFirst, fileManager.getFileSize("first/second/third/" + firstFileName));

        fileManager.createFile("first/", secondFileName);
        fileManager.writeToFile("./first/" + secondFileName, data);

        assertEquals(2 * data.length + nameSizeFirst + nameSizeSecond, fileManager.getFileSize("first"));
        assertEquals(2 * data.length + nameSizeFirst + nameSizeSecond, fileManager.getFileSize(""));
        assertEquals(2 * data.length + nameSizeFirst + nameSizeSecond, fileManager.getFileSize("."));
    }

    @Test
    public void createCyclicReferenceOnFileTest() {
        fileManager.createDirectory("", "first");
        fileManager.createDirectory("first", "second");
        fileManager.createDirectory("first/second", "third");
        fileManager.createFile("first/", "someFile");
        fileManager.createHardLink("first/someFile", "/first/second/third/", "cyclicRefCorrect");
    }

    @Test(expected = FileManagerException.class)
    public void getFileSizeOfNotExistedFileTest() {
        fileManager.getFileSize("first");
    }

    @Test(expected = FileManagerException.class)
    public void createCyclicReferenceOnDirectoryTest() {
        fileManager.createDirectory("", "first");
        fileManager.createDirectory("first", "second");
        fileManager.createDirectory("first/second", "third");
        fileManager.createHardLink("first/second", "/first/second/third/", "cyclicRefCorrect");
    }

    @Test(expected = FileManagerException.class)
    public void writeToNotExistingFileTest() {
        fileManager.writeToFile("first", new byte[]{1, 2, 3, 4, 5, 6});
    }

    @Test(expected = FileManagerException.class)
    public void writeToDirectoryTest() {
        fileManager.createDirectory("", "first");
        fileManager.writeToFile("first", new byte[]{1, 2, 3, 4, 5, 6});
    }

    @Test(expected = FileManagerException.class)
    public void moveFileToNotExistedDirectoryTest() {
        fileManager.createDirectory(".", "first");
        fileManager.createFile("./first", "someFile", 5000);
        fileManager.moveFileToDirectory("./first", "./second", "someFile");
    }

    @Test(expected = FileManagerException.class)
    public void moveFileToNotExistedFileTest() {
        fileManager.createDirectory(".", "first");
        fileManager.createFile("./first", "someFile", 5000);
        fileManager.moveFileToDirectory("./first", "./second", "someFile1");
    }

    @Test(expected = FileManagerException.class)
    public void createEmptyNameDirectoryTest() {
        fileManager.createDirectory(".", "");
    }

    @Test(expected = FileManagerException.class)
    public void createDirectoryInNotExistingDirectoryTest() {
        fileManager.createDirectory("./first/", "second");
    }

    @Test(expected = FileManagerException.class)
    public void createFileInNotExistingDirectoryTest() {
        fileManager.createFile("./first/", "second");
    }

    @Test(expected = FileManagerException.class)
    public void createFileWithEmptyNameTest() {
        fileManager.createFile(".", "", 1);
    }

    @Test(expected = FileManagerException.class)
    public void createHardLinkOnNotExistedFileTest() {
        fileManager.createFile(".", "1", 1);
        fileManager.createDirectory(".", "2");
        fileManager.createHardLink("./3", "2", "2");
    }

    @Test(expected = FileManagerException.class)
    public void createEmptyHardLinkTest() {
        fileManager.createFile(".", "1", 1);
        fileManager.createDirectory(".", "2");
        fileManager.createHardLink("./1", "2", "1");
        fileManager.createHardLink("./1", "2", "");
    }

    @Test(expected = FileManagerException.class)
    public void createHardLinkWithTheSameNameTest() {
        fileManager.createFile("", "first", 5000);
        fileManager.createHardLink("", "", "1");
        fileManager.createHardLink("", "", "2");
        fileManager.createHardLink("", "", "2");
    }

    @Ignore
    @Test
    public void forDocTest() {
        fileManager.createDirectory("", "first"); // will create first directory in root
        fileManager.createDirectory(".", "second"); // will create second directory in root
        fileManager.createDirectory("./", "third"); // will create third directory in root
        fileManager.createDirectory("./first", "fourth"); // will create fourth directory in first
        fileManager.createDirectory("/first", "fifth"); // will create fifth directory in first

        fileManager.createFile("./first/fifth", "someFile"); // will create file in directory /first/fifth with name someFile
        fileManager.createHardLink("./first/fifth", "/second", "fifthHardLink");

        fileManager.getFilesInDirectory("./first");


        // you can use version of this method with size of data specified, as it more effective for big files
        fileManager.createFile(".", "someFile"); // will create someFile in the root
        fileManager.writeToFileFromInputStream("./someFile", new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5}));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        fileManager.copyDataFromFileToOutputStream("./someFile", out);

        System.out.println(Arrays.toString(out.toByteArray()));
        // [1,2,3,4,5]

        // internal api
        fileManager.createFile(".", "anotherFile");
        fileManager.writeToFile("./anotherFile", new byte[]{1, 2, 3, 4, 5, 6});
        fileManager.writeToFile("./anotherFile", new byte[]{7, 8, 9, 10});

        ByteStream byteStream = fileManager.readFileByByteStream("./anotherFile");
        // you need to care about it if you use only internal api
        System.out.println("You read file: " + byteStream.getString()); // as name is stored in the file first
        while (byteStream.hasNext()) {
            System.out.print(byteStream.getByte() + " ");
        }
        System.out.println();

        // remove file
        fileManager.removeFile("./anotherFile");
        // or whole directory
        fileManager.removeFile("./second");

        // copy file

        // creation of what will be copied
        fileManager.createFile("", "willBeCopied");
        // let's write some data inside to check
        fileManager.writeToFile("./willBeCopied", new byte[]{1, 2, 3, 4, 5});

        // creation of directory to copy in
        fileManager.createDirectory("./", "second");
        fileManager.createDirectory("./second", "toCopyIn");

        // how to copy
        fileManager.copyFileToDirectory("./willBeCopied", "./second/toCopyIn", "ThatIsCopiedFile");

        ByteStream byteStreamOfCopiedFile = fileManager.readFileByByteStream("./second/toCopyIn/ThatIsCopiedFile");
        // reading copied file
        System.out.println("You read file: " + byteStreamOfCopiedFile.getString()); // as name is stored in the file first
        while (byteStreamOfCopiedFile.hasNext()) {
            System.out.print(byteStreamOfCopiedFile.getByte() + " ");
        }

        // to get size of file, but remember that first bytes used to store file's name

        fileManager.getFileSize("./second/toCopyIn/ThatIsCopiedFile");
    }

    @Ignore
    @Test
    public void forDocImageTest() throws IOException {
        fileManager.createFile(".", "smallFile");
        // it will copy image in oneFileSystem and back to another file
        try (InputStream in = classLoader.getResourceAsStream("test.jpg");) {
            fileManager.writeToFileFromInputStream("/smallFile", in);
        }

        File copiedJpg = new File("./doc/copied.jpg");
        try (OutputStream out = new FileOutputStream(copiedJpg)) {
            fileManager.copyDataFromFileToOutputStream("/smallFile", out);
        }

    }
}