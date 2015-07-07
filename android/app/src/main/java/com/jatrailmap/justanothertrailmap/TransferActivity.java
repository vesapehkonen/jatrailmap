package com.jatrailmap.justanothertrailmap;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;

public class TransferActivity extends AppCompatActivity {
    private final String LOG = "mylog";
    private String locsFilename = null, picsFilename = null,  username = null, password = null,
            trailname = null, locationname = null, description = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer);
        Bundle extras = getIntent().getExtras();
        locsFilename = extras.getString("locsFilename");
        picsFilename = extras.getString("picsFilename");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_transfer, menu);
        return true;
    }

    public void onClick(View view) {
        String  url;
        final int id = view.getId();
        switch (id) {
            case R.id.button_send:
                Log.i(LOG, "TransferActivity: button_send clicked");
                url = ((EditText) findViewById(R.id.edit_server_url)).getText().toString();
                username = ((EditText) findViewById(R.id.edit_username)).getText().toString();
                password = ((EditText) findViewById(R.id.edit_password)).getText().toString();
                trailname = ((EditText) findViewById(R.id.edit_trailname)).getText().toString();
                locationname = ((EditText) findViewById(R.id.edit_locationname)).getText().toString();
                description = ((EditText) findViewById(R.id.edit_description)).getText().toString();

                if (username.isEmpty() || password.isEmpty() || trailname.isEmpty()) {
                    Toast.makeText(getBaseContext(),
                            "Please fill in fields!", Toast.LENGTH_SHORT).show();
                    return;
                }

                File file = new File(getExternalFilesDir(null), locsFilename);

                if (file.exists() && file.length() > 0) {
                    if (isNetworkAvailable()) {
                        Log.i(LOG, "Netowrk is available");
                        new SendTrailTask().execute(url);
                        Toast.makeText(getBaseContext(),
                                "Send trail data to " + url,
                                Toast.LENGTH_LONG).show();
                    } else {
                        Log.e(LOG, "Netowrk is NOT available");
                        Toast.makeText(getBaseContext(), "Netowrk is NOT available!",
                                Toast.LENGTH_LONG).show();
                    }
                } else {
                    Log.e(LOG, url + "isn't exist");
                    Toast.makeText(getBaseContext(), "There isn't location data!",
                            Toast.LENGTH_LONG).show();
                }
                break;

            case R.id.button_cancel:
                Log.i(LOG, "TransferActivity: button_cancel clicked");
                // return result to main activity
                Intent intent = new Intent();
                setResult(RESULT_CANCELED, intent);
                finish();
                break;
        }
    }

    public void onBackPressed() {
        Log.i(LOG, "TransferActivity: onBackPressed");
        // return result to main activity
        Intent intent = new Intent();
        setResult(RESULT_CANCELED, intent);
        super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public boolean isNetworkAvailable() {
        ConnectivityManager connMgr = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            return true;
        } else {
            return false;
        }
    }

    private class SendTrailTask extends AsyncTask<String, Void, String> {

        // Read the image file and encode it to the base64 format
        private String readImageFile(String path) {
            byte[] bytes;
            byte[] buffer = new byte[8192];
            int bytesRead;
            File file = new File(path);
            if (!file.exists()) {
                Log.w(LOG, "file " + path + " isn't exist");
                return null;
            }
            try {
               FileInputStream inputStream = new FileInputStream(file);
               ByteArrayOutputStream output = new ByteArrayOutputStream();
               while ((bytesRead = inputStream.read(buffer)) != -1) {
                   output.write(buffer, 0, bytesRead);
               }
                 bytes = output.toByteArray();
            }
            catch (IOException e) {
                Log.e(LOG, "exception", e);
                return null;
            }
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        }


        private void writeContent(OutputStream stream) throws IOException, JSONException, ConnectException {
            File file;
            BufferedReader reader;
            String line;
            String loc, timestamp, path;
            String encImage;
            BufferedWriter writer;

            Context context = getApplicationContext();
            writer = new BufferedWriter(new OutputStreamWriter(stream, "UTF-8"));

            // JSON Document, write start
            line = "{\"newtrail\":[";
            writer.write(line);
            Log.d(LOG, "send data: " + line);

            // Write trail information
            line = "{\"type\":\"TrailInfo\",\"access\":\"public\",\"date\":\"" + Iso8061DateTime.get() +
                    "\",\"trailname\":\"" + trailname + "\",\"locationname\":\"" + locationname +
                    "\",\"description\":\"" + description + "\"},";
            writer.write(line);
            Log.d(LOG, "send data: " + line);

            // Write username and password
            line = "{\"type\":\"UserInfo\",\"username\":\"" + username +
                    "\",\"password\":\"" + password + "\"},";
            writer.write(line);
            Log.d(LOG, "send data: " + line);

            // Write location points
            file = new File(context.getExternalFilesDir(null), locsFilename);
            reader = new BufferedReader(new FileReader(file));


            line = "{\"type\":\"LocationCollection\",\"locations\":[";
            writer.write(line);
            Log.d(LOG, "send data: " + line);

            while ((line = reader.readLine()) != null) {
                writer.write(line);
                Log.d(LOG, "send data: " + line);
            }
            line = "]}";
            writer.write(line);
            Log.d(LOG, "send data: " + line);
            reader.close();

            // Write pictures
            file = new File(context.getExternalFilesDir(null), picsFilename);
            if (file.exists()) {
                line = ",{\"type\":\"PictureCollection\",\"pictures\":[\n";
                writer.write(line);
                Log.d(LOG, "send data: " + line);

                reader = new BufferedReader(new FileReader(file));
                boolean first = true;

                // Combine the json entry from the picture info file and encoded real image
                while ((line = reader.readLine()) != null) {
                    JSONObject json = new JSONObject(line);
                    loc = json.getString("loc");
                    timestamp = json.getString("timestamp");
                    path = json.getString("imagepath");
                    encImage = readImageFile(path);
                    if (encImage != null) {
                        if (first) {
                            first = false;
                        } else {
                            writer.write(",\n");
                            Log.d(LOG, "send data: " + ",\n");
                        }
                        line = "{\"timestamp\":\"" + timestamp + "\"," +
                                "\"filename\":\"" + path + "\"," +
                                "\"picturename\":\"\"," +
                                "\"description\":\"\"," +
                                "\"loc\":" + loc + ",";
                        writer.write(line);
                        Log.d(LOG, "send data: " + line);
                        writer.write("\"file\":\"" + encImage + "\"}");
                        // Don't write encoded image to the log
                        Log.d(LOG, "send data: \"file\":\"--Base64EncodedImage--\"}");
                    }
                }
                line = "]}";
                writer.write(line);
                Log.d(LOG, "send data: " + line);
            }
            line = "]}";
            Log.d(LOG, "send data: " + line);
            writer.write(line);
            reader.close();
            writer.close();
        }

        // Callback function that does the http request, and reads response
        protected String doInBackground(String... urls) {
            BufferedReader reader = null;
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(5000 /* milliseconds */);
                conn.setConnectTimeout(5000 /* milliseconds */);
                conn.setRequestMethod("POST");
                conn.setDoInput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setDoOutput(true);
                conn.setChunkedStreamingMode(0);

                // Write request
                writeContent(conn.getOutputStream());

                conn.connect();
                int code = conn.getResponseCode();
                if (code != 200) {
                    return "Http error code:" + code;
                }
                // Read response
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line = reader.readLine();
                reader.close();
                reader = null;

                JSONObject json = new JSONObject(line);
                String status = json.getString("status");
                String errMsg = json.getString("msg");
                if (status.equals("ok")) {
                    return "";
                } else {
                    return errMsg;
                }
            } catch (ConnectException e) {
                Log.e(LOG, "exception", e);
                return "Exception: " + e.getMessage();
            } catch (IOException e) {
                Log.e(LOG, "exception", e);
                return "Exception: " + e.getMessage();
            } catch (JSONException e) {
                Log.e(LOG, "exeption", e);
                return "Exception: " + e.getMessage();
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        Log.e(LOG, e.getMessage());
                    }
                }
            }
        }

        // This receives the response string from the doInBackground function
        @Override
        protected void onPostExecute(String err) {

            Context context = getApplicationContext();
            if (err.equals("")) {
                // No error, all data was sent. We don't need locations and picture files
                // anymore. We don't delete real pictures.
                File file = new File(context.getExternalFilesDir(null), locsFilename);
                file.delete();
                file = new File(context.getExternalFilesDir(null), picsFilename);
                if (file.exists()) {
                    file.delete();
                }
                Log.i(LOG, "Data was sent successfully");

                // return result to main activity
                Intent in = new Intent();
                //intent.putExtra("result", "sent");
                setResult(RESULT_OK, in);
                finish();
            } else {
                showDialog("Error", "I couldn't transfer trail data\n" + err);
                Log.e(LOG, "Error: " + err);
            }
        }

    }

    private void showDialog(String title, String msg) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
    }
}

