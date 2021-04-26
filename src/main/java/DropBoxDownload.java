import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.users.FullAccount;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.*;
import java.net.URI;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DropBoxDownload {
    private static final String ACCESS_TOKEN = "DwUPEDuS48cAAAAAAAAAAYffjyMJwHojo94erADrhL-T-CsjCKuVx3T8ToU5KSwY";
    private static String inputStreamToString(InputStream is) {

        String line = "";
        StringBuilder total = new StringBuilder();

        // Wrap a BufferedReader around the InputStream
        BufferedReader rd = new BufferedReader(new InputStreamReader(is));

        try {
            // Read response until the end
            while ((line = rd.readLine()) != null) {
                total.append(line);
            }
        } catch (IOException e) {
            //Log.e(TAG, e.getLocalizedMessage(), e);
        }

        // Return full string
        return total.toString();
    }
    public static void main(String args[]) throws DbxException {
        // Create Dropbox client
        DbxRequestConfig config = DbxRequestConfig.newBuilder("dropbox/java-tutorial").build();
        DbxClientV2 clientDBX = new DbxClientV2(config, ACCESS_TOKEN);
        FullAccount account = clientDBX.users().getCurrentAccount();
        System.out.println(account.getName().getDisplayName());
        ListFolderResult result = clientDBX.files().listFolder("/应用/mind monitor");
        while (true) {
            for (Metadata metadata : ((ListFolderResult) result).getEntries()) {
                System.out.println(metadata.getPathLower());
            }
            int size = ((ListFolderResult) result).getEntries().size();
            Metadata lastMetadata = ((ListFolderResult) result).getEntries().get(size - 1);
            String path = lastMetadata.getPathLower();
            System.out.println("the path is " + path);
            String localPath = "eeg.zip";
            FileInputStream fis = null;
            ZipInputStream zipIs = null;
            ZipEntry zEntry = null;
            try{
                OutputStream outputStream = new FileOutputStream(localPath);
                FileMetadata metadata = clientDBX.files()
                        .downloadBuilder(path)
                        .download(outputStream);
                fis = new FileInputStream(localPath);
                zipIs = new ZipInputStream(new BufferedInputStream(fis));
                while((zEntry = zipIs.getNextEntry()) != null){
                    try{
                        byte[] tmp = new byte[4*1024];
                        FileOutputStream fos = null;
                        String opFilePath = zEntry.getName();
                        System.out.println("Extracting file to "+opFilePath);
                        fos = new FileOutputStream(opFilePath);
                        int s = 0;
                        while((s = zipIs.read(tmp)) != -1){
                            fos.write(tmp, 0 , s);
                        }
                        fos.flush();
                        BufferedReader br = new BufferedReader(new FileReader(opFilePath));
                        String line;
                        FileWriter csvWriter = new FileWriter("sleepScore.csv");
                        csvWriter.append("TimeStamp");
                        csvWriter.append(",");
                        csvWriter.append("score");
                        csvWriter.append("\n");
                        double remLen = 0;
                        int hour = 0;
                        int min = 0;
                        double sec = 0;
                        while ((line = br.readLine()) != null) {
                            String[] cols = line.split(",");
                            try{
                                double deltaVal = Double. parseDouble(cols[3]);
                                deltaVal = Math.pow(10, deltaVal/10) * 60;
                                if (deltaVal < 40){
                                    remLen += 0.1;
                                    hour = (int)(remLen / 3600);
                                    min = (int)((remLen % 3600) / 60);
                                    sec = remLen % 60;
                                }
                                System.out.println("the sleep score is " + deltaVal);
                                cols[3] = String.valueOf(deltaVal);
                            }catch(Exception e){
                                continue;

                            }
                            String[] newCols = {cols[0], cols[3]};
                            if (cols[3].equals("Delta_AF8")) continue;
                            HttpGet httpGet = new HttpGet("http://ec2-54-236-12-35.compute-1.amazonaws.com/senddata.php");
                            URI uri = new URIBuilder(httpGet.getURI())
                                    .addParameter("rtype", "put")
                                    .addParameter("table", "eeg_rem")
                                    .addParameter("patient_id", "1")
                                    .addParameter("sensor_id", "1")
                                    .addParameter("time_stamp", cols[0])
                                    .addParameter("value", cols[3])
                                    .addParameter("user_name", "default")
                                    .addParameter("password", "rapid123")
                                    .build();
                            URI uri1 = new URIBuilder(httpGet.getURI())
                                    .addParameter("rtype", "put")
                                    .addParameter("table", "eeg_sensor")
                                    .addParameter("patient_id", "1")
                                    .addParameter("sensor_id", "1")
                                    .addParameter("time_stamp", cols[0])
                                    .addParameter("value", String.format("%dh%dmin%fseconds", hour, min, sec))
                                    .addParameter("user_name", "default")
                                    .addParameter("password", "rapid123")
                                    .build();
                            ((HttpRequestBase) httpGet).setURI(uri);
                            CloseableHttpClient client = HttpClientBuilder.create().build();
                            CloseableHttpResponse response = client.execute(httpGet);
                            HttpEntity entity = response.getEntity();
                            String str = EntityUtils.toString(entity);
                            System.out.println(str);
                            System.out.println("the status code is ");
                            System.out.println(response.getStatusLine());
                            ((HttpRequestBase) httpGet).setURI(uri);
                            CloseableHttpClient client1 = HttpClientBuilder.create().build();
                            CloseableHttpResponse response1 = client1.execute(httpGet);
                            HttpEntity entity1 = response1.getEntity();
                            String str1 = EntityUtils.toString(entity);
                            System.out.println(str1);
                            System.out.println("the status code is ");
                            System.out.println(response1.getStatusLine());
                            client.close();
                            csvWriter.append(String.join(",", newCols));
                            csvWriter.append("\n");
                        }
                        fos.close();
                    } catch(Exception ex){
                        System.out.println(ex);
                    }
                }
                zipIs.close();


            }catch (IOException e){
                System.out.println(e);
            }


            if (!result.getHasMore()) {
                break;
            }

            result = clientDBX.files().listFolderContinue(result.getCursor());
        }
    }
}
