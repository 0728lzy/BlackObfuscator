package top.niunaijun.blackobfuscator.gradle;

import java.io.File;

class VariantSigningInfo {
    private final File storeFile;
    private final String storePassword;
    private final String keyAlias;
    private final String keyPassword;

    VariantSigningInfo(File storeFile, String storePassword, String keyAlias, String keyPassword) {
        this.storeFile = storeFile;
        this.storePassword = storePassword;
        this.keyAlias = keyAlias;
        this.keyPassword = keyPassword;
    }

    File getStoreFile() {
        return storeFile;
    }

    String getStorePassword() {
        return storePassword;
    }

    String getKeyAlias() {
        return keyAlias;
    }

    String getKeyPassword() {
        return keyPassword;
    }
}
