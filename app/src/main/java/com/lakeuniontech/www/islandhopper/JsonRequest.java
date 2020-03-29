package com.lakeuniontech.www.islandhopper;

import android.content.Context;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;


interface JsonRequestCallback {
    void success(JSONObject response, int counter);
    void failure(String error);
}


class JsonRequest {
    private RequestQueue queue;

    JsonRequest(Context context) {
        queue = Volley.newRequestQueue(context);
    }

    void sendRequest(String url, final HashMap<String, String> headers, final int counter,
            final JsonRequestCallback callback) {
        JsonObjectRequest jsonRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        callback.success(response, counter);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        callback.failure(error.toString());
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                if (headers == null)
                    return new HashMap<String, String>();
                return headers;
            }
        };
        queue.add(jsonRequest);
    }
}
