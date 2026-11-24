package com.dave.nuviofaceupdater;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/** Daily background check: notifies once per new Nuvio Face release. */
public class UpdateCheckJob extends JobService {
    private static final int JOB_ID = 4201;
    static final String PREFS = "updater";
    static final String KEY_DAILY = "daily_check";
    private static final String KEY_NOTIFIED_TAG = "notified_tag";
    private static final String CHANNEL = "updates";

    static void schedule(Context context, boolean enabled) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        if (!enabled) {
            scheduler.cancel(JOB_ID);
            return;
        }
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, UpdateCheckJob.class))
                .setPeriodic(24L * 60 * 60 * 1000)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build();
        scheduler.schedule(job);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try {
                Updater.Release release = Updater.fetchLatest();
                long installed = Updater.installedVersionCode(this);
                SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
                if (installed >= 0 && Updater.isNewer(release, installed)
                        && !release.tag.equals(prefs.getString(KEY_NOTIFIED_TAG, ""))) {
                    notifyUpdate(release);
                    prefs.edit().putString(KEY_NOTIFIED_TAG, release.tag).apply();
                }
            } catch (Exception ignored) {
                // Try again tomorrow.
            }
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    private void notifyUpdate(Updater.Release release) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Nuvio Face updates", NotificationManager.IMPORTANCE_DEFAULT));
        }
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent tap = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Nuvio Face update ready")
                .setContentText(release.title + ": tap to install")
                .setContentIntent(tap)
                .setAutoCancel(true);
        nm.notify(1, b.build());
    }
}
