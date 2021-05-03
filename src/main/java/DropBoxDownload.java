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
                        FileWriter csvWriter = new FileWriter("Score.csv");
                        System.out.println("begin writing csv");
                        csvWriter.append("TimeStamp");
                        csvWriter.append(",");
                        csvWriter.append("score");
                        csvWriter.append("\n");
                        csvWriter.flush();
                        float remLen = 0;
                        float hour = 0;
                        line = br.readLine();
                        while ((line = br.readLine()) != null) {

                            int count  = 0;
                            long totalScore = 0;
                            String[] cols = line.split(",");
                            String timeStamp = cols[0];
                            while (line != null && count < 300){
                                try{
                                    String[] colss = line.split(",");
                                    line = br.readLine();
                                    if (colss[2].equals("Delta_AF7")) continue;
                                    double deltaVal = Double. parseDouble(colss[2]);
                                    deltaVal = Math.pow(10, deltaVal/10) * 60;
                                    totalScore += deltaVal;
                                    count += 1;
                                    //System.out.println("the total score is " + totalScore);
                                }catch(Exception e){
//                                    System.out.println(e);
                                    line = br.readLine();
                                    continue;
                                }

                            }

                            long averageScore = totalScore / count;
                            System.out.println("the average score is " + averageScore);
                            if (averageScore >= 60 && averageScore <= 70){
                                remLen += 300;
                                hour = (remLen / 3600);
                            }

                            String[] newCols = {cols[0], String.valueOf(averageScore)};
                            //System.out.println("the new Cols is " + newCols[0] + " " + newCols[1]);

                            HttpGet httpGet = new HttpGet("http://ec2-3-214-217-18.compute-1.amazonaws.com/senddata.php");
                            URI uri = new URIBuilder(httpGet.getURI())
                                    .addParameter("rtype", "put")
                                    .addParameter("table", "eeg_sensor")
                                    .addParameter("patient_id", "1")
                                    .addParameter("sensor_id", "1")
                                    .addParameter("time_stamp", timeStamp)
                                    .addParameter("value", String.valueOf(averageScore))
                                    .addParameter("user_name", "default")
                                    .addParameter("password", "rapid123")
                                    .build();
                            ((HttpRequestBase) httpGet).setURI(uri);
                            CloseableHttpClient client = HttpClientBuilder.create().build();
                            CloseableHttpResponse response = client.execute(httpGet);
                            HttpEntity entity = response.getEntity();
                            String str = EntityUtils.toString(entity);
                            System.out.println(str);
                            System.out.println("the status code is " + response.getStatusLine());
                            System.out.println("the value is " + String.valueOf(averageScore));
                            client.close();

                            HttpGet httpGet1 = new HttpGet("http://ec2-3-214-217-18.compute-1.amazonaws.com/senddata.php");
                            //String val = String.format("%dh%dmin", hour, min);
                            String val = String.valueOf(hour);
                            URI uri1 = new URIBuilder(httpGet1.getURI())
                                    .addParameter("rtype", "put")
                                    .addParameter("table", "eeg_rem")
                                    .addParameter("patient_id", "1")
                                    .addParameter("sensor_id", "1")
                                    .addParameter("time_stamp", timeStamp)
                                    .addParameter("value", val)
                                    .addParameter("user_name", "default")
                                    .addParameter("password", "rapid123")
                                    .build();
                            ((HttpRequestBase) httpGet1).setURI(uri1);
                            CloseableHttpClient client1 = HttpClientBuilder.create().build();
                            CloseableHttpResponse response1 = client1.execute(httpGet1);
                            HttpEntity entity1 = response1.getEntity();
                            String str1 = EntityUtils.toString(entity1);
                            System.out.println(str1);
                            System.out.println("the status code is " + response1.getStatusLine());
                            System.out.println("the rem len is " + val);
                            client1.close();

                            csvWriter.append(String.join(",", newCols));
                            System.out.println(String.join(",", newCols));
                            csvWriter.append("\n");
                            csvWriter.flush();
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
