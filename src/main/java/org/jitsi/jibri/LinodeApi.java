package org.jitsi.jibri;

import java.util.Random;
import java.util.logging.Logger;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;
import org.json.JSONArray;

import java.lang.StringBuilder;

public class LinodeApi {

    private Logger logger = Logger.getLogger(LinodeApi.class.getName());
    private final String UBUNTU_JIBRI = "ubuntu-jibri";
    private final String IF_CONFIG_ME_ADDRESS = "http://ifconfig.me/";
    private final String LINODE_INSTANCES_ENDPOINT = "https://api.linode.com/v4/linode/instances";
    private final String LINODE_IMAGES_ENDPOINT = "https://api.linode.com/v4/images";

    public int findCurrentLinodeId(String personalAccessToken){
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(IF_CONFIG_ME_ADDRESS).header("User-Agent", "curl/7.47.0").get().build();
        String serverIp = null;
        try {
            Response response = client.newCall(request).execute();
            serverIp = response.body().string();
            logger.info("server IPv4 = " + serverIp + " (from " + IF_CONFIG_ME_ADDRESS + ")");
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }

        request = new Request.Builder().url(LINODE_INSTANCES_ENDPOINT)
                .header("Authorization", "Bearer " + personalAccessToken).get().build();
        try {
            Response response = client.newCall(request).execute();
            JSONObject json = new JSONObject(response.body().string());
            JSONArray linodesArray = json.getJSONArray("data");
            for(int i = 0; i < linodesArray.length(); i++) {
                JSONObject linodeInstance = linodesArray.getJSONObject(i);
                JSONArray ipv4s = linodeInstance.getJSONArray("ipv4");
                for(int j = 0; j < ipv4s.length(); j++){
                    String ipv4 = ipv4s.getString(j);
                    if(ipv4.equals(serverIp)){
                        int linodeId = linodeInstance.getInt("id");
                        logger.info("server Linode id = " + linodeId);
                        return linodeId;
                    }
                }
           }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return -1;
    }

    private String getRandPassword(int n) {
        String characterSet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        Random random = new Random(System.nanoTime());
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < n; i++) {
            int rIndex = random.nextInt(characterSet.length());
            password.append(characterSet.charAt(rIndex));
        }
        return password.toString();
    }

    private String findUbuntuJibriImageId(String personalAccessToken){
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(LINODE_IMAGES_ENDPOINT)
                .header("Authorization", "Bearer " + personalAccessToken).get().build();
        try {
            Response response = client.newCall(request).execute();
            JSONObject json = new JSONObject(response.body().string());
            JSONArray imagesArray = json.getJSONArray("data");
            for(int i = 0; i < imagesArray.length(); i++) {
                JSONObject image = imagesArray.getJSONObject(i);
                if(image.getString("label").equals(UBUNTU_JIBRI))
                    return image.getString("id");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public int createNode(String personalAccessToken) {
        logger.info("your Linode personal access token: " + personalAccessToken);
        String rootPass = getRandPassword(100);
        String ubuntuJibriImageId = findUbuntuJibriImageId(personalAccessToken);
        if(ubuntuJibriImageId == null){
            logger.info("can't find ubuntu-jibri image id. cancelling new Linode creation request."); // TODO use logger.error
            return -1;
        }
        OkHttpClient client = new OkHttpClient();
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        String payload = "{\"type\":\"g6-standard-4\",\"region\":\"us-east\",\"backups_enabled\":false,\"root_pass\":\"" + rootPass
                + "\",\"booted\":true,\"image\":\"" + ubuntuJibriImageId  + "\"}";
        RequestBody body = RequestBody.create(JSON, payload);
        Request request = new Request.Builder().url(LINODE_INSTANCES_ENDPOINT)
                .header("Authorization", "Bearer " + personalAccessToken).post(body).build();
        try {
            Response response = client.newCall(request).execute();
            JSONObject json = new JSONObject(response.body().string());
            logger.info(json.toString());
            int newLinodeId = json.getInt("id");
            logger.info("created new Linode instance with root password as: " + rootPass);
            return newLinodeId;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    public void deleteNode(String personalAccessToken, int linodeId) {
        logger.info("your Linode personal access token: " + personalAccessToken);
        logger.info("Linode instance id to delete: " + linodeId);
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(LINODE_INSTANCES_ENDPOINT + "/" + linodeId)
                .header("Authorization", "Bearer " + personalAccessToken).delete().build();
        try {
            Response response = client.newCall(request).execute();
            JSONObject json = new JSONObject(response.body().string());
            logger.info(json.toString());
            logger.info("deleted Linode instance " + linodeId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
