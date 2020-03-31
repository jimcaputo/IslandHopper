package com.lakeuniontech.www.islandhopper;

import android.content.Context;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;


interface JsonRequestCallback {
    default void success(JSONObject response) {};
    default void success(JSONObject response, int counter) {};
    default void success(JSONArray response) {};
    void failure(String error);
}


class JsonRequest {
    private RequestQueue queue;

    JsonRequest(Context context) {
        queue = Volley.newRequestQueue(context);
    }

    void sendRequest(String url, final HashMap<String, String> headers, final JsonRequestCallback callback) {
        sendRequest(url, headers, 0, callback);
    }
    void sendRequest(String url, final int counter, final JsonRequestCallback callback) {
        sendRequest(url, null, counter, callback);
    }

    void sendRequest(String url, final HashMap<String, String> headers, final int counter,
            final JsonRequestCallback callback) {
        JsonObjectRequest jsonRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    if (counter > 0)
                        callback.success(response, counter);
                    callback.success(response);
                },
                error -> callback.failure(error.toString())
        ) {
            @Override
            public Map<String, String> getHeaders() {
                if (headers == null)
                    return new HashMap<String, String>();
                return headers;
            }
        };
        queue.add(jsonRequest);
    }

    void sendArrayRequest(String url, final JsonRequestCallback callback) {
        JsonArrayRequest jsonRequest = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> callback.success(response),
                error -> callback.failure(error.toString())
        );
        queue.add(jsonRequest);
    }
}
