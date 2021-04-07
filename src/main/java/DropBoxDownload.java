import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.users.FullAccount;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DropBoxDownload {
    private static final String ACCESS_TOKEN = "DwUPEDuS48cAAAAAAAAAAYffjyMJwHojo94erADrhL-T-CsjCKuVx3T8ToU5KSwY";
    public static void main(String args[]) throws DbxException {
        // Create Dropbox client
        DbxRequestConfig config = DbxRequestConfig.newBuilder("dropbox/java-tutorial").build();
        DbxClientV2 client = new DbxClientV2(config, ACCESS_TOKEN);
        FullAccount account = client.users().getCurrentAccount();
        System.out.println(account.getName().getDisplayName());
        ListFolderResult result = client.files().listFolder("/应用/mind monitor");
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
                FileMetadata metadata = client.files()
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
                        while ((line = br.readLine()) != null) {
                            String[] cols = line.split(",");
                            System.out.println("Coulmn 0= " + cols[0] + " , Column 3=" + cols[3]);
                            try{
                                double deltaVal = Double. parseDouble(cols[3]);
                                deltaVal = Math.pow(10, deltaVal);
                                System.out.println("the delta val is " + deltaVal);
                                cols[3] = String.valueOf(deltaVal);
                            }catch(Exception e){
                                System.out.println(e);
                            }
                            String[] newCols = {cols[0], cols[3]};
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

            result = client.files().listFolderContinue(result.getCursor());
        }
    }
}
