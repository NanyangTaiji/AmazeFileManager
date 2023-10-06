package com.amaze.filemanager.filesystem;

import com.amaze.filemanager.application.AppConfig;

public class FilenameHelper {
    private static final String SLASH = "/";
    private static final String REGEX_RAW_NUMBERS = "| [0-9]+";
    private static final String REGEX_SOURCE = " \\((?:(another|[0-9]+(th|st|nd|rd)) )?copy\\)|copy( [0-9]+)?|\\.\\(incomplete\\)| \\([0-9]+\\)|[- ]+";

    private static final String[] ordinals = {"th", "st", "nd", "rd"};

    public static String pathDirname(String input) {
        if (input.contains(SLASH)) {
            return input.substring(0, input.lastIndexOf(SLASH));
        } else {
            return "";
        }
    }

    public static String pathBasename(String input) {
        if (input.contains(SLASH)) {
            return input.substring(input.lastIndexOf(SLASH) + 1);
        } else {
            return input;
        }
    }

    public static String pathFileBasename(String input) {
        String basename = pathBasename(input);
        if (basename.contains(".")) {
            return basename.substring(0, basename.lastIndexOf('.'));
        } else {
            return basename;
        }
    }

    public static String pathFileExtension(String input) {
        String basename = pathBasename(input);
        if (basename.contains(".")) {
            return basename.substring(basename.lastIndexOf('.') + 1);
        } else {
            return "";
        }
    }

    public enum FilenameFormatFlag {
        DARWIN, DEFAULT, WINDOWS, LINUX
    }

    public static String strip(String input, boolean removeRawNumbers) {
        String filepath = stripIncrementInternal(input, removeRawNumbers);
        String extension = pathFileExtension(filepath);
        String dirname = stripIncrementInternal(pathDirname(filepath), removeRawNumbers);
        String stem = stem(filepath, removeRawNumbers);
        StringBuilder result = new StringBuilder();
        if (!dirname.isEmpty()) {
            result.append(dirname).append(SLASH);
        }
        result.append(stem);
        if (!extension.isEmpty()) {
            result.append('.').append(extension);
        }
        return result.toString();
    }

    public static String toOrdinal(int n) {
        return n + ordinal(n);
    }

    public static HybridFile increment(HybridFile file) {
        return increment(file, FilenameFormatFlag.DEFAULT);
    }

    public static HybridFile increment(HybridFile file, FilenameFormatFlag platform) {
        return increment(file, platform, true);
    }

    public static HybridFile increment(HybridFile file, FilenameFormatFlag platform, boolean strip) {
        return increment(file, platform, strip, false);
    }

    public static HybridFile increment(HybridFile file, FilenameFormatFlag platform, boolean strip, boolean removeRawNumbers) {
        return increment(file, platform, strip, removeRawNumbers, 1);
    }

    public static HybridFile increment(HybridFile file, FilenameFormatFlag platform, boolean strip, boolean removeRawNumbers, int startArg) {
        String filename = file.getName(AppConfig.getInstance());
        String dirname = pathDirname(file.getPath());
        String basename = pathFileBasename(filename);
        String extension = pathFileExtension(filename);
        int start = startArg;

        if (strip) {
            filename = stripIncrementInternal(filename, removeRawNumbers);
            dirname = stripIncrementInternal(dirname, removeRawNumbers);
            basename = strip(basename, removeRawNumbers);
        }

        HybridFile retval = new HybridFile(
                file.getMode(),
                dirname,
                filename,
                file.isDirectory(AppConfig.getInstance())
        );

        while (retval.exists(AppConfig.getInstance())) {
            if (!extension.isEmpty()) {
                filename = format(platform, basename, start++) + "." + extension;
            } else {
                filename = format(platform, basename, start++);
            }
            retval = new HybridFile(
                    file.getMode(),
                    dirname,
                    filename,
                    file.isDirectory(AppConfig.getInstance())
            );
        }

        return retval;
    }

    private static String stripIncrementInternal(String input, boolean removeRawNumbers) {
        String source = REGEX_SOURCE;
        if (removeRawNumbers) {
            source += REGEX_RAW_NUMBERS;
        }
        return input.replaceAll("(" + source + ")+$", "");
    }

    private static String stem(String filepath, boolean removeRawNumbers) {
        String extension = pathFileExtension(filepath);
        return stripIncrementInternal(
                pathFileBasename(filepath.substring(0, filepath.lastIndexOf('.'))),
                removeRawNumbers
        );
    }

    private static String ordinal(int n) {
        int index = ((n % 100) - 20) % 10;
        if (index < 0 || index >= ordinals.length) {
            index = 0;
        }
        return ordinals[index];
    }

    private static String format(FilenameFormatFlag flag, String stem, int n) {
        switch (flag) {
            case DARWIN:
                if (n == 1) {
                    return stem + " copy";
                } else if (n > 1) {
                    return stem + " copy " + n;
                } else {
                    return stem;
                }
            case LINUX:
                if (n == 0) {
                    return stem;
                } else if (n == 1) {
                    return stem + " (copy)";
                } else if (n == 2) {
                    return stem + " (another copy)";
                } else {
                    return stem + " (" + toOrdinal(n) + " copy)";
                }
            default:
                if (n >= 1) {
                    return stem + " (" + n + ")";
                } else {
                    return stem;
                }
        }
    }
}

