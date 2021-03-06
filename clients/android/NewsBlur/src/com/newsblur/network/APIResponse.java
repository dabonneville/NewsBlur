package com.newsblur.network;

import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import org.apache.http.HttpStatus;

import com.newsblur.R;
import com.newsblur.network.domain.NewsBlurResponse;
import com.newsblur.util.AppConstants;

/**
 * A JSON-encoded response from the API servers.  This class encodes the possible outcomes of
 * an attempted API call, including total failure, online failures, and successful responses.
 * In the latter case, the GSON reader used to look for errors is left open so that the expected
 * response can be read.  Instances of this class should be closed after use.
 */
public class APIResponse {
	
    private boolean isError;
    private String errorMessage;
	private String cookie;
    private String responseBody;
    public long readTime;

    /**
     * Construct an online response.  Will test the response for errors and extract all the
     * info we might need.
     */
    public APIResponse(Context context, URL originalUrl, HttpURLConnection connection) {
        this(context, originalUrl, connection, HttpStatus.SC_OK);
    }

    /**
     * Construct an online response.  Will test the response for errors and extract all the
     * info we might need.
     */
    public APIResponse(Context context, URL originalUrl, HttpURLConnection connection, int expectedReturnCode) {

        this.errorMessage = context.getResources().getString(R.string.error_unset_message);

        try {
            if (connection.getResponseCode() != expectedReturnCode) {
                Log.e(this.getClass().getName(), "API returned error code " + connection.getResponseCode() + " calling " + originalUrl + ". Expected " + expectedReturnCode);
                this.isError = true;
                this.errorMessage = context.getResources().getString(R.string.error_http_connection);
                return;
            }
            
            if (!TextUtils.equals(originalUrl.getHost(), connection.getURL().getHost())) {
                // TODO: the existing code rejects redirects as errors.  Is this correct?
                Log.e(this.getClass().getName(), "API redirected calling " + originalUrl);
                this.isError = true;
                this.errorMessage = context.getResources().getString(R.string.error_http_connection);
                return;
            }
        } catch (IOException ioe) {
            Log.e(this.getClass().getName(), "Error (" + ioe.getMessage() + ") calling " + originalUrl, ioe);
            this.isError = true;
            this.errorMessage = context.getResources().getString(R.string.error_read_connection);
            return;
        }

        this.cookie = connection.getHeaderField("Set-Cookie");

        try {
            StringBuilder builder = new StringBuilder();
            Reader reader = new InputStreamReader(connection.getInputStream());
            char[] chunk = new char[1024];
            int len;
            long startTime = System.currentTimeMillis();
            while ( (len = reader.read(chunk)) > 0) {
                builder.append(chunk, 0, len);
            }
            readTime = System.currentTimeMillis() - startTime;
            this.responseBody = builder.toString();
        } catch (Exception e) {
            Log.e(this.getClass().getName(), e.getClass().getName() + " (" + e.getMessage() + ") reading " + originalUrl, e);
            this.isError = true;
            this.errorMessage = context.getResources().getString(R.string.error_read_connection);
            return;
        }

        try {
            connection.disconnect();
        } catch (Exception e) {
            Log.e(this.getClass().getName(), e.getClass().getName() + " caught closing connection: " + e.getMessage(), e);
        }

        if (AppConstants.VERBOSE_LOG_NET) {
            // the default kernel truncates log lines. split by something we probably have, like a json delim
            if (responseBody.length() < 2048) {
                Log.d(this.getClass().getName(), "API response: \n" + this.responseBody);
            } else {
                Log.d(this.getClass().getName(), "API response: ");
                for (String s : TextUtils.split(responseBody, "\\}")) {
                    Log.d(this.getClass().getName(), s + "}");
                }
            }
        }
    }

    /**
     * Construct and empty/offline response.  Signals that the call was not made.
     */
    public APIResponse(Context context) {
        this.isError = true;
        this.errorMessage = context.getResources().getString(R.string.error_offline);
    }

    public boolean isError() {
        return this.isError;
    }

    public String getErrorMessage() {
        return this.errorMessage;
    }

    /**
     * Get the response object from this call.  A specific subclass of NewsBlurResponse
     * may be used for calls that return data, or the parent class may be used if no
     * return data are expected.
     */
    @SuppressWarnings("unchecked")
    public <T extends NewsBlurResponse> T getResponse(Gson gson, Class<T> classOfT) {
        if (this.isError) {
            // if we encountered an error, make a generic response type and populate
            // it's message field
            try {
                T response = classOfT.newInstance();
                response.message = this.errorMessage;
                return ((T) response);
            } catch (Exception e) {
                // this should never fail unless the constructor of the base response bean fails
                Log.wtf(this.getClass().getName(), "Failed to load class: " + classOfT);
                return null;
            }
        } else {
            // otherwise, parse the response as the expected class and defer error detection
            // to the NewsBlurResponse parent class
            T response = gson.fromJson(this.responseBody, classOfT);
            response.readTime = readTime;
            return response;
        }
    }

    public NewsBlurResponse getResponse(Gson gson) {
        return getResponse(gson, NewsBlurResponse.class);
    }

    public String getResponseBody() {
        return this.responseBody;
    }

    public String getCookie() {
        return this.cookie;
    }

}
