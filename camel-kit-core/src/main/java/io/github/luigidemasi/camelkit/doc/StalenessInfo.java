package io.github.luigidemasi.camelkit.doc;

public class StalenessInfo {

    private boolean stale;
    private String since;
    private String reason;

    public StalenessInfo() {
    }

    private StalenessInfo(boolean stale, String since, String reason) {
        this.stale = stale;
        this.since = since;
        this.reason = reason;
    }

    public static StalenessInfo fresh() {
        return new StalenessInfo(false, null, null);
    }

    public static StalenessInfo stale(String reason, String since) {
        return new StalenessInfo(true, since, reason);
    }

    public boolean isStale() {
        return stale;
    }

    public void setStale(boolean stale) {
        this.stale = stale;
    }

    public String getSince() {
        return since;
    }

    public void setSince(String since) {
        this.since = since;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
