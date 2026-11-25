package com.dossiersnoirs.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.Calendar;

/** Rappel quotidien « Affaire du jour » à 20 h 30 (heure locale du téléphone). */
public class DailyReminder extends BroadcastReceiver {
    static final int HOUR = 20, MINUTE = 30;
    static final String CHANNEL = "affaire_du_jour";
    static final String[] MSG_EN = {
        "A new case of the day is waiting for you, detective.",
        "The lieutenant just dropped a file on your desk.",
        "A suspect is still on the loose… The case of the day is open.",
        "Don't break your streak: the case of the day is waiting.",
        "Three suspects, one culprit. Your move, detective!"
    };
    static final String[] MSG_AR = {
        "قضية اليوم في انتظارك أيها المحقق.",
        "وضع الرئيس ملفًا جديدًا على مكتبك.",
        "مشتبه به ما زال طليقًا… قضية اليوم مفتوحة.",
        "لا تقطع سلسلتك: قضية اليوم في انتظارك.",
        "ثلاثة مشتبه بهم ومجرم واحد. دورك أيها المحقق!"
    };
    static final String[] MSG_ES = {
        "Un nuevo caso del día te espera, inspector.",
        "El comisario ha dejado un expediente en tu mesa.",
        "Un sospechoso sigue suelto… El caso del día está abierto.",
        "No rompas tu racha: el caso del día te espera.",
        "Tres sospechosos, un culpable. ¡Te toca, inspector!"
    };
    static final String[] MSG_IT = {
        "Un nuovo caso del giorno ti aspetta, ispettore.",
        "Il commissario ha lasciato un fascicolo sulla tua scrivania.",
        "Un sospettato è ancora a piede libero… Il caso del giorno è aperto.",
        "Non interrompere la tua serie: il caso del giorno ti aspetta.",
        "Tre sospettati, un colpevole. Tocca a te, ispettore!"
    };
    static final String[] MSG = {
        "Une nouvelle affaire du jour vous attend, inspecteur.",
        "Le commissaire a posé un dossier sur votre bureau.",
        "Un suspect court toujours… L'affaire du jour est ouverte.",
        "Ne brisez pas votre série : l'affaire du jour vous attend.",
        "Trois suspects, un seul coupable. À vous de jouer !"
    };

    public static void schedule(Context c) {
        Calendar t = Calendar.getInstance();
        t.set(Calendar.HOUR_OF_DAY, HOUR); t.set(Calendar.MINUTE, MINUTE); t.set(Calendar.SECOND, 0);
        if (t.getTimeInMillis() <= System.currentTimeMillis()) t.add(Calendar.DAY_OF_YEAR, 1);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, new Intent(c, DailyReminder.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
    }

    @Override public void onReceive(Context c, Intent i) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) { schedule(c); return; }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "fr".equals(java.util.Locale.getDefault().getLanguage()) ? "Affaire du jour" : "ar".equals(java.util.Locale.getDefault().getLanguage()) ? "قضية اليوم" : "es".equals(java.util.Locale.getDefault().getLanguage()) ? "Caso del día" : "it".equals(java.util.Locale.getDefault().getLanguage()) ? "Caso del giorno" : "Case of the day", NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open = PendingIntent.getActivity(c, 2, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean fr = "fr".equals(java.util.Locale.getDefault().getLanguage());
        String lg = java.util.Locale.getDefault().getLanguage();
        String[] M = fr ? MSG : "ar".equals(lg) ? MSG_AR : "es".equals(lg) ? MSG_ES : "it".equals(lg) ? MSG_IT : MSG_EN;
        String text = M[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % M.length];
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notif).setContentTitle(c.getString(R.string.app_name)).setContentText(text)
         .setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true);
        try { nm.notify(7, b.build()); } catch (SecurityException e) { /* permission refusée */ }
    }
}
