package io.github._0728lzy.zymproguardobfuscator.gradle;

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
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private String namespace;
    private VariantSigningInfo signingInfo;
    private File generatedAutoFilterFile;
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*)*)\\s*;?\\s*$");

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

        File mergedRulesFile = null;
        if (extension.isAutoFilter()) {
            mergedRulesFile = resolveAutoFilterRulesFile();
            if (mergedRulesFile == null || !mergedRulesFile.isFile()) {
                mergedRulesFile = generateAutoRulesFile(new File(workDir, "filter.txt"));
            }
            getLogger().lifecycle("BlackObfuscator auto filter: {}", mergedRulesFile.getAbsolutePath());
        }

        Map<String, File> replacementDexMap = extractAndObfuscateDex(inputApk, dexInputDir, dexOutputDir, mergedRulesFile);
        if (replacementDexMap.isEmpty()) {
            throw new GradleException("No dex files were obfuscated. Check blackObfuscator.packageName, rulesFile or autoFilter.");
        }

        repackageApk(inputApk, unsignedApk, replacementDexMap);
        File zipalign = findRequiredBuildTool("zipalign");
        File apksigner = findRequiredBuildTool("apksigner");

        runCommand(workDir, zipalign.getAbsolutePath(), "-f", "-p", "4",
                unsignedApk.getAbsolutePath(), alignedApk.getAbsolutePath());
        signApk(workDir, apksigner, alignedApk, outputApk);
        runCommand(workDir, apksigner.getAbsolutePath(), "verify", "--verbose", outputApk.getAbsolutePath());
        runCommand(workDir, zipalign.getAbsolutePath(), "-c", "-p", "4", outputApk.getAbsolutePath());

        if (extension.isDptEnabled()) {
            File dptProtectedApk = runDpt(workDir, outputApk);
            runCommand(workDir, apksigner.getAbsolutePath(), "verify", "--verbose", dptProtectedApk.getAbsolutePath());
            runCommand(workDir, zipalign.getAbsolutePath(), "-c", "-p", "4", dptProtectedApk.getAbsolutePath());
            replaceOutputApk(outputApk, dptProtectedApk);
        }

        deleteOriginalApkIfRequested(inputApk, outputApk);
        getLogger().lifecycle("BlackObfuscator output: {}", outputApk.getAbsolutePath());
    }

    private Map<String, File> extractAndObfuscateDex(File inputApk, File dexInputDir, File dexOutputDir, File autoRulesFile) {
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

            File rulesFile = resolveRulesFile(autoRulesFile);
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

    private File runDpt(File workDir, File inputApk) {
        File dptJar = requireConfiguredFile(extension.getDptJar(), "blackObfuscator.dptJar");
        File dptOutputDir = new File(workDir, "dpt");
        File dptAlignedApk = new File(workDir, replaceApkSuffix(inputApk.getName(), "-dpt-aligned.apk"));
        File dptProtectedApk = new File(workDir, replaceApkSuffix(inputApk.getName(), "-dpt.apk"));

        recreateDir(dptOutputDir);

        List<String> command = new ArrayList<String>();
        command.add(resolveJavaExecutable().getAbsolutePath());
        command.add("-jar");
        command.add(dptJar.getAbsolutePath());
        command.add("-f");
        command.add(inputApk.getAbsolutePath());
        command.add("-o");
        command.add(dptOutputDir.getAbsolutePath());
        command.add("-x");

        if (extension.isDptDebug()) {
            command.add("--debug");
        }
        if (extension.isDptDisableAcf()) {
            command.add("--disable-acf");
        }
        if (extension.isDptDumpCode()) {
            command.add("--dump-code");
        }
        if (extension.isDptNoisyLog()) {
            command.add("--noisy-log");
        }
        if (extension.isDptKeepClasses()) {
            command.add("-K");
        }
        if (extension.isDptSmaller()) {
            command.add("-S");
        }
        if (extension.isDptVerifySign()) {
            command.add("-vs");
        }
        if (extension.getDptExcludeAbi() != null && !extension.getDptExcludeAbi().trim().isEmpty()) {
            command.add("-e");
            command.add(extension.getDptExcludeAbi().trim());
        }

        File dptRulesFile = resolveConfiguredFile(extension.getDptRulesFile());
        if (dptRulesFile != null) {
            command.add("-r");
            command.add(dptRulesFile.getAbsolutePath());
        }

        File dptProtectConfig = resolveConfiguredFile(extension.getDptProtectConfig());
        if (dptProtectConfig != null) {
            command.add("-c");
            command.add(dptProtectConfig.getAbsolutePath());
        }

        runCommand(workDir, command.toArray(new String[0]));

        File generatedDptApk = findLatestApk(dptOutputDir);
        if (generatedDptApk == null) {
            throw new GradleException("dpt-shell did not generate an APK in " + dptOutputDir.getAbsolutePath());
        }

        File zipalign = findRequiredBuildTool("zipalign");
        File apksigner = findRequiredBuildTool("apksigner");

        runCommand(workDir, zipalign.getAbsolutePath(), "-f", "-p", "4",
                generatedDptApk.getAbsolutePath(), dptAlignedApk.getAbsolutePath());
        signApk(workDir, apksigner, dptAlignedApk, dptProtectedApk);
        return dptProtectedApk;
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
        List<File> roots = new ArrayList<File>();
        addSearchRoot(roots, new File(projectBuildDir, "outputs/apk"));
        addSearchRoot(roots, new File(getProject().getProjectDir(), variantDirName));
        addSearchRoot(roots, new File(getProject().getProjectDir(), variantName));
        addSearchRoot(roots, new File(getProject().getProjectDir(), "release"));
        addSearchRoot(roots, new File(getProject().getProjectDir(), "debug"));

        List<File> candidates = new ArrayList<File>();
        for (File root : roots) {
            collectApks(root, candidates);
        }

        File selected = null;
        for (File candidate : candidates) {
            String normalizedPath = candidate.getAbsolutePath().replace('\\', '/').toLowerCase(Locale.ROOT);
            String normalizedVariant = variantDirName.replace('\\', '/').toLowerCase(Locale.ROOT);
            String normalizedName = candidate.getName().toLowerCase(Locale.ROOT);
            String normalizedVariantName = variantName.toLowerCase(Locale.ROOT);
            String normalizedSuffix = extension.getOutputSuffix() == null ? "" : extension.getOutputSuffix().toLowerCase(Locale.ROOT);
            boolean pathMatchesVariant = normalizedPath.contains("/" + normalizedVariant.toLowerCase(Locale.ROOT) + "/");
            boolean nameMatchesVariant = normalizedName.contains(normalizedVariantName);
            boolean isGeneratedOutput = !normalizedSuffix.isEmpty() && normalizedName.contains(normalizedSuffix);
            if ((!pathMatchesVariant && !nameMatchesVariant) || isGeneratedOutput) {
                continue;
            }
            if (selected == null || candidate.lastModified() > selected.lastModified()) {
                selected = candidate;
            }
        }

        if (selected == null) {
            throw new GradleException("Could not locate APK for variant " + variantName + ". Searched: " + joinFiles(roots));
        }

        return selected;
    }

    private void addSearchRoot(List<File> roots, File root) {
        if (root == null || !root.isDirectory()) {
            return;
        }
        String path = root.getAbsolutePath();
        for (File existing : roots) {
            if (existing.getAbsolutePath().equals(path)) {
                return;
            }
        }
        roots.add(root);
    }

    private void collectApks(File dir, List<File> result) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
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

    private void deleteOriginalApkIfRequested(File inputApk, File outputApk) {
        if (!extension.isDeleteOriginalApk()) {
            return;
        }
        if (inputApk == null || outputApk == null) {
            return;
        }
        if (inputApk.getAbsolutePath().equals(outputApk.getAbsolutePath())) {
            return;
        }
        try {
            Files.deleteIfExists(inputApk.toPath());
            getLogger().lifecycle("BlackObfuscator deleted original APK: {}", inputApk.getAbsolutePath());
        } catch (IOException e) {
            throw new GradleException("Failed to delete original APK " + inputApk.getAbsolutePath(), e);
        }
    }

    private File resolveRulesFile(File autoRulesFile) {
        if (autoRulesFile != null) {
            return autoRulesFile;
        }
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

        File rulesFile = resolveRulesFile(null);
        boolean hasPackage = extension.getPackageName() != null && !extension.getPackageName().trim().isEmpty();
        boolean hasRules = rulesFile != null;
        boolean hasAutoFilter = extension.isAutoFilter();
        if (hasRules && !rulesFile.isFile()) {
            throw new GradleException("blackObfuscator.rulesFile does not exist: " + rulesFile.getAbsolutePath());
        }

        if (!hasPackage && !hasAutoFilter && !hasRules) {
            throw new GradleException("Set at least one of blackObfuscator.packageName, blackObfuscator.rulesFile or blackObfuscator.autoFilter.");
        }

        if (hasPackage && hasRules) {
            throw new GradleException("blackObfuscator.packageName cannot be used together with blackObfuscator.rulesFile.");
        }

        if (hasPackage && hasAutoFilter) {
            throw new GradleException("blackObfuscator.packageName cannot be used together with blackObfuscator.autoFilter.");
        }

        if (hasAutoFilter) {
            String autoFilterNamespace = resolveNamespace();
            if (autoFilterNamespace == null || autoFilterNamespace.trim().isEmpty()) {
                throw new GradleException("blackObfuscator.autoFilter requires android.namespace to be configured.");
            }
        }
    }

    private File generateAutoRulesFile(File outputFile) {
        String autoFilterNamespace = resolveNamespace();
        Set<String> rules = collectAutoFilterRules(autoFilterNamespace);
        File configuredRulesFile = resolveRulesFile(null);
        if (rules.isEmpty() && configuredRulesFile == null) {
            throw new GradleException("No source packages or files found under namespace " + autoFilterNamespace + " for autoFilter.");
        }

        File parent = outputFile.getParentFile();
        if (parent != null) {
            mkdirs(parent);
        }

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8")) {
            writer.write("# Auto generated by BlackObfuscator\n");
            writer.write("# namespace: " + autoFilterNamespace + "\n");
            for (String rule : rules) {
                writer.write(rule);
                writer.write('\n');
            }
            appendConfiguredRules(writer, configuredRulesFile);
        } catch (IOException e) {
            throw new GradleException("Failed to generate auto filter rules file: " + outputFile.getAbsolutePath(), e);
        }
        return outputFile;
    }

    private File resolveAutoFilterRulesFile() {
        Object rulesFile = extension.getRulesFile();
        if (rulesFile != null) {
            return getProject().file(rulesFile);
        }
        return generatedAutoFilterFile;
    }

    private List<String> readAutoFilterRulesFile() {
        File autoFilterRulesFile = resolveAutoFilterRulesFile();
        if (autoFilterRulesFile == null || !autoFilterRulesFile.isFile()) {
            return Collections.emptyList();
        }
        try {
            List<String> rules = new ArrayList<String>();
            List<String> lines = Files.readAllLines(autoFilterRulesFile.toPath());
            for (String line : lines) {
                String rule = line.trim();
                if (rule.isEmpty() || rule.startsWith("#")) {
                    continue;
                }
                rules.add(rule);
            }
            return rules;
        } catch (IOException e) {
            throw new GradleException("Failed to read auto filter rules file: " + autoFilterRulesFile.getAbsolutePath(), e);
        }
    }

    private void appendConfiguredRules(Writer writer, File configuredRulesFile) throws IOException {
        if (configuredRulesFile == null) {
            return;
        }

        writer.write('\n');
        writer.write("# User rules: ");
        writer.write(configuredRulesFile.getAbsolutePath());
        writer.write('\n');

        List<String> lines = Files.readAllLines(configuredRulesFile.toPath());
        for (String line : lines) {
            writer.write(line);
            writer.write('\n');
        }
    }

    private Set<String> collectAutoFilterRules(String autoFilterNamespace) {
        List<String> autoFilterRules = readAutoFilterRulesFile();
        if (!autoFilterRules.isEmpty()) {
            return new LinkedHashSet<String>(autoFilterRules);
        }

        File srcDir = new File(getProject().getProjectDir(), "src");
        if (!srcDir.isDirectory()) {
            return Collections.emptySet();
        }

        Set<String> rules = new LinkedHashSet<String>();
        List<File> sources = new ArrayList<File>();
        collectSourceFiles(srcDir, sources);

        for (File source : sources) {
            String declaredPackage = readDeclaredPackage(source);
            if (declaredPackage == null || !isNamespaceMatch(declaredPackage, autoFilterNamespace)) {
                continue;
            }

            String simpleName = source.getName();
            int dot = simpleName.lastIndexOf('.');
            if (dot > 0) {
                simpleName = simpleName.substring(0, dot);
            }
            if (!simpleName.isEmpty()) {
                rules.add(declaredPackage + "." + simpleName);
            }
        }
        return rules;
    }

    private void collectSourceFiles(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                if ("build".equals(file.getName())) {
                    continue;
                }
                collectSourceFiles(file, result);
            } else if (isSourceFile(file)) {
                result.add(file);
            }
        }
    }

    private boolean isSourceFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".java") || name.endsWith(".kt");
    }

    private String readDeclaredPackage(File source) {
        try {
            String content = new String(Files.readAllBytes(source.toPath()), "UTF-8");
            Matcher matcher = PACKAGE_PATTERN.matcher(content);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private File resolveConfiguredFile(Object configuredPath) {
        if (configuredPath == null) {
            return null;
        }
        return getProject().file(configuredPath);
    }

    private File requireConfiguredFile(Object configuredPath, String propertyName) {
        File file = resolveConfiguredFile(configuredPath);
        if (file == null || !file.isFile()) {
            throw new GradleException(propertyName + " does not exist: " + (file == null ? configuredPath : file.getAbsolutePath()));
        }
        return file;
    }

    private boolean isNamespaceMatch(String packageName, String autoFilterNamespace) {
        return packageName.equals(autoFilterNamespace) || packageName.startsWith(autoFilterNamespace + ".");
    }

    private String resolveNamespace() {
        if (namespace != null && !namespace.trim().isEmpty()) {
            return namespace.trim();
        }
        return readManifestPackage();
    }

    private String readManifestPackage() {
        File manifest = new File(getProject().getProjectDir(), "src/main/AndroidManifest.xml");
        if (!manifest.isFile()) {
            return null;
        }
        try {
            String content = new String(Files.readAllBytes(manifest.toPath()), "UTF-8");
            Matcher matcher = Pattern.compile("package\\s*=\\s*\"([^\"]+)\"").matcher(content);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private String buildOutputName(String inputName) {
        String suffix = extension.getOutputSuffix();
        int dotIndex = inputName.toLowerCase(Locale.ROOT).lastIndexOf(".apk");
        if (dotIndex < 0) {
            return inputName + suffix + ".apk";
        }
        return inputName.substring(0, dotIndex) + suffix + ".apk";
    }

    private String replaceApkSuffix(String inputName, String newSuffix) {
        int dotIndex = inputName.toLowerCase(Locale.ROOT).lastIndexOf(".apk");
        if (dotIndex < 0) {
            return inputName + newSuffix;
        }
        return inputName.substring(0, dotIndex) + newSuffix;
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

    private File findLatestApk(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return null;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        File latest = null;
        for (File file : files) {
            if (!file.isFile() || !file.getName().toLowerCase(Locale.ROOT).endsWith(".apk")) {
                continue;
            }
            if (latest == null || file.lastModified() > latest.lastModified()) {
                latest = file;
            }
        }
        return latest;
    }

    private void replaceOutputApk(File outputApk, File replacementApk) {
        if (outputApk.getAbsolutePath().equals(replacementApk.getAbsolutePath())) {
            return;
        }
        try {
            Files.move(replacementApk.toPath(), outputApk.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new GradleException("Failed to replace final APK " + outputApk.getAbsolutePath(), e);
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

    private String joinFiles(List<File> files) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < files.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(files.get(i).getAbsolutePath());
        }
        return builder.toString();
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private File resolveJavaExecutable() {
        List<File> executableCandidates = new ArrayList<File>();
        if (extension.getDptJavaExecutable() != null && !extension.getDptJavaExecutable().trim().isEmpty()) {
            executableCandidates.add(getProject().file(extension.getDptJavaExecutable().trim()));
        }
        if (extension.getJavaExecutable() != null && !extension.getJavaExecutable().trim().isEmpty()) {
            executableCandidates.add(getProject().file(extension.getJavaExecutable().trim()));
        }
        for (File candidate : executableCandidates) {
            if (candidate.isFile()) {
                return candidate;
            }
        }

        List<File> javaHomes = new ArrayList<File>();
        addJavaHomeCandidate(javaHomes, extension.getJavaHome());
        addJavaHomeCandidate(javaHomes, System.getenv("JAVA_HOME"));
        addJavaHomeCandidate(javaHomes, System.getProperty("java.home"));

        String javaCommand = isWindows() ? "java.exe" : "java";
        for (File javaHomeDir : javaHomes) {
            File binCandidate = new File(javaHomeDir, "bin" + File.separator + javaCommand);
            if (binCandidate.isFile()) {
                return binCandidate;
            }
            File directCandidate = new File(javaHomeDir, javaCommand);
            if (directCandidate.isFile()) {
                return directCandidate;
            }
        }

        throw new GradleException(
                "Java executable not found. Configure blackObfuscator.javaExecutable, " +
                        "blackObfuscator.javaHome or blackObfuscator.dptJavaExecutable."
        );
    }

    private void addJavaHomeCandidate(List<File> candidates, String rawPath) {
        if (rawPath == null) {
            return;
        }
        String trimmed = rawPath.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        candidates.add(getProject().file(trimmed));
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

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public void setSigningInfo(VariantSigningInfo signingInfo) {
        this.signingInfo = signingInfo;
    }

    public void setGeneratedAutoFilterFile(File generatedAutoFilterFile) {
        this.generatedAutoFilterFile = generatedAutoFilterFile;
    }
}
