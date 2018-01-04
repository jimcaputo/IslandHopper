package com.lakeuniontech.www.islandhopper;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;


interface JsonRequestCallback {
    void success(JSONObject response);
    void success(JSONArray response);
    void failure(String error);
}


class JsonRequest {
    private RequestQueue queue;

    JsonRequest(Context context) {
        queue = Volley.newRequestQueue(context);
    }

    void sendRequest(String url, final JsonRequestCallback callback) {
        JsonObjectRequest jsonRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        callback.success(response);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        callback.failure(error.toString());
                    }
                });
        queue.add(jsonRequest);
    }

    void sendRequestArray(String url, final JsonRequestCallback callback) {
        JsonArrayRequest jsonRequest = new JsonArrayRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        callback.success(response);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        callback.failure(error.toString());
                    }
                });
        queue.add(jsonRequest);
    }
}
