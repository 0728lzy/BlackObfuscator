package top.niunaijun.blackobfuscator.gradle;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.api.ApplicationVariant;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

public class BlackObfuscatorGradlePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        final BlackObfuscatorExtension extension = project.getExtensions()
                .create("blackObfuscator", BlackObfuscatorExtension.class);

        project.afterEvaluate(p -> {
            if (!p.getPlugins().hasPlugin("com.android.application")) {
                throw new GradleException("top.niunaijun.blackobfuscator requires the com.android.application plugin.");
            }

            AppExtension android = p.getExtensions().findByType(AppExtension.class);
            if (android == null) {
                throw new GradleException("Android AppExtension not found.");
            }

            android.getApplicationVariants().all(variant -> registerVariantTask(project, extension, variant));
        });
    }

    private void registerVariantTask(Project project, BlackObfuscatorExtension extension, ApplicationVariant variant) {
        String variantName = variant.getName();
        String taskName = "blackObfuscate" + capitalize(variantName);

        BlackObfuscateApkTask task = project.getTasks().create(taskName, BlackObfuscateApkTask.class);
        task.setGroup("blackobfuscator");
        task.setDescription("Obfuscate APK dex files for the " + variantName + " variant.");
        task.setExtension(extension);
        task.setVariantName(variantName);
        task.setVariantDirName(variant.getDirName());
        task.setProjectBuildDir(project.getBuildDir());
        task.setRootDir(project.getRootDir());
        task.setSigningInfo(extractSigningInfo(variant));

        task.dependsOn(variant.getAssembleProvider());

        if (extension.isAutoRun() && shouldHandleVariant(extension, variantName)) {
            variant.getAssembleProvider().configure(assembleTask -> assembleTask.finalizedBy(task));
        }
    }

    private VariantSigningInfo extractSigningInfo(ApplicationVariant variant) {
        Object signingConfig = variant.getSigningConfig();
        if (signingConfig == null) {
            return null;
        }

        try {
            Method getStoreFile = signingConfig.getClass().getMethod("getStoreFile");
            Method getStorePassword = signingConfig.getClass().getMethod("getStorePassword");
            Method getKeyAlias = signingConfig.getClass().getMethod("getKeyAlias");
            Method getKeyPassword = signingConfig.getClass().getMethod("getKeyPassword");

            File storeFile = (File) getStoreFile.invoke(signingConfig);
            String storePassword = (String) getStorePassword.invoke(signingConfig);
            String keyAlias = (String) getKeyAlias.invoke(signingConfig);
            String keyPassword = (String) getKeyPassword.invoke(signingConfig);

            if (keyPassword == null) {
                keyPassword = storePassword;
            }

            if (storeFile == null || storePassword == null || keyAlias == null) {
                return null;
            }

            return new VariantSigningInfo(storeFile, storePassword, keyAlias, keyPassword);
        } catch (Exception e) {
            throw new GradleException("Failed to read signing config for variant " + variant.getName(), e);
        }
    }

    private boolean shouldHandleVariant(BlackObfuscatorExtension extension, String variantName) {
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

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }
}
