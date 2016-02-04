package com.github.javiersantos.appupdater;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.util.Log;

import com.github.javiersantos.appupdater.enums.Duration;
import com.github.javiersantos.appupdater.enums.UpdateFrom;
import com.github.javiersantos.appupdater.objects.GitHub;
import com.github.javiersantos.appupdater.objects.Version;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

class UtilsLibrary {

    static String getAppName(Context context) {
        return context.getString(context.getApplicationInfo().labelRes);
    }

    static String getAppPackageName(Context context) {
        return context.getPackageName();
    }

    static String getAppInstalledVersion(Context context) {
        String version = "0.0.0.0";

        try {
            version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return version;
    }

    static Boolean isUpdateAvailable(String installedVersion, String latestVersion) {
        Boolean res = false;

        if (!installedVersion.equals("0.0.0.0") && !latestVersion.equals("0.0.0.0")) {
            Version installed = new Version(installedVersion);
            Version latest = new Version(latestVersion);
            res = installed.compareTo(latest) < 0;
        }

        return res;
    }

    static Boolean getDurationEnumToBoolean(Duration duration) {
        Boolean res = false;

        switch (duration) {
            case INDEFINITE:
                res = true;
                break;
        }

        return res;
    }

    static String getUpdateURL(Context context, UpdateFrom updateFrom, GitHub gitHub) {
        String res;

        switch (updateFrom) {
            default:
                res = Config.PLAY_STORE_URL + getAppPackageName(context);
                break;
            case GITHUB:
                res = Config.GITHUB_URL + gitHub.getGithubUser() + "/" + gitHub.getGithubRepo() + "/releases";
                break;
            case AMAZON:
                res = Config.AMAZON_URL + getAppPackageName(context);
                break;
            case FDROID:
                res = Config.FDROID_URL + getAppPackageName(context);
                break;
        }

        return res;
    }

    static String getLatestAppVersion(Context context, UpdateFrom updateFrom, GitHub gitHub) {
        Boolean isAvailable = false;
        String res = "0.0.0.0";
        String source = "";

        try {
            URL url = new URL(getUpdateURL(context, updateFrom, gitHub));

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            InputStream inputStream = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            StringBuilder str = new StringBuilder();

            String line;
            while((line = reader.readLine()) != null) {
                switch (updateFrom) {
                    default:
                        if (line.contains(Config.PLAY_STORE_TAG_RELEASE)) {
                            str.append(line);
                            isAvailable = true;
                        }
                        break;
                    case GITHUB:
                        if (line.contains(Config.GITHUB_TAG_RELEASE)) {
                            str.append(line);
                            isAvailable = true;
                        }
                        break;
                    case AMAZON:
                        if (line.contains(Config.AMAZON_TAG_RELEASE)) {
                            str.append(line);
                            isAvailable = true;
                        }
                        break;
                    case FDROID:
                        if (line.contains(Config.FDROID_TAG_RELEASE)) {
                            str.append(line);
                            isAvailable = true;
                        }
                }
            }

            if (str.length() == 0) {
                Log.e("AppUpdater", "Cannot retrieve latest version. Is it configured properly?");
            }

            inputStream.close();
            source = str.toString();
        } catch (FileNotFoundException e) {
            Log.e("AppUpdater", "App wasn't found in the provided source. Is it published?");
        } catch (IOException ignore) {}

        if (isAvailable) {
            switch (updateFrom) {
                default:
                    String[] splitPlayStore = source.split(Config.PLAY_STORE_TAG_RELEASE);
                    splitPlayStore = splitPlayStore[1].split("(<)");
                    res = splitPlayStore[0].trim();
                    break;
                case GITHUB:
                    String[] splitGitHub = source.split(Config.GITHUB_TAG_RELEASE);
                    splitGitHub = splitGitHub[1].split("(\")");
                    res = splitGitHub[0].trim();
                    if (res.contains("v")) { // Some repo uses vX.X.X
                        splitGitHub = res.split("(v)");
                        res = splitGitHub[1].trim();
                    }
                    break;
                case AMAZON:
                    String[] splitAmazon = source.split(Config.AMAZON_TAG_RELEASE);
                    splitAmazon = splitAmazon[1].split("(<)");
                    res = splitAmazon[0].trim();
                    break;
                case FDROID:
                    String[] splitFDroid = source.split(Config.FDROID_TAG_RELEASE);
                    splitFDroid = splitFDroid[1].split("(<)");
                    res = splitFDroid[0].trim();
                    break;
            }
        }

        return res;
    }

    static Intent intentToUpdate(Context context, UpdateFrom updateFrom, GitHub gitHub) {
        String updateURL = getUpdateURL(context, updateFrom, gitHub);
        Intent intent;

        if (updateFrom.equals(UpdateFrom.GOOGLE_PLAY)) {
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getAppPackageName(context)));
        } else {
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(updateURL));
        }

        return intent;
    }

    static void goToUpdate(Context context, UpdateFrom updateFrom, GitHub gitHub) {
        Intent intent = intentToUpdate(context, updateFrom, gitHub);
        String updateURL = getUpdateURL(context, updateFrom, gitHub);

        if (updateFrom.equals(UpdateFrom.GOOGLE_PLAY)) {
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(updateURL));
                context.startActivity(intent);
            }
        } else {
            context.startActivity(intent);
        }
    }

    static Boolean isAbleToShow(Context context, Integer showEvery) {
        LibraryPreferences libraryPreferences = new LibraryPreferences(context);
        return libraryPreferences.getSuccessfulChecks() % showEvery == 0;
    }

    static Boolean isNetworkAvailable(Context context) {
        Boolean res = false;
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            if (networkInfo != null) {
                res = networkInfo.isConnected();
            }
        }

        return res;
    }

}
