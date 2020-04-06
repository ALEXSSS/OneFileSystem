# OneFileSystem

BUILD INFORMATION:
[![Build Status](https://travis-ci.com/ALEXSSS/OneFileSystem.svg?branch=master)](https://travis-ci.com/ALEXSSS/OneFileSystem)

## Description

#### Glossary of OneFileSystem

Page - the minimum allocated amount of memory (for example 4096 bytes).

Segment - 1 or more contiguous pages.

#### SuperBlock
![GitHub Logo](./doc/superblock.png)

SuperBlock is some amount of **inodes** which have following properties:

1) segment - start page where data is stored (pointing to the start of segment sequence)
2) size - size of stored data
3) counter - counter of hardlinks
4) fileType - only File and Directory are supported
5) lastSegment - pointer to the last segment in the segment sequence

#### Storage

That is memory splitted on pages and also service which can allocate data or release data for user. During data allocation storage will 
try to allocate data less fragmented by using eager algorithm. As well during release of segments storage will merge all 
splitted segments for internal representation and will make them free again.
 
#### FileManager

FileManager will use both **storage** and **superBlock** services to keep track of allocated segments, putting data inside and emulating
commonly used file abstractions like file, directory, hardlink.

#### How to use?

Initialise FileManager 

```
fileSystemConfiguration = FileSystemConfiguration.of(sifeOfFileSystem, sizeOfPage, numOfInodes, originalFile, true);
fileManager = new FileManager(fileSystemConfiguration);
```

After that you can create files and directories, providing where to create file or directory
as the first arg of `createDirectory` or `createFile` methods (`""` or `"."` is root).

```
fileManager.createDirectory("", "first"); // will create first directory in root
fileManager.createDirectory(".", "second"); // will create second directory in root
fileManager.createDirectory("./", "third"); // will create third directory in root
fileManager.createDirectory("./first", "fourth"); // will create fourth directory in first
fileManager.createDirectory("/first", "fifth"); // will create fifth directory in first

fileManager.createFile("./first/fifth", "someFile"); // will create file in directory /first/fifth with name someFile
```

Or you can create hardlink 

```
// it will create hardlink on file ./first/fifth in directory second with name fifthHardLink
fileManager.createHardLink("./first/fifth", "/second", "fifthHardLink");
```

You can easily read content of directory 

```
// method will return list [("fourth", DIRECTORY), ("fifth", DIRECTORY)]
fileManager.getFilesInDirectory("./first");
// if you add boolean parameter, then you get list with files' sizes
fileManager.getFilesInDirectory("./first", true);
```

Write to the file from InputStream and read from OutputStream

```
// you can use version of this method with size of data specified, as it more effective for big files
fileManager.createFile(".", "someFile"); // will create someFile in the root
fileManager.writeToFileFromInputStream("./someFile", new ByteArrayInputStream(new byte[]{1,2,3,4,5}));

ByteArrayOutputStream out = new ByteArrayOutputStream();
fileManager.copyDataFromFileToOutputStream("./someFile", out);

System.out.println(Arrays.toString(out.toByteArray()));
// [1,2,3,4,5]
```

Or you can use internal API to read and write data

```
fileManager.createFile(".", "anotherFile");
// writing byte arrays to files
fileManager.writeToFile("./anotherFile", new byte[]{1,2,3,4,5,6});
fileManager.writeToFile("./anotherFile", new byte[]{7,8,9,10});

ByteStream byteStream = fileManager.readFileByByteStream("./anotherFile");
// you need to care about it if you use only internal api
System.out.println("You read file: "+ byteStream.getString()); // as name is stored in the file first
while (byteStream.hasNext()){
      System.out.print(byteStream.getByte() + " ");
}
// 1 2 3 4 5 6 7 8 9 10 
```

To remove file simply write

```
fileManager.removeFile("./anotherFile");
```

To copy file you can do something like that ... 

