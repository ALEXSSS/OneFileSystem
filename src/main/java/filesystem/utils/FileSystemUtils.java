package filesystem.utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toCollection;

public class FileSystemUtils {

    public static List<String> pathToSteps(String path) {
        return Arrays.stream(URI.create(path).normalize().toString()
                .replace("/", " ").trim().split(" ")).collect(toCollection(ArrayList::new));
    }

    public static String getFileNameByPath(String path) {
        List<String> steps = pathToSteps(path);
        return steps.get(steps.size() - 1);
    }

    public static String getFileParent(String pathToFile) {
        int index = pathToFile.lastIndexOf('/');
        if (index == -1){
            return "";
        }
        return pathToFile.substring(0, pathToFile.lastIndexOf('/'));
    }

    public static String addToPath(String path, String fileName) {
        List<String> paths = pathToSteps(path);
        paths.add(fileName);
        return String.join("/", paths);
    }

    /**
     *
     * @param possibleAncestor path in file system
     * @param pathToCheck path to check
     * @return true if given path has possibleAncestor as its ancestor
     */
    public static boolean checkThatDirectoryAncestor(String possibleAncestor, String pathToCheck){
        String ancestorFormatted = URI.create(possibleAncestor)
                .normalize().toString().replace("/", " ").trim();

        String pathToCheckFormatted = URI.create(pathToCheck)
                .normalize().toString().replace("/", " ").trim();

        return pathToCheckFormatted.contains(ancestorFormatted);
    }
}
