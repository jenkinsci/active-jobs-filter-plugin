package io.jenkins.plugins.activejobsfilter;

public enum JobType {
    ALL("All"),
    PIPELINE("Pipeline"),
    MULTIBRANCH_PIPELINE("Multibranch pipeline"),
    FREESTYLE("Freestyle");

    private final String displayName;

    JobType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
