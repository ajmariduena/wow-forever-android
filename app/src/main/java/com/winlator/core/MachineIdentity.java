package com.winlator.core;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Windows machine identity reported by the Wine prefix. The shipped prefix pack carries one fixed
 * MachineGuid, so without this every install looks like the same PC to Battle.net, which rejects
 * the login with BLZ51914400.
 */
public final class MachineIdentity {
    private static final String TAG = "MachineIdentity";

    /** Read by libgamenative_dns_v4mapped.so; wineboot derives the ComputerName from gethostname(). */
    public static final String HOSTNAME_ENV = "GAMENATIVE_HOSTNAME";

    static final String FILE_NAME = "wow_identity.properties";

    private static final String KEY_MACHINE_GUID = "machineGuid";
    private static final String KEY_COMPUTER_NAME = "computerName";
    private static final String KEY_VOLUME_SERIAL = "volumeSerial";
    private static final String KEY_PRODUCT_ID = "productId";
    private static final String KEY_INSTALL_DATE = "installDate";

    private static final Pattern GUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern COMPUTER_NAME = Pattern.compile("DESKTOP-[A-Z0-9]{7}");
    private static final Pattern VOLUME_SERIAL = Pattern.compile("[0-9a-f]{8}");
    private static final Pattern PRODUCT_ID = Pattern.compile("\\d{5}-\\d{5}-\\d{5}-AA\\d{3}");
    private static final String NAME_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final long THREE_YEARS_SECONDS = 3L * 365 * 24 * 60 * 60;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static MachineIdentity cached;

    public final String machineGuid;
    public final String computerName;
    public final String volumeSerial;
    public final String productId;
    public final int installDate;

    private MachineIdentity(String machineGuid, String computerName, String volumeSerial, String productId, int installDate) {
        this.machineGuid = machineGuid;
        this.computerName = computerName;
        this.volumeSerial = volumeSerial;
        this.productId = productId;
        this.installDate = installDate;
    }

    public static synchronized MachineIdentity get(Context context) {
        if (cached == null) cached = load(new File(context.getFilesDir(), FILE_NAME), System.currentTimeMillis() / 1000);
        return cached;
    }

    static MachineIdentity load(File file, long nowSeconds) {
        Properties props = new Properties();
        if (file.isFile()) {
            try (InputStream in = new FileInputStream(file)) {
                props.load(in);
            }
            catch (IOException e) {
                Log.w(TAG, "Failed to read " + file + ", regenerating: " + e);
                props.clear();
            }
        }

        boolean changed = false;
        String machineGuid = props.getProperty(KEY_MACHINE_GUID, "");
        if (!GUID.matcher(machineGuid).matches()) {
            machineGuid = UUID.randomUUID().toString();
            changed = true;
        }
        String computerName = props.getProperty(KEY_COMPUTER_NAME, "");
        if (!COMPUTER_NAME.matcher(computerName).matches()) {
            computerName = randomComputerName();
            changed = true;
        }
        String volumeSerial = props.getProperty(KEY_VOLUME_SERIAL, "");
        if (!VOLUME_SERIAL.matcher(volumeSerial).matches()) {
            volumeSerial = String.format(Locale.ROOT, "%08x", RANDOM.nextInt());
            changed = true;
        }
        String productId = props.getProperty(KEY_PRODUCT_ID, "");
        if (!PRODUCT_ID.matcher(productId).matches()) {
            productId = String.format(Locale.ROOT, "%05d-%05d-%05d-AA%03d",
                    RANDOM.nextInt(100000), RANDOM.nextInt(100000), RANDOM.nextInt(100000), RANDOM.nextInt(1000));
            changed = true;
        }
        int installDate = parseInstallDate(props.getProperty(KEY_INSTALL_DATE), nowSeconds);
        if (installDate == 0) {
            long minimum = nowSeconds - THREE_YEARS_SECONDS;
            installDate = (int) (minimum + (long) (RANDOM.nextDouble() * (THREE_YEARS_SECONDS - 30L * 24 * 60 * 60)));
            changed = true;
        }

        MachineIdentity identity = new MachineIdentity(machineGuid, computerName, volumeSerial, productId, installDate);
        if (changed) identity.save(file);
        return identity;
    }

    private static int parseInstallDate(String value, long nowSeconds) {
        if (value == null) return 0;
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds > 0 && seconds <= nowSeconds && seconds <= Integer.MAX_VALUE ? (int) seconds : 0;
        }
        catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String randomComputerName() {
        StringBuilder name = new StringBuilder("DESKTOP-");
        for (int i = 0; i < 7; i++) name.append(NAME_CHARS.charAt(RANDOM.nextInt(NAME_CHARS.length())));
        return name.toString();
    }

    private void save(File file) {
        Properties props = new Properties();
        props.setProperty(KEY_MACHINE_GUID, machineGuid);
        props.setProperty(KEY_COMPUTER_NAME, computerName);
        props.setProperty(KEY_VOLUME_SERIAL, volumeSerial);
        props.setProperty(KEY_PRODUCT_ID, productId);
        props.setProperty(KEY_INSTALL_DATE, Integer.toString(installDate));
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        File tmp = new File(file.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            props.store(out, null);
        }
        catch (IOException e) {
            Log.w(TAG, "Failed to persist " + file + ": " + e);
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(file)) {
            Log.w(TAG, "Failed to move " + tmp + " to " + file);
            tmp.delete();
        }
    }

    public void applyTo(File winePrefix) {
        File systemRegFile = new File(winePrefix, "system.reg");
        if (systemRegFile.isFile()) {
            try (WineRegistryEditor registryEditor = new WineRegistryEditor(systemRegFile)) {
                registryEditor.setStringValue("Software\\Microsoft\\Cryptography", "MachineGuid", machineGuid);
                registryEditor.setStringValue("Software\\Microsoft\\Windows NT\\CurrentVersion", "ProductId", productId);
                registryEditor.setDwordValue("Software\\Microsoft\\Windows NT\\CurrentVersion", "InstallDate", installDate);
            }
        }
        else {
            Log.w(TAG, "No system.reg at " + systemRegFile + ", skipping registry identity");
        }

        File driveC = new File(winePrefix, "drive_c");
        if (driveC.isDirectory()) {
            File serialFile = new File(driveC, ".windows-serial");
            String serial = volumeSerial + "\n";
            try {
                if (!serialFile.isFile() || !serial.equals(FileUtils.readString(serialFile))) {
                    try (OutputStream out = new FileOutputStream(serialFile)) {
                        out.write(serial.getBytes(StandardCharsets.US_ASCII));
                    }
                }
            }
            catch (IOException e) {
                Log.w(TAG, "Failed to write " + serialFile + ": " + e);
            }
        }
    }
}
