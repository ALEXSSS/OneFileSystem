# OneFileSystem

BUILD INFORMATION:
[![Build Status](https://travis-ci.com/ALEXSSS/OneFileSystem.svg?branch=master)](https://travis-ci.com/ALEXSSS/OneFileSystem)

## Description

#### Glossary of OneFileSystem

Page - the minimum allocated amount of memory (for example 4096 bytes).

Segment - 1 or more contiguous pages.

#### SuperBlock
![GitHub Logo](./doc/superblock.png)

SuperBlock is some amount of inodes which have following properties:

1) segment - start page where data is stored (pointing to the start of segment sequence)
2) size - size of stored data
3) counter - counter of hardlinks
4) fileType - only File and Directory are supported
5) lastSegment - pointer to the last segment in the segment sequence

#### Storage

That is memory splitted on pages and also service which can allocate data or release data for user. During data allocation storage will 
try to allocate data less fragmented by using eager algorithm. As well during release of segments storage will merge all 
splitted segments for internal representation and will make them free again.
 
### FileManager

FileManager will use both storage and superBlock services to keep track of allocated segments, putting data inside emulating
commonly used file abstractions like file, directory, hardlink.

