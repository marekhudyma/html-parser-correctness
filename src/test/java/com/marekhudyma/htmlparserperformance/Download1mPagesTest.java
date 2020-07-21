package com.marekhudyma.htmlparserperformance;


import com.google.common.collect.Lists;
import net.lingala.zip4j.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLException;
import java.io.*;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Download1mPagesTest {

    private final int timeout = 30;
    private final RequestConfig config = RequestConfig.custom()
            .setConnectTimeout(timeout * 1000)
            .setConnectionRequestTimeout(timeout * 1000)
            .setSocketTimeout(timeout * 1000).build();
    private final CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(config).build();

    private final static String OUTPUT_DIRECTORY_PATH = "ADD_PATH_HERE";

    @Test
    @Disabled
    public void downloadTop1mPages() throws Exception {
        DomainFeeder domainFeeder = new DomainFeeder(0, 1_000_000);
        domainFeeder.init();
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(200);

        List<Domain> domains;
        while ((domains = domainFeeder.getDomains()) != null) {

            String directoryName = null;
            String directoryFullPath = null;
            String zipFullPath = null;
            for (int i = 0; i < 3; i++) {
                CountDownLatch countDownLatch = new CountDownLatch(domains.size());
                directoryName = getOutputDirectoryNameForRank(domains.get(0));
                directoryFullPath = Paths.get(OUTPUT_DIRECTORY_PATH, directoryName).toAbsolutePath().toString();
                zipFullPath = Paths.get(directoryFullPath + ".zip").toAbsolutePath().toString();
                for (Domain domain : domains) {
                    executor.execute(
                            new Downloader(httpClient,
                                    domain,
                                    directoryFullPath,
                                    zipFullPath,
                                    countDownLatch));
                }
                countDownLatch.await();
                System.out.println("iteration = " + i);
            }
            deleteEmptyFilesInDirectory(directoryFullPath);

            ZipFile zipFile = new ZipFile(new File(zipFullPath));
            zipFile.addFolder(new File(directoryFullPath));
            FileUtils.deleteDirectory(new File(directoryFullPath));
        }
    }

    private String getOutputDirectoryNameForRank(Domain domain) {
        int directoryNumber = (domain.getRank() - 1) / 1_000;
        String directoryNumberStr = StringUtils.leftPad("" + directoryNumber, 4, "0");
        return directoryNumberStr;
    }

    private void deleteEmptyFilesInDirectory(String directoryFullPath) {
        List<String> filePaths = filePathsInDirectory(directoryFullPath);
        filePaths.stream()
                .map(File::new)
                .filter(file -> file.length() == 0)
                .forEachOrdered(File::delete);
    }

    private static List<String> filePathsInDirectory(String directoryFullPath) {
        File directory = new File(directoryFullPath);
        String[] list = directory.list();
        if (list != null) {
            return Stream.of(list)
                    .map(fileName -> Paths.get(directoryFullPath, fileName).toAbsolutePath().toString())
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private static class Downloader implements Runnable {
        private final CloseableHttpClient httpClient;
        private final Domain domain;
        private final String outputDirPath;
        private final String zipFullPath;
        private final CountDownLatch countDownLatch;

        public Downloader(CloseableHttpClient httpClient,
                          Domain domain,
                          String outputDirPath,
                          String zipFullPath,
                          CountDownLatch countDownLatch) {
            this.httpClient = httpClient;
            this.domain = domain;
            this.outputDirPath = outputDirPath;
            this.zipFullPath = zipFullPath;
            this.countDownLatch = countDownLatch;
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < 3; i++) {
                    try {
                        download(httpClient, domain, outputDirPath, zipFullPath);
                        return;
                    } catch (Exception e) {
                        if (i == 2) {
                            System.out.println(e.getMessage());
                        }
                    }
                }
            } finally {
                countDownLatch.countDown();
            }
        }
    }

    private static void download(CloseableHttpClient httpClient, Domain domain, String outputDirPath, String zipFullPath) {
        String filePath = Paths.get(outputDirPath, domain.getRank() + "_" + domain.getDomain() + ".html").toAbsolutePath().toString();
        try {
            Files.createDirectories(Paths.get(outputDirPath));
            if (Files.exists(Paths.get(zipFullPath))) { // zip exists, nothing to do
                return;
            }
            if (!Files.exists(Paths.get(filePath))) {
                String body = downloadAllProtocols(httpClient, domain.getDomain());
                if (body == null) { // no body, no exception - skip
                    createEmptyFile(filePath);
                } else {
                    save(filePath, body);
                }
            }
            System.out.println("process=downloadPageSuccess, page=" + domain.getDomain());
        } catch (UnknownHostException e) { // domain doesn't exist
            createEmptyFile(filePath);
        } catch (HttpHostConnectException e) { // nobody is listening
            createEmptyFile(filePath);
        } catch (ConnectTimeoutException | SocketTimeoutException e) { // repeat
            throw new RuntimeException(e); // it need to be rerun
        } catch (SSLException | ClientProtocolException e) { // skip
            createEmptyFile(filePath);
        } catch (NoHttpResponseException e) {
            createEmptyFile(filePath);
        } catch (IOException e) {
            throw new RuntimeException(e); // it need to be rerun
        }
    }

    private static String downloadAllProtocols(CloseableHttpClient httpClient, String domain) throws IOException {
        String body = download(httpClient, "http://" + domain);
        if (body == null) {
            body = download(httpClient, "https://" + domain);
        }
        if (body == null) {
            body = download(httpClient, "http://wwww." + domain);
        }
        if (body == null) {
            body = download(httpClient, "https://www" + domain);
        }
        return body;
    }

    private static void save(String filePath, String content) throws FileNotFoundException, UnsupportedEncodingException {
        try (PrintWriter out = new PrintWriter(filePath, "UTF-8")) {
            out.write(content);
        }
    }

    private static void createEmptyFile(String filePath) {
        try {
            File file = new File(filePath);
            file.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class DomainFeeder {

        private final int lowerRankLimit;

        private final int upperRankLimit;

        private final ConcurrentLinkedQueue<List<Domain>> queue;

        public DomainFeeder(int lowerRankLimit, int upperRankLimit) {
            this.lowerRankLimit = lowerRankLimit;
            this.upperRankLimit = upperRankLimit;
            this.queue = new ConcurrentLinkedQueue<>();
        }

        public void init() throws IOException {
            File top1m = new File(getClass().getClassLoader().getResource("top-1m.csv").getFile());
            try (BufferedReader buffer = new BufferedReader(new InputStreamReader(new FileInputStream(top1m)))) {
                List<Domain> domains = buffer.lines()
                        .map((String line) -> new Domain(line.split(",")[1], Integer.parseInt(line.split(",")[0])))
                        .filter(domain -> domain.getRank() > lowerRankLimit)
                        .filter(domain -> domain.getRank() < upperRankLimit)
                        .collect(Collectors.toList());
                queue.addAll(Lists.partition(domains, 1_000));
            }
        }

        /**
         * @return Domain object or null when queue is empty
         */
        public List<Domain> getDomains() {
            return queue.poll();
        }
    }

    private static class Domain {
        private final String domain;
        private final int rank;

        public Domain(String domain, int rank) {
            this.domain = domain;
            this.rank = rank;
        }

        public String getDomain() {
            return domain;
        }

        public int getRank() {
            return rank;
        }
    }

    public static String download(CloseableHttpClient httpClient, String fullUrl) throws IOException {
        HttpGet request = new HttpGet(fullUrl);
        CloseableHttpResponse response = null;
        try {
            response = httpClient.execute(request);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                return EntityUtils.toString(entity);
            } else {
                return null;
            }
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }
}
