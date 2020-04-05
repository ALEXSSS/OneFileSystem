package filesystem.entity.filesystem;


import filesystem.entity.memorymarks.MemorySize;

@MemorySize(sizeInBytes = 1)
public enum FileType {
    DIRECTORY(0),
    FILE(1);

    private final int value;

    FileType(int value){
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static FileType getFileTypeFromInt(int fileType) {
        switch (fileType){
            case 0: return DIRECTORY;
            case 1: return FILE;
            default: throw new IllegalArgumentException("Only two file types are available!");
        }
    }

    public int FileTypeToInt(FileType type){
        return type.getValue();
    }
}
