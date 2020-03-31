package com.lakeuniontech.www.islandhopper;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

import static com.lakeuniontech.www.islandhopper.R.id.parent;


public class MainActivity extends AppCompatActivity {
    final String URL_SCHEDULE = "https://www.wsdot.wa.gov/ferries/api/schedule/rest/schedule/%d-%d-%d/%d/%d?apiaccesscode=%s";
    final String URL_VESSELS = "https://www.wsdot.wa.gov/ferries/api/vessels/rest/vessellocations?apiaccesscode=%s";

    JsonRequest jsonRequest;
    // Seems hacky, but this is used to make sure we only ever update the UI with the most recent
    // response, which should match the current settings (ie terminals, dates)
    int requestCounter;

    LocationInfo locationInfo;

    // Currently selected day being viewed
    Calendar cal;
    DialogFragment datePicker;

    // User specified departure and arrival terminals, using Spinner control
    TerminalSpinner depart;
    TerminalSpinner arrive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        jsonRequest = new JsonRequest(this);
        requestCounter = 0;

        cal = Calendar.getInstance();
        datePicker = new DatePickerFragment();

        depart = new TerminalSpinner();
        depart.spinner = (Spinner) findViewById(R.id.spinnerDepart);
        ArrayAdapter<Terminal> adapterDepart = new ArrayAdapter<Terminal>(
                this, R.layout.support_simple_spinner_dropdown_item, Terminal.values());
        adapterDepart.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        depart.spinner.setAdapter(adapterDepart);
        depart.spinner.setOnItemSelectedListener(depart);

        arrive = new TerminalSpinner();
        arrive.spinner = (Spinner) findViewById(R.id.spinnerArrive);
        ArrayAdapter<Terminal> adapterArrive = new ArrayAdapter<Terminal>(
                this, R.layout.support_simple_spinner_dropdown_item, Terminal.values());
        adapterArrive.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        arrive.spinner.setAdapter(adapterArrive);
        arrive.spinner.setOnItemSelectedListener(arrive);

        locationInfo = new LocationInfo(this);
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        locationInfo.startListener();

