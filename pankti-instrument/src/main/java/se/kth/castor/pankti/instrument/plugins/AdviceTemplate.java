package se.kth.castor.pankti.instrument.plugins;

import com.thoughtworks.xstream.XStream;
import se.kth.castor.pankti.instrument.converters.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public interface AdviceTemplate {
    public static final String PROPERTY_FILE_PATH = "/tmp/pankti-object-data/paths.properties";
    public static final int FILE_NAME_LENGTH = 50;

    XStream xStream = new XStream();

    static void setUpXStream() {
        xStream.registerConverter(new ClassLoaderConverter());
        xStream.registerConverter(new FileCleanableConverter());
        xStream.registerConverter(new InflaterConverter());
        xStream.registerConverter(new CleanerImplConverter());
        xStream.registerConverter(new ThreadConverter());
        xStream.registerConverter(new ThreadGroupConverter());
    }

    static String setUpInvokedMethodsCSVFile(String storageDir) throws Exception {
        String[] HEADERS = {"visibility", "parent-FQN", "method-name", "param-list", "return-type",
                "param-signature", "has-mockable-invocations", "nested-invocations"};

        File invokedMethodsCSVFile = new File(storageDir + "invoked-methods.csv");
        if (!invokedMethodsCSVFile.exists()) {
            FileWriter myWriter = new FileWriter(invokedMethodsCSVFile);
            myWriter.write(String.join(",", HEADERS));
            myWriter.close();
        }
        return invokedMethodsCSVFile.getAbsolutePath();
    }

    static Map<Type, String> setUpFiles(String path) {
        Map<Type, String> fileNameMap = new HashMap<>();
        try {
            String storageDir = "/tmp/pankti-object-data/";
            Files.createDirectories(Paths.get(storageDir));
            String invokedMethodsCSVFilePath = setUpInvokedMethodsCSVFile(storageDir);
            String filePath = storageDir + getUniqueCode(path);
            fileNameMap.put(Type.RECEIVING_PRE, filePath + "-receiving.xml");
            fileNameMap.put(Type.RECEIVING_POST, filePath + "-receiving-post.xml");
            fileNameMap.put(Type.PARAMS, filePath + "-params.xml");
            fileNameMap.put(Type.RETURNED, filePath + "-returned.xml");
            fileNameMap.put(Type.INVOCATION_COUNT, filePath + "-count.txt");
            fileNameMap.put(Type.OBJECT_PROFILE_SIZE, filePath + "-object-profile-sizes.txt");
            fileNameMap.put(Type.INVOKED_METHODS, invokedMethodsCSVFilePath);
            return fileNameMap;
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
        return fileNameMap;
    }

    static String getUniqueCode(String path) throws IOException {
        Properties properties = loadProperties();
        String uniqueCode = properties.getProperty(path);
        if (uniqueCode == null) {
            uniqueCode = generateShortFileName(FILE_NAME_LENGTH);
            properties.setProperty(path, uniqueCode);
            saveProperties(properties);
        }
        return uniqueCode;
    }

    static Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        File file = new File(PROPERTY_FILE_PATH);
        if (file.exists()) {
            try (InputStream inputStream = new FileInputStream(file)) {
                properties.load(inputStream);
            }
        }
        return properties;
    }

    static void saveProperties(Properties properties) throws IOException {
        try (OutputStream outputStream = new FileOutputStream(PROPERTY_FILE_PATH)) {
            properties.store(outputStream, null);
        }
    }

    static String generateShortFileName(int length) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int index = (int) (Math.random() * characters.length());
            sb.append(characters.charAt(index));
        }
        return sb.toString();
    }
}


