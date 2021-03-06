/*
 * Pairandomizer - An Android app for randomizing situations.
 * Copyright (C) 2018 Jonas Rinner
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package xyz.karpador.pairandomizer;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import xyz.karpador.pairandomizer.data.Scenario;
import xyz.karpador.pairandomizer.exceptions.HttpResponseException;
import xyz.karpador.pairandomizer.helpers.DownloadHelper;

public class MainActivity extends AppCompatActivity {

    private static final String SERVERIP_KEY = "SERVER_IP";
    private static final String SERVERIP_DEFAULT = "https://karpador.xyz/pairandomizer/";
    private static final int SETTINGS_ACTIVITY_REQUEST = 25566;
    private String serverIP;

    private JSONObject serverSpecJson;

    private List<Scenario> allScenarios = new ArrayList<>();
    private List<Scenario> availableScenarios = new ArrayList<>();
    private List<String> availableLanguages = new ArrayList<>();
    private static final String CURRENTSCENARIO_KEY = "CURRENT_SCENARIO";
    private Scenario currentScenario;

    private static final String NAMES_FILE = "names.txt";
    private static final String INDEX_FILE = "index.json";

    private final Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        serverIP = getPreferences(Context.MODE_PRIVATE).getString(SERVERIP_KEY, SERVERIP_DEFAULT);

        findViewById(R.id.randomizeButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String[] names =
                        ((EditText)findViewById(R.id.namesEdit))
                                .getText()
                                .toString()
                                .split("\n");
                if(!names[0].isEmpty()) {
                    List<String> namesList = Arrays.asList(names);
                    final Scenario.SceneMessage sceneMessage = currentScenario.getRandomScene(namesList, random);
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setNeutralButton(R.string.share, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int i) {
                            Intent sendIntent = new Intent();
                            sendIntent.setAction(Intent.ACTION_SEND);
                            sendIntent.putExtra(
                                    Intent.EXTRA_TEXT,
                                    sceneMessage.title + "\n\n" + sceneMessage.message
                            );
                            sendIntent.setType("text/plain");
                            startActivity(sendIntent);
                        }
                    });
                    builder.setTitle(sceneMessage.title)
                            .setMessage(sceneMessage.message)
                            .setPositiveButton(R.string.ok, null)
                            .show();
                } else {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(R.string.error)
                            .setMessage(R.string.error_no_names)
                            .setPositiveButton(R.string.ok, null)
                            .show();
                }
            }
        });

        loadNamesIfAny();

        if(!new File(getFilesDir(), INDEX_FILE).exists()) {
            new LoadDataTask().execute();
        } else {
            try {
                serverSpecJson = new JSONObject(loadFile(INDEX_FILE));
                JSONArray scenariosJson = serverSpecJson.getJSONArray("scenarios");
                allScenarios = new ArrayList<>();
                availableLanguages = new ArrayList<>();
                for(int i = 0; i < scenariosJson.length(); i++) {
                    JSONObject scenarioJson = scenariosJson.getJSONObject(i);
                    String lang =
                            scenarioJson.isNull("lang")
                                    ? null
                                    : scenarioJson.getString("lang");
                    allScenarios.add(
                            new Scenario(
                                    scenarioJson.getString("name"),
                                    scenarioJson.getString("filename"),
                                    lang
                            )
                    );
                    if(lang != null) {
                        if (!availableLanguages.contains(lang))
                            availableLanguages.add(lang);
                    }
                }
                availableScenarios = getAvailableScenarios(allScenarios);
                loadScenario(getPreferences(Context.MODE_PRIVATE).getInt(CURRENTSCENARIO_KEY, 0));
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.server_info:
                showServerInfo();
                return true;
            case R.id.reload_data:
                new LoadDataTask().execute();
                return true;
            case R.id.settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivityForResult(intent, SETTINGS_ACTIVITY_REQUEST);
                return true;
            case R.id.change_scenario:
                showScenarioChangeDialog();
                return true;
            case R.id.scenario_options:
                showScenarioOptionsDialog();
                return true;
            case R.id.about:
                showAboutDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        saveNames();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == SETTINGS_ACTIVITY_REQUEST) {
            if(resultCode == RESULT_OK) {
                if(data.getBooleanExtra(SettingsActivity.SERVER_IP_CHANGED_KEY, false)) {
                    new LoadDataTask().execute();
                }
                else if(data.getBooleanExtra(SettingsActivity.SHOW_ALL_CHANGED_KEY, false)) {
                    availableScenarios = getAvailableScenarios(allScenarios);
                    try {
                        loadScenario(0);
                    } catch(IOException | JSONException ex) {
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.error)
                                .setMessage(R.string.error_loading_file)
                                .setPositiveButton(R.string.ok, null)
                                .show();
                    }
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private List<Scenario> getAvailableScenarios(List<Scenario> currentScenarios) {
        if(PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("scenario_show_all", false))
            return currentScenarios;
        List<Scenario> result = new ArrayList<>();
        String lang = Locale.getDefault().getLanguage();
        if(!availableLanguages.contains(lang))
            lang = "en";
        for(Scenario scenario : currentScenarios) {
            if(scenario.getLang() == null) {
                result.add(scenario);
                continue;
            }
            if(scenario.getLang().equals(lang))
                result.add(scenario);
        }
        return result;
    }

    private void loadNamesIfAny() {
        if(new File(getFilesDir(), NAMES_FILE).exists()) {
            try {
                FileInputStream stream = openFileInput(NAMES_FILE);
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                String line = reader.readLine();
                EditText namesEdit = findViewById(R.id.namesEdit);
                while(line != null) {
                    namesEdit.append(line);
                    line = reader.readLine();
                    if(line != null)
                        namesEdit.append("\n");
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveNames() {
        String fileContent = ((EditText)findViewById(R.id.namesEdit)).getText().toString();
        try {
            saveFile(NAMES_FILE, fileContent);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadScenario(int index) throws IOException, JSONException {
        currentScenario = availableScenarios.get(index);
        if(!currentScenario.isDataLoaded()) {
            String scenarioJson = loadFile(currentScenario.getFilename());
            currentScenario.loadJsonData(new JSONObject(scenarioJson));
        }
        getPreferences(Context.MODE_PRIVATE).edit().putInt(CURRENTSCENARIO_KEY, index).apply();
    }

    private void saveFile(String filename, String fileContent) throws IOException {
        FileOutputStream stream = openFileOutput(filename, Context.MODE_PRIVATE);
        stream.write(fileContent.getBytes());
        stream.close();
    }

    private String loadFile(String filename) throws IOException {
        FileInputStream stream = openFileInput(filename);
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder resultBuilder = new StringBuilder();
        String line;
        while((line = reader.readLine()) != null) {
            resultBuilder.append(line).append('\n');
        }
        reader.close();
        return resultBuilder.toString();
    }

    public void showServerInfo() {
        try {
            String message =
                    getString(
                            R.string.server_info_message,
                            serverSpecJson.getString("server_name"),
                            serverSpecJson.getString("comment")
                    );
            new AlertDialog.Builder(this)
                    .setTitle(R.string.server_info)
                    .setMessage(message)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private int selectedScenarioIndex = 0;
    private void showScenarioChangeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.change_scenario);
        String[] options = new String[availableScenarios.size()];
        for(int i = 0; i < options.length; i++) {
            options[i] = availableScenarios.get(i).getName();
        }
        selectedScenarioIndex = getPreferences(Context.MODE_PRIVATE).getInt(CURRENTSCENARIO_KEY, 0);
        builder.setSingleChoiceItems(
                options,
                selectedScenarioIndex,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        selectedScenarioIndex = i;
                    }
                }
        );
        builder.setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        try {
                            loadScenario(selectedScenarioIndex);
                            getPreferences(Context.MODE_PRIVATE).edit()
                                    .putInt(CURRENTSCENARIO_KEY, selectedScenarioIndex)
                                    .apply();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
        builder.show();
    }

    private boolean[] enabledItems;
    private void showScenarioOptionsDialog() {
        enabledItems = currentScenario.getEnabledScenes();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.enabled_scenes);
        builder.setMultiChoiceItems(
                currentScenario.getSceneNames(),
                enabledItems,
                new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int index, boolean checked) {
                        enabledItems[index] = checked;
                    }
                }
        );
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int index) {
                boolean enabledAny = false;
                for (boolean enabledItem : enabledItems) {
                    if (enabledItem) {
                        enabledAny = true;
                        break;
                    }
                }
                if(!enabledAny) {
                    enabledItems[0] = true;
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(R.string.info)
                            .setMessage(R.string.info_all_disabled)
                            .setPositiveButton(R.string.ok, null)
                            .show();
                }
                currentScenario.setEnabledScenes(enabledItems);
            }
        });
        builder.show();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setMessage(R.string.about_text)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private class LoadDataTask extends AsyncTask<Void, Void, String> {
        private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(MainActivity.this);
            progressDialog.setCancelable(false);
            progressDialog.setTitle(R.string.please_wait);
            progressDialog.setMessage(getString(R.string.loading_data));
            progressDialog.show();
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                String indexFileContent =
                        DownloadHelper.downloadFileContents(serverIP + "/" + INDEX_FILE);
                // Validate the file content
                serverSpecJson = new JSONObject(indexFileContent);
                // At this point, it's valid JSON, so save it
                saveFile(INDEX_FILE, indexFileContent);
                // Loop through the files the server lists and download them all
                JSONArray scenariosJson = serverSpecJson.getJSONArray("scenarios");
                allScenarios = new ArrayList<>();
                availableLanguages = new ArrayList<>();
                for(int i = 0; i < scenariosJson.length(); i++) {
                    JSONObject scenarioJson = scenariosJson.getJSONObject(i);
                    String filename = scenarioJson.getString("filename");
                    String fileContent =
                            DownloadHelper.downloadFileContents(serverIP + "/" + filename);
                    // Validate the file content
                    new JSONObject(fileContent);
                    // Valid JSON at this point, so save it
                    saveFile(filename, fileContent);
                    String lang =
                            scenarioJson.isNull("lang")
                                    ? null
                                    : scenarioJson.getString("lang");
                    allScenarios.add(
                            new Scenario(scenarioJson.getString("name"), filename, lang)
                    );
                    if(lang != null) {
                        if (!availableLanguages.contains(lang))
                            availableLanguages.add(lang);
                    }
                }
                availableScenarios = getAvailableScenarios(allScenarios);
                loadScenario(0);
            } catch (IOException e) {
                e.printStackTrace();
                return e.getMessage();
            } catch (HttpResponseException e) {
                e.printStackTrace();
                return e.getMessage();
            } catch (JSONException e) {
                e.printStackTrace();
                return getString(R.string.error_invalid_json);
            }

            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            progressDialog.dismiss();
            progressDialog = null;
            if(result != null) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.error)
                        .setMessage(result)
                        .setNeutralButton(R.string.ok, null)
                        .show();
            }
        }
    }
}
