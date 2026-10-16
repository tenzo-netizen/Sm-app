package com.xyron.game.launcher.util;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class GameDataVerifier {
    private static final String[] REQUIRED_DIRECTORIES = new String[] {
            "anim",
            "audio",
            "data",
            "models",
            "texdb"
    };

    private static final String[] REQUIRED_FILES = new String[] {
            "SAMP/main.scm",
            "models/MINFO.BIN",
            "texdb/SAMPCOL.img",
            "texdb/gta3.img",
            "texdb/gta_int.img",
            "texdb/player.img",
            "texdb/samp.img",
            "texdb/gta3/gta3.txt",
            "texdb/gta_int/gta_int.txt",
            "texdb/menu/menu.txt",
            "texdb/mobile/mobile.txt",
            "texdb/player/player.txt",
            "texdb/samp/samp.txt",
            "texdb/txd/txd.txt"
    };

    private GameDataVerifier() {
    }

    public static boolean hasRequiredGameData(Context context) {
        File externalFilesDir = context.getExternalFilesDir(null);
        if (externalFilesDir == null) {
            return false;
        }

        for (String relativePath : REQUIRED_DIRECTORIES) {
            File directory = new File(externalFilesDir, relativePath);
            if (!directory.exists() || !directory.isDirectory()) {
                return false;
            }
        }

        for (String relativePath : REQUIRED_FILES) {
            if (!hasNonEmptyFile(externalFilesDir, relativePath)) {
                return false;
            }
        }

        return hasTextureSet(externalFilesDir, "texdb/gta3/gta3")
                && hasTextureSet(externalFilesDir, "texdb/gta_int/gta_int")
                && hasTextureSet(externalFilesDir, "texdb/menu/menu")
                && hasTextureSet(externalFilesDir, "texdb/mobile/mobile")
                && hasTextureSet(externalFilesDir, "texdb/samp/samp")
                && hasTextureSet(externalFilesDir, "texdb/txd/txd")
                && hasNonEmptyFile(externalFilesDir, "texdb/player/player.pvr.dat")
                && hasNonEmptyFile(externalFilesDir, "texdb/player/player.pvr.tmb")
                && hasNonEmptyFile(externalFilesDir, "texdb/player/player.pvr.toc");
    }

    private static boolean hasTextureSet(File baseDirectory, String basePath) {
        return hasTextureSet(baseDirectory, basePath, "dxt")
                || hasTextureSet(baseDirectory, basePath, "etc")
                || hasTextureSet(baseDirectory, basePath, "pvr");
    }

    private static boolean hasTextureSet(File baseDirectory, String basePath, String format) {
        return hasNonEmptyFile(baseDirectory, basePath + "." + format + ".dat")
                && hasNonEmptyFile(baseDirectory, basePath + "." + format + ".tmb")
                && hasNonEmptyFile(baseDirectory, basePath + "." + format + ".toc");
    }

    private static boolean hasNonEmptyFile(File baseDirectory, String relativePath) {
        File file = new File(baseDirectory, relativePath);
        return file.exists() && file.isFile() && file.length() > 0L;
    }
    // Lists what is missing (empty list = data is complete). Used to show a clear report.
    public static List<String> findMissing(Context context) {
        List<String> missing = new ArrayList<>();
        File base = context.getExternalFilesDir(null);
        if (base == null) {
            missing.add("(پۆڵدەری یاری نییە)");
            return missing;
        }
        for (String dir : REQUIRED_DIRECTORIES) {
            File directory = new File(base, dir);
            if (!directory.exists() || !directory.isDirectory()) {
                missing.add(dir + "/");
            }
        }
        for (String path : REQUIRED_FILES) {
            if (!hasNonEmptyFile(base, path)) {
                missing.add(path);
            }
        }
        String[] sets = {"texdb/gta3/gta3", "texdb/gta_int/gta_int", "texdb/menu/menu",
                "texdb/mobile/mobile", "texdb/samp/samp", "texdb/txd/txd"};
        for (String set : sets) {
            if (!hasTextureSet(base, set)) {
                missing.add(set + ".(dxt|etc|pvr).(dat|tmb|toc)");
            }
        }
        for (String ext : new String[] {"dat", "tmb", "toc"}) {
            if (!hasNonEmptyFile(base, "texdb/player/player.pvr." + ext)) {
                missing.add("texdb/player/player.pvr." + ext);
            }
        }
        return missing;
    }
}
