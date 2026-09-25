package com.yeowool.federation;

/** A federation's bank balance and cumulative activity (yw_federations.bank_balance / activity). */
public record FederationProgress(long bankBalance, long activity) {
}