        Terminal departTerminal = locationInfo.getDepartTerminal();
        setTerminals(departTerminal, departTerminal.getArriveTerminal());
    }

    @Override
    protected void onPause() {
        super.onPause();
        locationInfo.stopListener();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationInfo.startListener();
            } else {
                displayToast("Location features disabled");
            }
        }
    }

    public static class DatePickerFragment extends DialogFragment implements DatePickerDialog.OnDateSetListener {
        @Override
        @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(this.getArguments().getLong("date"));
            DatePickerDialog datePickerDialog = new DatePickerDialog(getActivity(), this,
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));

            // Set minDate as today, and maxDate as 90 days from now
            datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis());
            Long maxDate = System.currentTimeMillis() + (90 * 24 * 60 * 60 * 1000L);
            datePickerDialog.getDatePicker().setMaxDate(maxDate);

            return datePickerDialog;
        }

        public void onDateSet(DatePicker view, int year, int month, int day) {
            ((MainActivity) getActivity()).cal.set(year, month, day);
            ((MainActivity) getActivity()).fetchSchedule();
        }
    }

    public void showDateDialog(View view) {
        // Create a bundle to pass the date
        Bundle date = new Bundle();
        date.putLong("date", cal.getTimeInMillis());

        datePicker.setArguments(date);
        datePicker.show(getSupportFragmentManager(), "datePicker");
    }

    public class TerminalSpinner implements AdapterView.OnItemSelectedListener {
        Spinner spinner;
        Terminal terminal;

        private void setTerminal(Terminal terminal) {
            this.terminal = terminal;
            spinner.setSelection(((ArrayAdapter<Terminal>) spinner.getAdapter()).getPosition(terminal));
        }

        public void onItemSelected(AdapterView parent, View view, int pos, long id) {
            String item = parent.getItemAtPosition(pos).toString();
            if (item.equals(Terminal.ANACORTES.name)) terminal = Terminal.ANACORTES;
            else if (item.equals(Terminal.FRIDAY_HARBOR.name)) terminal = Terminal.FRIDAY_HARBOR;
            else if (item.equals(Terminal.LOPEZ.name)) terminal = Terminal.LOPEZ;
            else if (item.equals(Terminal.ORCAS.name)) terminal = Terminal.ORCAS;
            else if (item.equals(Terminal.SHAW.name)) terminal = Terminal.SHAW;

            fetchSchedule();
            // If this TerminalSpinner is for the departure terminal, then update driving time
            if (this == MainActivity.this.depart)
                locationInfo.getDrivingTime();
        }

        public void onNothingSelected(AdapterView parent) {}
    }

    public void displayToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    public void reverse(View view) {
        setTerminals(arrive.terminal, depart.terminal);
        fetchSchedule();
    }

    private void setTerminals(Terminal terminalDepart, Terminal terminalArrive) {
        if (terminalDepart != null) depart.setTerminal(terminalDepart);
        if (terminalArrive != null) arrive.setTerminal(terminalArrive);
    }

    public void getPrev(View view) {
        cal.add(Calendar.DATE, -1);
        fetchSchedule();
    }

    public void getNext(View view) {
        cal.add(Calendar.DATE, 1);
        fetchSchedule();
    }

    private boolean isToday() {
        Calendar today = Calendar.getInstance();
        return (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)  &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR));
    }

    private void updateDate() {
        Button button = (Button) findViewById(R.id.buttonPrev);
        button.setEnabled(!isToday());

        TextView textDate = (TextView) findViewById(R.id.textDate);
        textDate.setText(String.format(Locale.US, "%s, %d/%d/%d",
                cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.US),
                cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.YEAR) - 2000));   // Forgive the hack to get just 2 digit year  :)
    }

    private void fetchSchedule() {
        updateDate();

        ListView listView = (ListView) findViewById(R.id.listview);
        listView.setAdapter(null);

        // If the user set the terminals both the same, then clear the ListView, and simply return.
        if (depart.terminal == arrive.terminal) {
            return;
        }

        String url = String.format(Locale.US, URL_SCHEDULE,
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
                depart.terminal.id, arrive.terminal.id, ApiKeys.WSDOT);

        jsonRequest.sendRequest(url, ++requestCounter, new JsonRequestCallback() {
                @Override
                public void success(JSONObject response, int counter) {
                    // TODO - make sure the response matches the current UI settings, as the user may have clicked
                    // quickly on various changes, and now the responses are coming in over time and potentially
                    // out of order.
                    if (counter == requestCounter)
                        populateList(response);
                }

                @Override
                public void failure(String error) {
                    displayToast("Request failed: " + error);
                }
        });
    }

    private class FerryListAdapter extends BaseAdapter {
        int sailings;       // Count of ferries.  Can be less than JSONArray size, given that we filter sailings after the current time for current day
        String[][] ferries;
        LayoutInflater layoutInflater;

        FerryListAdapter(MainActivity mainActivity, JSONArray array) {
            ferries = new String[array.length() + 1][4];
            sailings = 1;

            ferries[0][0] = "Depart";
            ferries[0][1] = "Arrive";
            ferries[0][2] = "Duration";
            ferries[0][3] = "Ferry";

            try {
                Calendar now = Calendar.getInstance();
                for (int i = 0; i < array.length(); i++) {
                    String vessel = array.getJSONObject(i).getString("VesselName");
                    String strDepart = array.getJSONObject(i).getString("DepartingTime");
                    String strArrive = array.getJSONObject(i).getString("ArrivingTime");
                    Date depart = new Date(Long.valueOf(strDepart.substring(strDepart.indexOf("(") + 1, strDepart.indexOf("-"))));
                    Date arrive = new Date(Long.valueOf(strArrive.substring(strArrive.indexOf("(") + 1, strArrive.indexOf("-"))));
                    Long durMinutes = (arrive.getTime() - depart.getTime()) / 1000 / 60;
                    String duration = durMinutes.toString() + "min";

                    // If cal is today, then only show times later in the day (plus a 1 hour buffer since ferries often run late).
                    if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                            cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) {
                        if (depart.getTime() < now.getTimeInMillis() - 1 * 60 * 60 * 1000) {    // 1 hour buffer
                            continue;
                        }
                    }

                    ferries[sailings][0] = formatTime(depart);
                    ferries[sailings][1] = formatTime(arrive);
                    ferries[sailings][2] = duration;
                    ferries[sailings][3] = vessel;
                    sailings++;
                }
            } catch (Exception e) {
                mainActivity.displayToast("Failed parsing response");
            }

            layoutInflater = (LayoutInflater) mainActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            return sailings;
        }

        @Override
        public Object getItem(int position) {
            return ferries[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = layoutInflater.inflate(R.layout.ferry_list_item, parent, false);
            TextView textDepart = (TextView) view.findViewById(R.id.textDepart);
            TextView textArrive = (TextView) view.findViewById(R.id.textArrive);
            TextView textDuration = (TextView) view.findViewById(R.id.textDuration);
            TextView textFerry = (TextView) view.findViewById(R.id.textFerry);

            textDepart.setText(ferries[position][0]);
            textArrive.setText(ferries[position][1]);
            textDuration.setText(ferries[position][2]);
            textFerry.setText(ferries[position][3]);

            if (position == 0) {
                textDepart.setTypeface(null, Typeface.BOLD);
                textDepart.setTextColor(Color.parseColor("#FF000000"));     // Black
                textArrive.setTypeface(null, Typeface.BOLD);
                textArrive.setTextColor(Color.parseColor("#FF000000"));     // Black
                textDuration.setTypeface(null, Typeface.BOLD);
                textDuration.setTextColor(Color.parseColor("#FF000000"));     // Black
                textFerry.setTypeface(null, Typeface.BOLD);
                textFerry.setTextColor(Color.parseColor("#FF000000"));     // Black
            }

            return view;
        }
    }

    public void populateList(JSONObject response) {
        try {
            JSONArray array = response.getJSONArray("TerminalCombos").getJSONObject(0).getJSONArray("Times");
            FerryListAdapter ferryListAdapter = new FerryListAdapter(this, array);
            ListView listView = (ListView) findViewById(R.id.listview);
            listView.setAdapter(ferryListAdapter);
        } catch (Exception e) {
            displayToast("Failed parsing response");
        }
    }

    private String formatTime(Date time) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(time);
        String period = cal.get(Calendar.HOUR_OF_DAY) < 12 ? "am" : "pm";
        Integer hour = cal.get(Calendar.HOUR) == 0 ? 12 : cal.get(Calendar.HOUR);
        return String.format(Locale.US, "%s:%02d%s", hour, cal.get(Calendar.MINUTE), period);
    }
}
