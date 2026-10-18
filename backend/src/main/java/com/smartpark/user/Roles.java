package com.smartpark.user;

/** Canonical role names (match the `roles` seed table). */
public final class Roles {

    private Roles() {
    }

    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String PARKING_OWNER = "PARKING_OWNER";
    public static final String PARKING_STAFF = "PARKING_STAFF";
    public static final String CUSTOMER = "CUSTOMER";
}
