import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;

/**
 * @author luca
 * 
 */
public class NakedBackup {

    private static final String DEL_PREFIX = "~del.";
    private static final String BAK_PREFIX = "~bak.";
    private static final String SLASH = System.getProperty("file.separator");
    private static final String USAGE = "Usage: java " + NakedBackup.class.getSimpleName()
            + " [-v][-sb][-sh] destDir srcDir1 [[srcDir2] ...]";

    /**
     * Print an error message and exit
     * 
     * @param message
     * @param printUsage
     */
    private static void exitWithError(String message, boolean printUsage) {
        System.err.println(message);
        if (printUsage) {
            System.out.println(USAGE);
        }
        System.exit(1);
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            // Print usage help
            System.out.println(USAGE);
            return;
        }

        // Setup processing switches
        boolean verbose = false;
        boolean skipBackup = false;
        boolean skipHidden = false;

        int idx = 0;
        while (args[idx].startsWith("-")) {
            String arg = args[idx++];
            switch (arg) {
            case "-v":
                verbose = true;
                break;
            case "-sb":
                skipBackup = true;
                break;
            case "-sh":
                skipHidden = true;
                break;
            default:
                exitWithError("Unknown option " + arg, true);
            }
        }

        if (args.length < idx + 2) {
            exitWithError("At least one source and one destination directories are required", true);
        }

        // Determine source and destination directories
        String destDir = args[idx++];
        ArrayList<String> srcDirs = new ArrayList<String>();
        for (int i = idx; i < args.length; i++) {
            String dirName = args[i];
            File file = new File(dirName);
            if (!file.exists()) {
                exitWithError("Source directory " + dirName + " does not exist", false);
            }
            if (!file.isDirectory()) {
                exitWithError("Source " + dirName + " is not a directory", false);
            }
            if (!file.canRead()) {
                exitWithError("Source directory " + dirName + " is not readable", false);
            }
            if (srcDirs.contains(dirName)) {
                exitWithError("Source directory " + dirName + " is duplicated", false);
            }

            if (dirName.endsWith(SLASH)) {
                srcDirs.add(dirName.substring(0, dirName.length() - 1));
            } else {
                srcDirs.add(dirName);
            }
        }

        // Perform backup
        new NakedBackup().execute(destDir, srcDirs, verbose, skipBackup, skipHidden);
    }

    /**
     * Perform backup
     * 
     * @param skipHidden
     * @param skipBackup
     * @param verbose
     * @param srcDirs
     * @param destDir
     */
    private void execute(String destDirectory, ArrayList<String> srcDirectories, boolean verbose, boolean skipBackup,
            boolean skipHidden) {
        if (verbose) {
            System.out.println("verbose=" + verbose);
            System.out.println("skipBackup=" + skipBackup);
            System.out.println("skipHidden=" + skipHidden);
            System.out.println("destDir=" + destDirectory);
            System.out.println("srcDirs=");
            for (int i = 0; i < srcDirectories.size(); i++) {
                System.out.println(String.format("%3d: ", i + 1) + srcDirectories.get(i));
            }
            System.out.println();
        }
        try {
            // Create first level destination directory
            File destFile = new File(destDirectory);
            mkdir(destFile);

            // Loop through all source directories and process them
            for (String srcDir : srcDirectories) {
                File srcFile = new File(srcDir);
                if (!(skipHidden && srcFile.isHidden())) {
                    process(srcFile, new File(destFile, srcFile.getName()), verbose, skipHidden, skipBackup);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param srcDir
     * @param srcFile
     * @return
     */
    private String getRelativePath(File srcDir, File srcFile) {
        return srcFile.getAbsolutePath().substring(srcDir.getAbsolutePath().length());
    }

    /**
     * @param srcFile
     * @param destFile
     * @return
     */
    private boolean isFileChanged(File srcFile, File destFile) {
        return srcFile.length() != destFile.length() || srcFile.lastModified() != destFile.lastModified();
    }

    /**
     * @param destFile
     * @throws IOException
     */
    private void mkdir(File destFile) throws IOException {
        if (!destFile.exists()) {
            destFile.mkdir();
        }
        if (!destFile.isDirectory()) {
            throw new IOException(destFile.getName() + " already exists but it is not a directory");
        }
    }

    /**
     * @param verbose
     * @param skipHidden
     * @param skipBackup
     * @param srcName
     * @param destName
     * @throws IOException
     */
    private void process(File srcDir, File destDir, boolean verbose, boolean skipHidden, boolean skipBackup)
            throws IOException {
        if (verbose) {
            System.out.println("Now processing " + srcDir.getAbsolutePath() + " -> " + destDir.getAbsolutePath());
        }

        mkdir(destDir);
        // Loop through all files and directories in source directory
        for (File srcFile : srcDir.listFiles()) {
            if (srcFile.isDirectory() && !(skipHidden && srcFile.isHidden())) {
                // Process any subdirectories
                process(srcFile, new File(destDir, srcFile.getName()), verbose, skipHidden, skipBackup);
            } else if (!skipHidden && !srcFile.isHidden()) {
                // Process files in current directory
                File destFile = new File(destDir, srcFile.getName());
                if (!destFile.exists()) {
                    // File does not exist in destination directory - copy as is
                    if (verbose) {
                        System.out.print("    " + getRelativePath(srcDir, srcFile) + "    new file copy...");
                    }
                    Files.copy(srcFile.toPath(), destFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                    if (verbose) {
                        System.out.println("done!");
                    }
                } else if (isFileChanged(srcFile, destFile)) {
                    // File exists but size or date are different - make a
                    // backup if not disabled
                    if (verbose) {
                        System.out.print("    " + getRelativePath(srcDir, srcFile) + "    existing file copy...");
                    }
                    if (!skipBackup) {
                        File backupFile = new File(destDir, BAK_PREFIX + srcFile.getName());
                        Files.move(destFile.toPath(), backupFile.toPath(), LinkOption.NOFOLLOW_LINKS);
                        if (verbose) {
                            System.out.print("backup made...");
                        }
                    }
                    Files.copy(srcFile.toPath(), destFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES,
                            StandardCopyOption.REPLACE_EXISTING);
                    if (verbose) {
                        System.out.println("done!");
                    }
                } else {
                    // File exists and is the same as in source directory - do
                    // nothing
                    if (verbose) {
                        System.out.println("    " + getRelativePath(srcDir, srcFile) + "    skipped!");
                    }
                }
            }
        }

        // Loop through all files in destination directory to check if any was
        // deleted since last backup
        for (File destFile : destDir.listFiles()) {
            if (!destFile.isDirectory() && !destFile.getName().startsWith("~") && !(skipHidden && destFile.isHidden())) {
                File srcFile = new File(srcDir, destFile.getName());
                if (!srcFile.exists()) {
                    if (verbose) {
                        System.out.println("    " + getRelativePath(destDir, destFile) + "    marked as deleted");
                    }
                    File deletedFile = new File(destDir, DEL_PREFIX + destFile.getName());
                    Files.move(destFile.toPath(), deletedFile.toPath(), LinkOption.NOFOLLOW_LINKS);
                }
            }
        }
    }

}
