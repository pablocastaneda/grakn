package io.mindmaps.factory;

import io.mindmaps.core.dao.MindmapsGraph;
import io.mindmaps.core.exceptions.ErrorMessage;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;

public class MindmapsClient {
    private static final String DEFAULT_URI = "http://localhost:4567/graph_factory";
    private static final Map<String, MindmapsGraphFactory> openFactories = new HashMap<>();

    public static MindmapsGraph newGraph(){
        return newGraph(DEFAULT_URI);
    }

    public static MindmapsGraph newGraph(String uri){
        try {
            URL url = new URL(uri);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            if(connection.getResponseCode() != 200){
                throw new IllegalArgumentException(ErrorMessage.INVALID_ENGINE_RESPONSE.getMessage(uri, connection.getResponseCode()));
            }

            //Reading from Connection
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append("\n").append(line);
            }
            br.close();
            String config = sb.toString();

            //TODO: We should make config handling generic rather than through files.
            //Creating Temp File
            File file = File.createTempFile("mindmaps-config", ".tmp");
            String path = file.getAbsolutePath();
            BufferedWriter bw = new BufferedWriter(new FileWriter(file));
            bw.write(config);
            bw.close();

            //Creating the actual mindmaps graph using reflection
            FileInputStream fis = new FileInputStream(path);
            PropertyResourceBundle bundle = new PropertyResourceBundle(fis);

            String factoryType;
            try {
                factoryType = bundle.getString("factory.internal");
            } catch(MissingResourceException e){
                throw new IllegalArgumentException(ErrorMessage.MISSING_FACTORY_DEFINITION.getMessage());
            }

            return getFactory(factoryType).newGraph(path);
        } catch (IOException e) {
            throw new IllegalArgumentException(ErrorMessage.CONFIG_NOT_FOUND.getMessage(uri, e.getMessage()));
        }
    }

    private static MindmapsGraphFactory getFactory(String factoryType){
        if(!openFactories.containsKey(factoryType)) {
            MindmapsGraphFactory mindmapsGraphFactory;
            try {
                mindmapsGraphFactory = (MindmapsGraphFactory) Class.forName(factoryType).newInstance();
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                throw new IllegalArgumentException(ErrorMessage.INVALID_FACTORY.getMessage(factoryType));
            }
            openFactories.put(factoryType, mindmapsGraphFactory);
        }
        return openFactories.get(factoryType);
    }
}
