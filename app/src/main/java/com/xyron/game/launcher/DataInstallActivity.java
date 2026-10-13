package com.xyron.game.launcher;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.xyron.game.launcher.util.GameDataVerifier;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads the full game data (one .7z archive on MediaFire) and extracts it
 * into the app's own external files folder. Replaces the old file-by-file updater.
 */
public class DataInstallActivity extends AppCompatActivity {
    // Change this one line to point to another MediaFire file.
    private static final String PAGE_URL =
            "https://www.mediafire.com/file/eb4iuiq5p19unua/Data_Full_GTA_SAMP.7z/file";
    private static final String ARCHIVE_NAME = "Data_Full_GTA_SAMP.7z";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36";
    private static final long GB = 1024L * 1024L * 1024L;

    private TextView statusView;
    private TextView detailView;
    private ProgressBar progressBar;
    private Button actionButton;
    private Button closeButton;

    private volatile boolean running = false;
    private volatile boolean cancelled = false;
    private Thread worker;

    private static class CancelledException extends IOException {
        CancelledException() {
            super("cancelled");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        updateButtons();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            cancelled = true;
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#0E1116"));
        root.setPadding(dp(32), dp(24), dp(32), dp(24));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView title = new TextView(this);
        title.setText("داگرتن و دامەزراندنی داتای یاری");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView info = new TextView(this);
        info.setText("قەبارەی فایلەکە نزیکەی ١.٥ گیگابایتە و پێویستی بە ٤ گیگابایت شوێنی بەتاڵە. ئەپەکە کراوە بهێڵەرەوە. ئەگەر داگرتنی ئۆتۆماتیک کاری نەکرد، لە براوزەر فایلەکە دابگرە و دووبارە دەستپێبکە، ئەپەکە خۆی لە Download دەیدۆزێتەوە.");
        info.setTextColor(Color.parseColor("#8FA1AE"));
        info.setTextSize(13);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, dp(10), 0, dp(20));
        root.addView(info);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        root.addView(progressBar, new LinearLayout.LayoutParams(dp(420), LinearLayout.LayoutParams.WRAP_CONTENT));

        statusView = new TextView(this);
        statusView.setText("ئامادەیە.");
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(15);
        statusView.setTypeface(Typeface.DEFAULT_BOLD);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, dp(14), 0, 0);
        root.addView(statusView);

        detailView = new TextView(this);
        detailView.setText("");
        detailView.setTextColor(Color.parseColor("#8FA1AE"));
        detailView.setTextSize(12);
        detailView.setGravity(Gravity.CENTER);
        detailView.setPadding(0, dp(4), 0, dp(18));
        root.addView(detailView);

        actionButton = new Button(this);
        actionButton.setAllCaps(false);
        actionButton.setOnClickListener(v -> {
            if (running) {
                cancelled = true;
            } else {
                startWork();
            }
        });
        root.addView(actionButton, new LinearLayout.LayoutParams(dp(220), dp(48)));

