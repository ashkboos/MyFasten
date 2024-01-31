package eu.fasten.core.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.FileUtils;

public class FilesUtils {
    public static File getFileFromResources(final String fileName) {
        InputStream inputStream = FileUtils.class.getClassLoader().getResourceAsStream(fileName);
        if (inputStream == null) {
            throw new RuntimeException("File not found: " + fileName);
        } else {
            try {
                File file = new File(fileName);
                FileOutputStream outputStream = new FileOutputStream(file);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
                outputStream.close();
                inputStream.close();
                return file;
            } catch (IOException e){
                throw new RuntimeException(e);
            }
        }
    }
}
