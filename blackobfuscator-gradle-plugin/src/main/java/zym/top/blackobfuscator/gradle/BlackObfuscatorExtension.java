package io.github._0728lzy.zymproguardobfuscator.gradle;

import java.util.ArrayList;
import java.util.List;

public class BlackObfuscatorExtension {

    private boolean enabled = true;
    private boolean autoRun = false;
    private boolean autoFilter = false;
    private int depth = 1;
    private String packageName;
    private Object rulesFile;
    private String outputSuffix = "-blackobf";
    private boolean deleteOriginalApk = false;
    private List<String> variants = new ArrayList<String>();
    private boolean dptEnabled = false;
    private Object dptJar = "tools/dpt.jar";
    private String dptExcludeAbi = "x86,x86_64";
    private boolean dptDebug = false;
    private boolean dptDisableAcf = false;
    private boolean dptDumpCode = false;
    private boolean dptNoisyLog = false;
    private boolean dptKeepClasses = false;
    private boolean dptSmaller = false;
    private boolean dptVerifySign = false;
    private Object dptRulesFile;
    private Object dptProtectConfig;
    private String javaExecutable;
    private String javaHome;
    private String dptJavaExecutable;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoRun() {
        return autoRun;
    }

    public void setAutoRun(boolean autoRun) {
        this.autoRun = autoRun;
    }

    public boolean isAutoFilter() {
        return autoFilter;
    }

    public void setAutoFilter(boolean autoFilter) {
        this.autoFilter = autoFilter;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public Object getRulesFile() {
        return rulesFile;
    }

    public void setRulesFile(Object rulesFile) {
        this.rulesFile = rulesFile;
    }

    public String getOutputSuffix() {
        return outputSuffix;
    }

    public void setOutputSuffix(String outputSuffix) {
        this.outputSuffix = outputSuffix;
    }

    public boolean isDeleteOriginalApk() {
        return deleteOriginalApk;
    }

    public void setDeleteOriginalApk(boolean deleteOriginalApk) {
        this.deleteOriginalApk = deleteOriginalApk;
    }

    public List<String> getVariants() {
        return variants;
    }

    public void setVariants(List<String> variants) {
        this.variants = variants;
    }

    public boolean isDptEnabled() {
        return dptEnabled;
    }

    public void setDptEnabled(boolean dptEnabled) {
        this.dptEnabled = dptEnabled;
    }

    public Object getDptJar() {
        return dptJar;
    }

    public void setDptJar(Object dptJar) {
        this.dptJar = dptJar;
    }

    public String getDptExcludeAbi() {
        return dptExcludeAbi;
    }

    public void setDptExcludeAbi(String dptExcludeAbi) {
        this.dptExcludeAbi = dptExcludeAbi;
    }

    public boolean isDptDebug() {
        return dptDebug;
    }

    public void setDptDebug(boolean dptDebug) {
        this.dptDebug = dptDebug;
    }

    public boolean isDptDisableAcf() {
        return dptDisableAcf;
    }

    public void setDptDisableAcf(boolean dptDisableAcf) {
        this.dptDisableAcf = dptDisableAcf;
    }

    public boolean isDptDumpCode() {
        return dptDumpCode;
    }

    public void setDptDumpCode(boolean dptDumpCode) {
        this.dptDumpCode = dptDumpCode;
    }

    public boolean isDptNoisyLog() {
        return dptNoisyLog;
    }

    public void setDptNoisyLog(boolean dptNoisyLog) {
        this.dptNoisyLog = dptNoisyLog;
    }

    public boolean isDptKeepClasses() {
        return dptKeepClasses;
    }

    public void setDptKeepClasses(boolean dptKeepClasses) {
        this.dptKeepClasses = dptKeepClasses;
    }

    public boolean isDptSmaller() {
        return dptSmaller;
    }

    public void setDptSmaller(boolean dptSmaller) {
        this.dptSmaller = dptSmaller;
    }

    public boolean isDptVerifySign() {
        return dptVerifySign;
    }

    public void setDptVerifySign(boolean dptVerifySign) {
        this.dptVerifySign = dptVerifySign;
    }

    public Object getDptRulesFile() {
        return dptRulesFile;
    }

    public void setDptRulesFile(Object dptRulesFile) {
        this.dptRulesFile = dptRulesFile;
    }

    public Object getDptProtectConfig() {
        return dptProtectConfig;
    }

    public void setDptProtectConfig(Object dptProtectConfig) {
        this.dptProtectConfig = dptProtectConfig;
    }

    public String getJavaExecutable() {
        return javaExecutable;
    }

    public void setJavaExecutable(String javaExecutable) {
        this.javaExecutable = javaExecutable;
    }

    public String getJavaHome() {
        return javaHome;
    }

    public void setJavaHome(String javaHome) {
        this.javaHome = javaHome;
    }

    public String getDptJavaExecutable() {
        return dptJavaExecutable;
    }

    public void setDptJavaExecutable(String dptJavaExecutable) {
        this.dptJavaExecutable = dptJavaExecutable;
    }
}
