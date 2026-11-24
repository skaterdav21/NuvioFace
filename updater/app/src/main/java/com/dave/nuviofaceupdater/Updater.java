package com.dave.nuviofaceupdater;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Talks to GitHub Releases and Android's package installer. No UI here. */
final class Updater {
    static final String REPO = "skaterdav21/NuvioFace";
    static final String PHONE_PACKAGE = "com.dave.nuvioface";
    static final String TV_PACKAGE = "com.dave.nuviofacetv";

    /** TV boxes get Nuvio Face TV (built on NuvioTV); phones and tablets get Nuvio Face. */
    static boolean isTv(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || pm.hasSystemFeature("android.software.leanback_only");
    }

    static String facePackage(Context context) {
        return isTv(context) ? TV_PACKAGE : PHONE_PACKAGE;
    }

    static String appLabel(Context context) {
        return isTv(context) ? "Nuvio Face TV" : "Nuvio Face";
    }

    private Updater() {}

    static final class Release {
        String tag;
        String title;
        String notes;
        long versionCode = -1;
        String versionName;
        String assetName;
        String assetUrl;
        long assetSize;
    }

    interface Progress {
        void onProgress(long done, long total);
    }

    /** "arm64" for 64-bit Android (phones, Shield); "arm32" for 32-bit boxes (Chromecast with Google TV, onn). */
    static String abiFlavor() {
        boolean has64 = Build.SUPPORTED_64_BIT_ABIS != null && Build.SUPPORTED_64_BIT_ABIS.length > 0;
        return (has64 && Process.is64Bit()) ? "arm64" : "arm32";
    }

    static Release fetchLatest(Context context) throws IOException {
        boolean tv = isTv(context);
        HttpURLConnection conn = (HttpURLConnection) new URL(
                "https://api.github.com/repos/" + REPO + "/releases/latest").openConnection();
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent", "NuvioFaceUpdater");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        try {
            int code = conn.getResponseCode();
            if (code == 404) throw new IOException("No Nuvio Face release published yet.");
            if (code != 200) throw new IOException("GitHub answered " + code + ".");
            JSONObject json = new JSONObject(readAll(conn.getInputStream()));
            Release r = new Release();
            r.tag = json.optString("tag_name");
            r.title = json.optString("name", r.tag);
            r.notes = json.optString("body", "");
            String codeKey = tv ? "tvVersionCode" : "versionCode";
            String nameKey = tv ? "tvVersionName" : "versionName";
            Matcher vc = Pattern.compile("(?m)^" + codeKey + ":\\s*(\\d+)").matcher(r.notes);
            if (vc.find()) r.versionCode = Long.parseLong(vc.group(1));
            Matcher vn = Pattern.compile("(?m)^" + nameKey + ":\\s*(\\S+)").matcher(r.notes);
            if (vn.find()) r.versionName = vn.group(1);
            String prefix = tv ? "NuvioFaceTV-" : "NuvioFace-";
            String flavor = abiFlavor();
            JSONArray assets = json.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    String name = a.optString("name");
                    if (name.startsWith(prefix) && name.contains(flavor) && name.endsWith(".apk")) {
                        r.assetName = name;
                        r.assetUrl = a.optString("browser_download_url");
                        r.assetSize = a.optLong("size");
                        break;
                    }
                }
            }
            if (r.assetUrl == null) throw new IOException("The latest release has no " + flavor + " APK.");
            return r;
        } catch (org.json.JSONException e) {
            throw new IOException("Couldn't read the release info.", e);
        } finally {
            conn.disconnect();
        }
    }

    /** Installed Nuvio Face version code, or -1 if it isn't installed. */
    @SuppressWarnings("deprecation")
    static long installedVersionCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(facePackage(context), 0);
            return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    static String installedVersionName(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(facePackage(context), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    static boolean isNewer(Release release, long installed) {
        return installed < 0 || release.versionCode < 0 || release.versionCode > installed;
    }

    static File download(Context context, Release release, Progress progress) throws IOException {
        File out = new File(context.getCacheDir(), "nuvioface-update.apk");
        String url = release.assetUrl;
        HttpURLConnection conn = null;
        // GitHub sends release assets through a redirect to its CDN.
        for (int hop = 0; hop < 5; hop++) {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "NuvioFaceUpdater");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            int code = conn.getResponseCode();
            if (code >= 300 && code < 400) {
                url = conn.getHeaderField("Location");
                conn.disconnect();
                continue;
            }
            if (code != 200) throw new IOException("Download failed (" + code + ").");
            break;
        }
        long total = conn.getContentLengthLong() > 0 ? conn.getContentLengthLong() : release.assetSize;
        try (InputStream in = conn.getInputStream(); OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            long lastReport = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
                done += n;
                if (done - lastReport > 256 * 1024 || done == total) {
                    lastReport = done;
                    progress.onProgress(done, total);
                }
            }
            progress.onProgress(done, total);
        } finally {
            conn.disconnect();
        }
        return out;
    }

    /** Hands the APK to Android's installer; the result comes back to InstallReceiver. */
    static void install(Context context, File apk) throws IOException {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(facePackage(context));
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }
        int sessionId = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = session.openWrite("nuvioface.apk", 0, apk.length())) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                session.fsync(out);
            }
            Intent intent = new Intent(context, InstallReceiver.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pending = PendingIntent.getBroadcast(context, sessionId, intent, flags);
            session.commit(pending.getIntentSender());
        }
    }

    private static String readAll(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }
}
