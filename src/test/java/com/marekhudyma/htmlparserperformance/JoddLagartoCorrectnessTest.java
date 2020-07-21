package com.marekhudyma.htmlparserperformance;


import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class JoddLagartoCorrectnessTest {

    private static final String IN_FOLDER = "IN";
    private static final String OUT_FOLDER = "OUT";

    // 862_542
    @Test
    void shouldParseAllHtmlPages() throws IOException {
        LagartoHtmlParser parser = new LagartoHtmlParser();
        int counter = 0;
        List<String> failed = new LinkedList<>();

        for (String zipFileName : new File(IN_FOLDER).list()) {
            File file = new File(Paths.get(IN_FOLDER, zipFileName).toAbsolutePath().toString());
            ZipFile zipFile = new ZipFile(file);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    String html = IOUtils.toString(zipFile.getInputStream(entry), StandardCharsets.UTF_8.name());
                    try {
                        parser.parse(html);
                    } catch (Exception e) {
                        String filePathInsideZip = entry.getName();
                        String[] filePathInsideZipDivided = filePathInsideZip.split("/");
                        String fileName = filePathInsideZipDivided[filePathInsideZipDivided.length - 1];
                        failed.add(filePathInsideZip);
                        System.err.println(filePathInsideZip + " " + e.getClass());
                        FileUtils.writeStringToFile(new File(Paths.get(OUT_FOLDER, fileName).toAbsolutePath().toString()),
                                html,
                                StandardCharsets.UTF_8);
                    }
                    counter++;
                    if (counter % 1_000 == 0) {
                        System.out.println("counter = " + counter);
                    }
                }
            }
        }

        System.out.println("tested.count=" + counter);
        failed.forEach(System.err::println);
    }
}