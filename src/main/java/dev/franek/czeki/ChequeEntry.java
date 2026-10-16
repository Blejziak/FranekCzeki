package dev.franek.czeki;

public class ChequeEntry {

    public enum Status {
        ACTIVE,
        REDEEMED,
        VOIDED
    }

    private final String id;
    private final String creator;
    private final double initialAmount;
    private final long createdAt;
    private Status status;
    private String redeemedBy;
    private long redeemedAt;
    private double redeemedAmount;

    public ChequeEntry(String id, String creator, double initialAmount, long createdAt,
                       Status status, String redeemedBy, long redeemedAt, double redeemedAmount) {
        this.id = id;
        this.creator = creator;
        this.initialAmount = initialAmount;
        this.createdAt = createdAt;
        this.status = status;
        this.redeemedBy = redeemedBy;
        this.redeemedAt = redeemedAt;
        this.redeemedAmount = redeemedAmount;
    }

    public String getId() {
        return id;
    }

    public String getCreator() {
        return creator;
    }

    public double getInitialAmount() {
        return initialAmount;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getRedeemedBy() {
        return redeemedBy;
    }

    public void setRedeemedBy(String redeemedBy) {
        this.redeemedBy = redeemedBy;
    }

    public long getRedeemedAt() {
        return redeemedAt;
    }

    public void setRedeemedAt(long redeemedAt) {
        this.redeemedAt = redeemedAt;
    }

    public double getRedeemedAmount() {
        return redeemedAmount;
    }

    public void setRedeemedAmount(double redeemedAmount) {
        this.redeemedAmount = redeemedAmount;
    }

    public long getElapsedHours() {
        long diffMs = Math.max(0L, System.currentTimeMillis() - createdAt);
        return diffMs / (1000L * 60L * 60L);
    }

    public double getCurrentTaxPercent(double taxPerHour, double maxTaxPercent) {
        if (status == Status.VOIDED) {
            return 100.0;
        }
        long hours = getElapsedHours();
        double tax = hours * taxPerHour;
        return Math.min(maxTaxPercent, Math.max(0.0, tax));
    }

    public double getCurrentValue(double taxPerHour, double maxTaxPercent) {
        if (status == Status.VOIDED) {
            return 0.0;
        }
        if (status == Status.REDEEMED) {
            return redeemedAmount;
        }
        double taxPct = getCurrentTaxPercent(taxPerHour, maxTaxPercent);
        double val = initialAmount * (1.0 - (taxPct / 100.0));
        return Math.max(0.0, Math.round(val * 100.0) / 100.0);
    }
}
