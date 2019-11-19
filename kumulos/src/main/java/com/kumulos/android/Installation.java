package com.kumulos.android;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.UUID;

/**
 * Represents the unique installation ID assigned the first time the Kumulos SDK is initialized
 */
public class Installation {
    private static String sID = null;

    /**
     * Returns the unique Kumulos installation ID, creating it if it doesn't exist
     *
     * @param context
     * @return
     */
    public synchronized static String id(Context context) {
        if (sID != null) {
            return sID;
        }

        File installation = new File(context.getFilesDir(), context.getResources().getString(R.string.uuid_storage_dir));
        try {
            if (!installation.exists())
                writeInstallationFile(installation);
            sID = readInstallationFile(installation);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return sID;
    }

    private static String readInstallationFile(File installation) throws IOException {
        RandomAccessFile f = new RandomAccessFile(installation, "r");
        byte[] bytes = new byte[(int) f.length()];
        f.readFully(bytes);
        f.close();
        return new String(bytes);
    }

    private static void writeInstallationFile(File installation) throws IOException {
        FileOutputStream out = new FileOutputStream(installation);
        String id = UUID.randomUUID().toString();
        out.write(id.getBytes());
        out.close();
    }
}
