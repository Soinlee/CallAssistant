package com.github.soinlee.callassistant.model.permission;

/**
 * Query-only permission facade.
 *
 * <p>Only read/query helpers live here. Actually requesting a runtime permission or
 * opening the overlay settings page is done by the caller (Activity / Fragment) via
 * the Jetpack Activity Result API ({@code registerForActivityResult} with
 * {@code ActivityResultContracts}), because an {@code ActivityResultLauncher} can
 * only be registered on a {@code ComponentActivity} / {@code Fragment}, not on this
 * plain class.
 */
public interface Permission {

    boolean canDrawOverlays();

    int checkPermission(String permission);

    boolean canReadPhoneState();

    boolean canReadContact();

}
