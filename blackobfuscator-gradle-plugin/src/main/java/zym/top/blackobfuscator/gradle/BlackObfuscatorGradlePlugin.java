package zym.top.blackobfuscator.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class BlackObfuscatorGradlePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        final BlackObfuscatorExtension extension = project.getExtensions()
                .create("blackObfuscator", BlackObfuscatorExtension.class);

        project.getPlugins().withId("com.android.application", plugin ->
                project.afterEvaluate(p -> registerVariantTasks(project, extension)));
    }

    private void registerVariantTasks(Project project, BlackObfuscatorExtension extension) {
        Object androidExtension = project.getExtensions().findByName("android");
        if (androidExtension == null) {
            throw new GradleException("Android extension not found.");
        }

        Object variantsObject = invoke(androidExtension, "getApplicationVariants");
        if (!(variantsObject instanceof Collection)) {
            throw new GradleException("Application variants are not available on the Android extension.");
        }

        Collection<?> variants = (Collection<?>) variantsObject;
        for (Object variant : variants) {
            registerVariantTask(project, extension, variant);
        }
    }

    private void registerVariantTask(Project project, BlackObfuscatorExtension extension, Object variant) {
        String variantName = String.valueOf(invoke(variant, "getName"));
        String taskName = "blackObfuscate" + capitalize(variantName);
        String prepareTaskName = "prepareBlackObfuscator" + capitalize(variantName);
        String variantDirName = String.valueOf(invoke(variant, "getDirName"));
        VariantSigningInfo signingInfo = extractSigningInfo(variant);
        String namespace = extractNamespace(project);
        File generatedAutoFilterFile = new File(project.getBuildDir(), "blackobfuscator-auto-filter/" + variantName + "/filter.txt");

        TaskProvider<BlackObfuscatorAutoFilterTask> prepareTaskProvider = project.getTasks().register(prepareTaskName, BlackObfuscatorAutoFilterTask.class, task -> {
            task.setGroup("blackobfuscator");
            task.setDescription("Snapshot source classes for the " + variantName + " BlackObfuscator auto filter.");
            task.setExtension(extension);
            task.setVariantName(variantName);
            task.setGeneratedRulesFile(generatedAutoFilterFile);
            task.setNamespace(namespace);
        });

        TaskProvider<BlackObfuscateApkTask> taskProvider = project.getTasks().register(taskName, BlackObfuscateApkTask.class, task -> {
            task.setGroup("blackobfuscator");
            task.setDescription("Obfuscate APK dex files for the " + variantName + " variant.");
            task.setExtension(extension);
            task.setVariantName(variantName);
            task.setVariantDirName(variantDirName);
            task.setProjectBuildDir(project.getBuildDir());
            task.setRootDir(project.getRootDir());
            task.setNamespace(namespace);
            task.setSigningInfo(signingInfo);
            task.setGeneratedAutoFilterFile(generatedAutoFilterFile);
        });

        Object assembleProvider = findAssembleProvider(variant);
        if (assembleProvider != null) {
            taskProvider.configure(task -> task.dependsOn(assembleProvider));
            configureDependsOn(assembleProvider, prepareTaskProvider);
        } else {
            taskProvider.configure(task -> task.dependsOn(prepareTaskProvider));
        }

        if (extension.isAutoRun() && shouldHandleVariant(extension, variantName)) {
            if (assembleProvider instanceof TaskProvider) {
                ((TaskProvider<?>) assembleProvider).configure(assembleTask -> assembleTask.finalizedBy(taskProvider));
            } else if (assembleProvider instanceof Task) {
                ((Task) assembleProvider).finalizedBy(taskProvider);
            }
        }
    }

    private void configureDependsOn(Object taskObject, TaskProvider<?> dependency) {
        if (taskObject instanceof TaskProvider) {
            ((TaskProvider<?>) taskObject).configure(task -> task.dependsOn(dependency));
        } else if (taskObject instanceof Task) {
            ((Task) taskObject).dependsOn(dependency);
        }
    }

    private Object findAssembleProvider(Object variant) {
        try {
            return invoke(variant, "getAssembleProvider");
        } catch (GradleException ignored) {
        }

        try {
            return invoke(variant, "getAssemble");
        } catch (GradleException ignored) {
            return null;
        }
    }

    private VariantSigningInfo extractSigningInfo(Object variant) {
        Object signingConfig = invoke(variant, "getSigningConfig");
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
            throw new GradleException("Failed to read signing config for variant " + invoke(variant, "getName"), e);
        }
    }

    private String extractNamespace(Project project) {
        Object androidExtension = project.getExtensions().findByName("android");
        if (androidExtension == null) {
            return null;
        }

        try {
            Object namespace = invoke(androidExtension, "getNamespace");
            if (namespace instanceof String) {
                String value = ((String) namespace).trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        } catch (GradleException ignored) {
        }
        return null;
    }

    private Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (NoSuchMethodException e) {
            throw new GradleException("Method not found: " + methodName + " on " + target.getClass().getName(), e);
        } catch (IllegalAccessException e) {
            throw new GradleException("Cannot access method: " + methodName + " on " + target.getClass().getName(), e);
        } catch (InvocationTargetException e) {
            throw new GradleException("Failed to invoke method: " + methodName + " on " + target.getClass().getName(), e.getCause());
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