        Button browserButton = new Button(this);
        browserButton.setAllCaps(false);
        browserButton.setText("کردنەوە لە براوزەر");
        browserButton.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PAGE_URL)));
            } catch (Exception ignored) {
            }
        });
        LinearLayout.LayoutParams browserParams = new LinearLayout.LayoutParams(dp(220), dp(44));
        browserParams.topMargin = dp(8);
        root.addView(browserButton, browserParams);

        closeButton = new Button(this);
        closeButton.setAllCaps(false);
        closeButton.setText("گەڕانەوە");
        closeButton.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(220), dp(44));
        closeParams.topMargin = dp(8);
        root.addView(closeButton, closeParams);

        setContentView(root);
    }

    private void updateButtons() {
        actionButton.setText(running ? "وەستاندن" : "دەستپێکردن");
        closeButton.setEnabled(!running);
    }

    private void startWork() {
        if (running) {
            return;
        }
        running = true;
        cancelled = false;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        updateButtons();
        worker = new Thread(this::runInstall, "data-install");
        worker.start();
    }

    private void finishWork(String status, String detail) {
        running = false;
        runOnUiThread(() -> {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            statusView.setText(status);
            detailView.setText(detail);
            updateButtons();
        });
    }

    private void setStatus(String status, String detail, int progress) {
        runOnUiThread(() -> {
            statusView.setText(status);
            detailView.setText(detail);
            if (progress >= 0) {
                progressBar.setProgress(progress);
            }
        });
    }

    private void runInstall() {
        try {
            File base = getExternalFilesDir(null);
            if (base == null) {
                finishWork("هەڵە", "شوێنی پاشەکەوتکردن بەردەست نییە.");
                return;
            }
            if (!base.exists() && !base.mkdirs()) {
                finishWork("هەڵە", "نەتوانرا فۆڵدەری یاری دروست بکرێت.");
                return;
            }

            File archive = new File(base, ARCHIVE_NAME);
            File part = new File(base, ARCHIVE_NAME + ".part");
            File manual = archive.exists() ? null : findManualArchive();

            long free = base.getUsableSpace();
            long needed = (archive.exists() || manual != null) ? (long) (2.5 * GB) : 4 * GB;
            if (free < needed) {
                finishWork("شوێن بەس نییە",
                        "پێویستە لانیکەم " + (needed / GB) + " گیگابایت بەتاڵ بێت. ئێستا " + (free / (1024 * 1024)) + " مێگابایت ماوە.");
                return;
            }

            File source;
            if (archive.exists()) {
                source = archive;
            } else if (manual != null) {
                source = manual;
                setStatus("فایلی داگیراو دۆزرایەوە", manual.getName(), 0);
            } else {
                setStatus("دۆزینەوەی لینکی داگرتن...", "", 0);
                String direct = resolveDirectUrl();
                download(direct, part);
                if (!part.renameTo(archive)) {
                    throw new IOException("rename failed");
                }
                source = archive;
            }

            extract(source, base);

            if (GameDataVerifier.hasRequiredGameData(this)) {
                if (source == archive) {
                    //noinspection ResultOfMethodCallIgnored
                    archive.delete();
                }
                runOnUiThread(() -> progressBar.setProgress(1000));
                finishWork("تەواو بوو ✅", "داتای یاری دامەزرا. دەتوانیت بگەڕێیتەوە و یاری بکەیت.");
            } else {
                finishWork("داتا تەواو نییە",
                        "فایلەکان دەرهێنران بەڵام هەندێک فایلی پێویست نەدۆزرانەوە. دووبارە هەوڵ بدەرەوە.");
            }
        } catch (CancelledException e) {
            finishWork("وەستێنرا", "کاتێک دووبارە دەستپێدەکەیتەوە، لە شوێنی وەستانەکەوە بەردەوام دەبێت.");
        } catch (Throwable error) {
            finishWork("هەڵە ڕووی دا", describe(error));
        }
    }

    private String describe(Throwable error) {
        if (error instanceof OutOfMemoryError) {
            return "ڕامی مۆبایلەکە بەس نییە بۆ دەرهێنانی ئەم فایلە.";
        }
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getSimpleName() : message;
    }

    // ---------- MediaFire page -> direct link ----------

    private String resolveDirectUrl() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(PAGE_URL).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        connection.setInstanceFollowRedirects(true);

        int code = connection.getResponseCode();
        String html;
        try (InputStream in = new BufferedInputStream(code >= 400 ? connection.getErrorStream() : connection.getInputStream())) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) != -1 && out.size() < 4 * 1024 * 1024) {
                out.write(buffer, 0, read);
            }
            html = out.toString("UTF-8");
        } finally {
            connection.disconnect();
        }

        // 1) MediaFire hides the link as base64 in data-scrambled-url
        Matcher scrambled = Pattern.compile("data-scrambled-url=\"([^\"]+)\"").matcher(html);
        while (scrambled.find()) {
            try {
                String decoded = new String(Base64.decode(scrambled.group(1), Base64.DEFAULT), "UTF-8").trim();
                if (decoded.startsWith("http")) {
                    return decoded;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        // 2) normal href (also protocol-relative //download...)
        Matcher href = Pattern
                .compile("href=\"((?:https?:)?//download[0-9]*\\.mediafire\\.com/[^\"]+)\"")
                .matcher(html);
        if (href.find()) {
            String url = href.group(1).replace("&amp;", "&");
            return url.startsWith("//") ? "https:" + url : url;
        }

        // 3) any direct link in the page
        Matcher any = Pattern
                .compile("https?://download[0-9]*\\.mediafire\\.com/[^\"'\\s<>]+")
                .matcher(html);
        if (any.find()) {
            return any.group().replace("&amp;", "&");
        }

        // 4) link escaped inside a script (https:\/\/download...)
        Matcher escaped = Pattern
                .compile("https?:\\\\?/\\\\?/download[0-9]*\\.mediafire\\.com[^\"'\\s<>]+")
                .matcher(html);
        if (escaped.find()) {
            return escaped.group().replace("\\/", "/").replace("&amp;", "&");
        }

        // 5) let a real WebView run the page's JavaScript and read the download button
        String viaWebView = resolveViaWebView();
        if (viaWebView != null) {
            return viaWebView;
        }

        throw new IOException("لینکی داگرتن نەدۆزرایەوە (HTTP " + code + "، " + html.length()
                + " بایت). دوگمەی «کردنەوە لە براوزەر» بەکاربهێنە، فایلەکە دابگرە، پاشان دووبارە دەستپێبکە.");
    }

    private static final String READ_LINK_JS =
            "(function(){var a=document.getElementById('downloadButton')"
                    + "||document.querySelector('a[aria-label=\"Download file\"]');"
                    + "if(!a){return '';}"
                    + "var h=a.getAttribute('href')||'';"
                    + "if(h.indexOf('://download')>0){return h;}"
                    + "var s=a.getAttribute('data-scrambled-url');"
                    + "if(s){try{return atob(s);}catch(e){}}"
                    + "return '';})()";

    private String resolveViaWebView() {
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] result = new String[1];
        final WebView[] holder = new WebView[1];
        final Handler handler = new Handler(Looper.getMainLooper());

        runOnUiThread(() -> {
            try {
                final WebView web = new WebView(DataInstallActivity.this);
                holder[0] = web;
                web.getSettings().setJavaScriptEnabled(true);
                web.getSettings().setDomStorageEnabled(true);
                web.getSettings().setUserAgentString(USER_AGENT);

                final int[] attempts = {0};
                final boolean[] started = {false};
                final Runnable[] poll = new Runnable[1];
                poll[0] = () -> web.evaluateJavascript(READ_LINK_JS, value -> {
                    String link = value == null ? "" : value.replace("\"", "")
                            .replace("\\u0026", "&").replace("\\/", "/").trim();
                    if (link.startsWith("http") && link.contains("://download")) {
                        result[0] = link;
                        latch.countDown();
                    } else if (++attempts[0] > 40) {
                        latch.countDown();
                    } else {
                        handler.postDelayed(poll[0], 500);
                    }
                });

                web.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        if (!started[0]) {
                            started[0] = true;
                            handler.postDelayed(poll[0], 800);
                        }
                    }
                });
                web.loadUrl(PAGE_URL);
            } catch (Throwable error) {
                latch.countDown();
            }
        });

        try {
            latch.await(35, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        runOnUiThread(() -> {
            if (holder[0] != null) {
                try {
                    holder[0].stopLoading();
                    holder[0].destroy();
                } catch (Throwable ignored) {
                }
            }
        });
        return result[0];
    }

    // A .7z already downloaded by the browser (Download folder) is used instead of downloading again.
    private File findManualArchive() {
        try {
            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File[] files = downloads == null ? null : downloads.listFiles();
            if (files == null) {
                return null;
            }
            File best = null;
            for (File file : files) {
                String name = file.getName().toLowerCase();
                if (file.isFile() && name.endsWith(".7z") && name.contains("data") && name.contains("gta")
                        && file.length() > GB / 2) {
                    if (best == null || file.length() > best.length()) {
                        best = file;
                    }
                }
            }
            return best;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ---------- Download (resumable) ----------

    private long contentLength(HttpURLConnection connection) {
        try {
            String value = connection.getHeaderField("Content-Length");
            return value == null ? -1L : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private void download(String directUrl, File part) throws IOException {
        long existing = part.exists() ? part.length() : 0L;

        HttpURLConnection connection = (HttpURLConnection) new URL(directUrl).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        if (existing > 0) {
            connection.setRequestProperty("Range", "bytes=" + existing + "-");
        }

        try {
            int code = connection.getResponseCode();
            if (code != 200 && code != 206) {
                throw new IOException("MediaFire وەڵامی " + code + " ی دایەوە.");
            }
            String type = connection.getContentType();
            if (type != null && type.toLowerCase().startsWith("text/html")) {
                throw new IOException("لینکی داگرتن بەسەرچووە. دووبارە هەوڵ بدەرەوە.");
            }

            boolean resumed = code == 206;
            if (!resumed) {
                existing = 0L;
            }
            long length = contentLength(connection);
            long total = length > 0 ? length + existing : -1L;

            long done = existing;
            long lastUi = 0L;
            byte[] buffer = new byte[256 * 1024];

            try (InputStream in = new BufferedInputStream(connection.getInputStream(), buffer.length);
                 OutputStream out = new FileOutputStream(part, resumed)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (cancelled) {
                        throw new CancelledException();
                    }
                    out.write(buffer, 0, read);
                    done += read;

                    long now = System.currentTimeMillis();
                    if (now - lastUi > 400) {
                        lastUi = now;
                        int progress = total > 0 ? (int) (done * 1000L / total) : 0;
                        setStatus("داگرتن...",
                                (done / (1024 * 1024)) + " / " + (total > 0 ? (total / (1024 * 1024)) : "?") + " MB",
                                progress);
                    }
                }
            }

            if (total > 0 && done < total) {
                throw new IOException("داگرتنەکە تەواو نەبوو. دووبارە دەستپێبکەرەوە.");
            }
        } finally {
            connection.disconnect();
        }
    }

    // ---------- Extract .7z ----------

    private void extract(File archive, File base) throws IOException {
        setStatus("ئامادەکردنی دەرهێنان...", "", 0);

        String baseCanonical = base.getCanonicalPath() + File.separator;

        try (RandomAccessFile raf = new RandomAccessFile(archive, "r")) {
            // Pass 1: find the folder that contains "texdb/" and the total size.
            String prefix = null;
            long totalSize = 0L;
            try (SevenZFile scan = new SevenZFile(raf.getChannel())) {
                List<String> names = new ArrayList<>();
                for (SevenZArchiveEntry entry : scan.getEntries()) {
                    String name = entry.getName().replace('\\', '/');
                    names.add(name);
                    if (!entry.isDirectory()) {
                        totalSize += Math.max(0L, entry.getSize());
                    }
                }
                for (String name : names) {
                    int index = name.indexOf("texdb/");
                    if (index == 0 || (index > 0 && name.charAt(index - 1) == '/')) {
                        String candidate = name.substring(0, index);
                        if (prefix == null || candidate.length() < prefix.length()) {
                            prefix = candidate;
                        }
                    }
                }
            }
            if (prefix == null) {
                prefix = "";
            }

            // Pass 2: extract.
            try (RandomAccessFile raf2 = new RandomAccessFile(archive, "r");
                 SevenZFile sevenZ = new SevenZFile(raf2.getChannel())) {
                byte[] buffer = new byte[256 * 1024];
                long written = 0L;
                long lastUi = 0L;
                SevenZArchiveEntry entry;
                while ((entry = sevenZ.getNextEntry()) != null) {
                    if (cancelled) {
                        throw new CancelledException();
                    }
                    String name = entry.getName().replace('\\', '/');
                    if (!name.startsWith(prefix)) {
                        continue;
                    }
                    String relative = name.substring(prefix.length());
                    if (relative.isEmpty()) {
                        continue;
                    }

                    File target = new File(base, relative);
                    if (!target.getCanonicalPath().startsWith(baseCanonical)) {
                        continue; // path traversal protection
                    }

                    if (entry.isDirectory()) {
                        //noinspection ResultOfMethodCallIgnored
                        target.mkdirs();
                        continue;
                    }

                    File parent = target.getParentFile();
                    if (parent != null && !parent.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        parent.mkdirs();
                    }

                    try (OutputStream out = new FileOutputStream(target, false)) {
                        int read;
                        while ((read = sevenZ.read(buffer)) != -1) {
                            if (cancelled) {
                                throw new CancelledException();
                            }
                            out.write(buffer, 0, read);
                            written += read;

                            long now = System.currentTimeMillis();
                            if (now - lastUi > 400) {
                                lastUi = now;
                                int progress = totalSize > 0 ? (int) (written * 1000L / totalSize) : 0;
                                setStatus("دەرهێنانی فایلەکان...", relative, Math.min(progress, 1000));
                            }
                        }
                    }
                }
            }
        }
    }
}
