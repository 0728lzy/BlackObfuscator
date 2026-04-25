package top.niunaijun.blackobfuscator.gradle;

import com.googlecode.dex2jar.tools.BlackObfuscatorCmd;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class BlackObfuscateApkTask extends DefaultTask {

    private BlackObfuscatorExtension extension;
    private String variantName;
    private String variantDirName;
    private File projectBuildDir;
    private File rootDir;
    private VariantSigningInfo signingInfo;

    @TaskAction
    public void obfuscate() {
        if (extension == null || !extension.isEnabled()) {
            getLogger().lifecycle("BlackObfuscator is disabled.");
            return;
        }

        if (!shouldHandleVariant()) {
            getLogger().lifecycle("Skipping variant {} because it is not selected.", variantName);
            return;
        }

        validateConfiguration();

        File inputApk = locateInputApk();
        File workDir = new File(projectBuildDir, "blackobfuscator/" + variantName);
        File dexInputDir = new File(workDir, "dex-in");
        File dexOutputDir = new File(workDir, "dex-out");
        File unsignedApk = new File(workDir, inputApk.getName().replace(".apk", "-unsigned.apk"));
        File alignedApk = new File(workDir, inputApk.getName().replace(".apk", "-aligned.apk"));
        File outputApk = new File(inputApk.getParentFile(), buildOutputName(inputApk.getName()));

        recreateDir(workDir);
        mkdirs(dexInputDir);
        mkdirs(dexOutputDir);

        Map<String, File> replacementDexMap = extractAndObfuscateDex(inputApk, dexInputDir, dexOutputDir);
        if (replacementDexMap.isEmpty()) {
            throw new GradleException("No dex files were obfuscated. Check blackObfuscator.packageName or rulesFile.");
        }

        repackageApk(inputApk, unsignedApk, replacementDexMap);
        File zipalign = findRequiredBuildTool("zipalign");
        File apksigner = findRequiredBuildTool("apksigner");

        runCommand(workDir, zipalign.getAbsolutePath(), "-f", "-p", "4",
                unsignedApk.getAbsolutePath(), alignedApk.getAbsolutePath());
        signApk(workDir, apksigner, alignedApk, outputApk);
        runCommand(workDir, apksigner.getAbsolutePath(), "verify", "--verbose", outputApk.getAbsolutePath());
        runCommand(workDir, zipalign.getAbsolutePath(), "-c", "-p", "4", outputApk.getAbsolutePath());

        getLogger().lifecycle("BlackObfuscator output: {}", outputApk.getAbsolutePath());
    }

    private Map<String, File> extractAndObfuscateDex(File inputApk, File dexInputDir, File dexOutputDir) {
        Map<String, File> replacementDexMap = new HashMap<String, File>();
        List<String> dexEntries = new ArrayList<String>();
        Logger logger = getLogger();

        try (ZipFile zipFile = new ZipFile(inputApk)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (isDexEntry(entry.getName())) {
                    dexEntries.add(entry.getName());
                    File extracted = new File(dexInputDir, new File(entry.getName()).getName());
                    try (InputStream in = new BufferedInputStream(zipFile.getInputStream(entry));
                         OutputStream out = new BufferedOutputStream(new FileOutputStream(extracted))) {
                        copy(in, out);
                    }
                }
            }
        } catch (IOException e) {
            throw new GradleException("Failed to extract dex files from " + inputApk, e);
        }

        if (dexEntries.isEmpty()) {
            throw new GradleException("No classes*.dex entries found in " + inputApk.getAbsolutePath());
        }

        for (String dexEntry : dexEntries) {
            File extractedDex = new File(dexInputDir, new File(dexEntry).getName());
            File obfuscatedDex = new File(dexOutputDir, "obf-" + extractedDex.getName());

            logger.lifecycle("Obfuscating {} for variant {}", extractedDex.getName(), variantName);
            List<String> args = new ArrayList<String>();
            args.add("d2j-black-obfuscator");
            args.add("-d");
            args.add(String.valueOf(extension.getDepth()));
            args.add("-i");
            args.add(extractedDex.getAbsolutePath());
            args.add("-o");
            args.add(obfuscatedDex.getAbsolutePath());

            File rulesFile = resolveRulesFile();
            if (rulesFile != null) {
                args.add("-a");
                args.add(rulesFile.getAbsolutePath());
            } else {
                args.add("-p");
                args.add(extension.getPackageName());
            }

            args.add("run");
            BlackObfuscatorCmd.main(args.toArray(new String[0]));

            if (obfuscatedDex.isFile()) {
                replacementDexMap.put(dexEntry, obfuscatedDex);
            }
        }
        return replacementDexMap;
    }

    private void repackageApk(File inputApk, File outputApk, Map<String, File> replacementDexMap) {
        try (ZipFile zipFile = new ZipFile(inputApk);
             ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outputApk)))) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (isSignatureEntry(entry.getName())) {
                    continue;
                }

                File replacement = replacementDexMap.get(entry.getName());
                if (replacement != null) {
                    writeZipEntry(zipOutputStream, entry, replacement);
                } else {
                    writeZipEntry(zipOutputStream, entry, zipFile.getInputStream(entry));
                }
            }
        } catch (IOException e) {
            throw new GradleException("Failed to repackage APK " + inputApk.getAbsolutePath(), e);
        }
    }

    private void writeZipEntry(ZipOutputStream out, ZipEntry original, File replacement) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(replacement))) {
            writeZipEntry(out, original, in, replacement.length(), computeCrc(replacement));
        }
    }

    private void writeZipEntry(ZipOutputStream out, ZipEntry original, InputStream in) throws IOException {
        writeZipEntry(out, original, in, original.getSize(), original.getCrc());
    }

    private void writeZipEntry(ZipOutputStream out, ZipEntry original, InputStream in, long size, long crc) throws IOException {
        ZipEntry target = new ZipEntry(original.getName());
        target.setTime(original.getTime());
        target.setComment(original.getComment());
        target.setExtra(original.getExtra());

        if (original.getMethod() == ZipEntry.STORED) {
            target.setMethod(ZipEntry.STORED);
            target.setSize(size);
            target.setCompressedSize(size);
            target.setCrc(crc);
        } else {
            target.setMethod(ZipEntry.DEFLATED);
        }

        out.putNextEntry(target);
        copy(in, out);
        out.closeEntry();
    }

    private void signApk(File workDir, File apksigner, File alignedApk, File outputApk) {
        if (signingInfo == null) {
            throw new GradleException("No Android signingConfig found for variant " + variantName + ".");
        }

        runCommand(workDir, apksigner.getAbsolutePath(),
                "sign",
                "--v1-signing-enabled", "false",
                "--v2-signing-enabled", "true",
                "--v3-signing-enabled", "false",
                "--v4-signing-enabled", "false",
                "--ks", signingInfo.getStoreFile().getAbsolutePath(),
                "--ks-key-alias", signingInfo.getKeyAlias(),
                "--ks-pass", "pass:" + signingInfo.getStorePassword(),
                "--key-pass", "pass:" + signingInfo.getKeyPassword(),
                "--out", outputApk.getAbsolutePath(),
                alignedApk.getAbsolutePath());
    }

    private File findRequiredBuildTool(String toolName) {
        File sdkDir = getAndroidSdkDir();
        File buildToolsRoot = new File(sdkDir, "build-tools");
        if (!buildToolsRoot.isDirectory()) {
            throw new GradleException("Android build-tools directory not found under " + sdkDir.getAbsolutePath());
        }

        File[] candidates = buildToolsRoot.listFiles();
        if (candidates == null || candidates.length == 0) {
            throw new GradleException("No build-tools versions found under " + buildToolsRoot.getAbsolutePath());
        }

        File latest = null;
        for (File candidate : candidates) {
            if (!candidate.isDirectory()) {
                continue;
            }
            if (latest == null || candidate.getName().compareTo(latest.getName()) > 0) {
                latest = candidate;
            }
        }

        if (latest == null) {
            throw new GradleException("No build-tools versions found under " + buildToolsRoot.getAbsolutePath());
        }

        String executableName = isWindows() ? toolName + (toolName.equals("apksigner") ? ".bat" : ".exe") : toolName;
        File executable = new File(latest, executableName);
        if (!executable.isFile()) {
            throw new GradleException("Required build tool not found: " + executable.getAbsolutePath());
        }
        return executable;
    }

    private File getAndroidSdkDir() {
        File sdkDir = null;
        Object sdkDirProp = getProject().findProperty("android.sdkDirectory");
        if (sdkDirProp instanceof File) {
            sdkDir = (File) sdkDirProp;
        }
        if (sdkDir != null && sdkDir.isDirectory()) {
            return sdkDir;
        }

        File localProperties = new File(rootDir, "local.properties");
        if (localProperties.isFile()) {
            try (InputStream in = new FileInputStream(localProperties)) {
                Properties properties = new Properties();
                properties.load(in);
                String sdkDirValue = properties.getProperty("sdk.dir");
                if (sdkDirValue != null) {
                    File file = new File(sdkDirValue);
                    if (file.isDirectory()) {
                        return file;
                    }
                }
            } catch (IOException ignored) {
            }
        }

        String[] envCandidates = {
                System.getenv("ANDROID_SDK_ROOT"),
                System.getenv("ANDROID_HOME")
        };
        for (String candidate : envCandidates) {
            if (candidate != null) {
                File file = new File(candidate);
                if (file.isDirectory()) {
                    return file;
                }
            }
        }

        throw new GradleException("Android SDK not found. Set sdk.dir in local.properties or ANDROID_SDK_ROOT.");
    }

    private void runCommand(File workDir, String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .directory(workDir)
                    .inheritIO()
                    .start();

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new GradleException("Command failed with exit code " + exitCode + ": " + join(command));
            }
        } catch (IOException e) {
            throw new GradleException("Failed to run command: " + join(command), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GradleException("Interrupted while running command: " + join(command), e);
        }
    }

    private File locateInputApk() {
        File apkDir = new File(projectBuildDir, "outputs/apk");
        if (!apkDir.isDirectory()) {
            throw new GradleException("APK output directory not found: " + apkDir.getAbsolutePath());
        }

        List<File> candidates = new ArrayList<File>();
        collectApks(apkDir, candidates);

        File selected = null;
        for (File candidate : candidates) {
            String normalizedPath = candidate.getAbsolutePath().replace('\\', '/').toLowerCase(Locale.ROOT);
            String normalizedVariant = variantDirName.replace('\\', '/').toLowerCase(Locale.ROOT);
            if (!normalizedPath.contains("/" + normalizedVariant.toLowerCase(Locale.ROOT) + "/")) {
                continue;
            }
            if (selected == null || candidate.lastModified() > selected.lastModified()) {
                selected = candidate;
            }
        }

        if (selected == null) {
            throw new GradleException("Could not locate APK for variant " + variantName + " under " + apkDir.getAbsolutePath());
        }

        return selected;
    }

    private void collectApks(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collectApks(file, result);
            } else if (file.getName().toLowerCase(Locale.ROOT).endsWith(".apk")) {
                result.add(file);
            }
        }
    }

    private File resolveRulesFile() {
        Object rulesFile = extension.getRulesFile();
        if (rulesFile == null) {
            return null;
        }
        return getProject().file(rulesFile);
    }

    private boolean shouldHandleVariant() {
        List<String> variants = extension.getVariants();
        if (variants == null || variants.isEmpty()) {
            return true;
        }
        for (String candidate : variants) {
            if (variantName.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void validateConfiguration() {
        if (extension.getDepth() < 1) {
            throw new GradleException("blackObfuscator.depth must be >= 1");
        }

        File rulesFile = resolveRulesFile();
        boolean hasPackage = extension.getPackageName() != null && !extension.getPackageName().trim().isEmpty();
        boolean hasRules = rulesFile != null;

        if (hasPackage == hasRules) {
            throw new GradleException("Set exactly one of blackObfuscator.packageName or blackObfuscator.rulesFile.");
        }

        if (hasRules && !rulesFile.isFile()) {
            throw new GradleException("blackObfuscator.rulesFile does not exist: " + rulesFile.getAbsolutePath());
        }
    }

    private String buildOutputName(String inputName) {
        String suffix = extension.getOutputSuffix();
        int dotIndex = inputName.toLowerCase(Locale.ROOT).lastIndexOf(".apk");
        if (dotIndex < 0) {
            return inputName + suffix + ".apk";
        }
        return inputName.substring(0, dotIndex) + suffix + ".apk";
    }

    private boolean isDexEntry(String entryName) {
        return entryName.matches("classes(\\d+)?\\.dex");
    }

    private boolean isSignatureEntry(String entryName) {
        String upper = entryName.toUpperCase(Locale.ROOT);
        return upper.startsWith("META-INF/")
                && (upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC")
                || upper.endsWith(".SF") || upper.endsWith(".MF"));
    }

    private long computeCrc(File file) throws IOException {
        CRC32 crc32 = new CRC32();
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                crc32.update(buffer, 0, read);
            }
        }
        return crc32.getValue();
    }

    private void recreateDir(File dir) {
        if (dir.exists()) {
            deleteRecursively(dir.toPath());
        }
        mkdirs(dir);
    }

    private void deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try {
            Files.walk(path)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException e) {
            throw new GradleException("Failed to clean work directory " + path, e);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                throw new GradleException("Failed to clean work directory " + path, e.getCause());
            }
            throw e;
        }
    }

    private void mkdirs(File dir) {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new GradleException("Failed to create directory " + dir.getAbsolutePath());
        }
    }

    private void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
    }

    private String join(String[] command) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < command.length; i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(command[i]);
        }
        return builder.toString();
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    public void setExtension(BlackObfuscatorExtension extension) {
        this.extension = extension;
    }

    public void setVariantName(String variantName) {
        this.variantName = variantName;
    }

    public void setVariantDirName(String variantDirName) {
        this.variantDirName = variantDirName;
    }

    public void setProjectBuildDir(File projectBuildDir) {
        this.projectBuildDir = projectBuildDir;
    }

    public void setRootDir(File rootDir) {
        this.rootDir = rootDir;
    }

    public void setSigningInfo(VariantSigningInfo signingInfo) {
        this.signingInfo = signingInfo;
    }
}
