package zym.top.blackobfuscator.gradle;

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
}
