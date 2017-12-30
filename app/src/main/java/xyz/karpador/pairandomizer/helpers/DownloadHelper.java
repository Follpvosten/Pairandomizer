package xyz.karpador.pairandomizer.helpers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import javax.net.ssl.HttpsURLConnection;

import xyz.karpador.pairandomizer.exceptions.HttpResponseException;

public final class DownloadHelper {

    public static String downloadFileContents(String uri) throws IOException, HttpResponseException {
        URL url = new URL(uri);
        URLConnection con = url.openConnection();
        // You can't be serious.
        int responseCode;
        if(con instanceof HttpsURLConnection) {
            responseCode = ((HttpsURLConnection)con).getResponseCode();
        } else {
            responseCode = ((HttpURLConnection)con).getResponseCode();
        }
        if(responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(con.getInputStream())
                    );
            StringBuilder httpResult = new StringBuilder();
            String line;
            while((line = reader.readLine()) != null)
                httpResult.append(line).append('\n');
            reader.close();
            return httpResult.toString();
        } else {
            throw new HttpResponseException("HTTP Connection error: " + responseCode);
        }
    }

}
