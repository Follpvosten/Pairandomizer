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

package xyz.karpador.pairandomizer.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Scenario {
    private static class Scene {
        private String name;
        private List<String> messages;

        public Scene(String name, List<String> messages) {
            this.name = name;
            this.messages = messages;
        }

        public String getName() {
            return name;
        }

        public String getRandomMessage(Random random) {
            return messages.get(random.nextInt(messages.size()));
        }
    }

    private final String name;
    private final String filename;
    private final String lang;
    private boolean dataLoaded = false;

    private final List<Scene> scenes = new ArrayList<>();
    private final List<String> soloMessages = new ArrayList<>();
    private boolean[] enabledScenes;
    private int enabledCount = 0;

    public Scenario(String name, String filename, String lang) {
        this.name = name;
        this.filename = filename;
        this.lang = lang;
    }

    public String getName() {
        return name;
    }

    public String getFilename() {
        return filename;
    }

    public String getLang() {
        return lang;
    }

    public boolean[] getEnabledScenes() {
        return enabledScenes;
    }

    public void setEnabledScenes(boolean[] enabledScenes) {
        this.enabledScenes = enabledScenes;
        enabledCount = 0;
        for (boolean enabledScene : enabledScenes)
            if (enabledScene) enabledCount++;
    }

    public String[] getSceneNames() {
        String[] result = new String[scenes.size()];
        for(int i = 0; i < result.length; i++)
            result[i] = scenes.get(i).getName();
        return result;
    }

    public void loadJsonData(JSONObject json) throws JSONException {
        JSONArray scenesJson = json.getJSONArray("scenes");
        for(int i = 0; i < scenesJson.length(); i++) {
            JSONObject sceneJson = scenesJson.getJSONObject(i);
            String sceneName = sceneJson.getString("name");
            JSONArray sceneMessagesJson = sceneJson.getJSONArray("messages");
            List<String> sceneMessages = new ArrayList<>();
            for(int j = 0; j < sceneMessagesJson.length(); j++) {
                sceneMessages.add(sceneMessagesJson.getString(j));
            }
            scenes.add(new Scene(sceneName, sceneMessages));
        }
        enabledScenes = new boolean[scenes.size()];
        enabledCount = enabledScenes.length;
        for(int i = 0; i < enabledScenes.length; i++)
            enabledScenes[i] = true;
        JSONArray soloMessagesJson = json.getJSONArray("solo_messages");
        for(int i = 0; i < soloMessagesJson.length(); i++) {
            soloMessages.add(soloMessagesJson.getString(i));
        }
        dataLoaded = true;
    }

    public boolean isDataLoaded() {
        return dataLoaded;
    }

    // Simple return type to make things easier
    public static class SceneMessage {
        public String title;
        public String message;

        public SceneMessage(String title, String message) {
            this.title = title;
            this.message = message;
        }
    }

    public SceneMessage getRandomScene(List<String> names, Random random) {
        Collections.shuffle(names, random);
        StringBuilder resultBuilder = new StringBuilder();
        Scene scene = scenes.get(0);
        if(enabledCount < 2) {
            for (int i = 0; i < enabledScenes.length; i++)
                if (enabledScenes[i]) scene = scenes.get(i);
        } else {
            while(true) {
                int index = random.nextInt(scenes.size());
                if(enabledScenes[index]) {
                    scene = scenes.get(index);
                    break;
                }
            }
        }
        for(int i = 0; i < names.size(); i += 2) {
            if(i < names.size() - 1) {
                resultBuilder
                        .append(String.format(
                                scene.getRandomMessage(random),
                                names.get(i), names.get(i+1))
                        );
                if(i < names.size() - 2)
                    resultBuilder.append('\n');
            } else {
                resultBuilder.append(
                        String.format(
                                soloMessages.get(random.nextInt(soloMessages.size())),
                                names.get(i)
                        )
                );
            }
        }
        return new SceneMessage(scene.getName(), resultBuilder.toString());
    }
}
