package org.jitsi.jibri;


import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.Random;
import java.lang.Runnable;
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
    private ScheduledExecutorService rebootScheduler = Executors.newScheduledThreadPool(1);
    private final int WAIT_TO_CLONE_TIME = 10;
    private final int REBOOT_REQUEST_INTERVAL = 5;

    private void rebootLinodeInstance(int id, String personalAccessToken){
	Runnable r = new Runnable() {
			private boolean isBooted = false;
			@Override
			public void run() {
				if(isBooted)
					return;
				OkHttpClient client = new OkHttpClient();
				MediaType JSON = MediaType.parse("application/json; charset=utf-8");
				RequestBody body = RequestBody.create(null, new byte[0]);
				String rebootUrl = LINODE_INSTANCES_ENDPOINT + "/" + id + "/reboot";
				Request request = new Request.Builder().url(rebootUrl)
					.header("Authorization", "Bearer " + personalAccessToken).post(body).build();
				try {
					Response response = client.newCall(request).execute();
					JSONObject json = new JSONObject(response.body().string());
					boolean isThereErrors = json.has("errors");
					if(isThereErrors)
						logger.info("reboot result of new Linode instance with id: " + id + " ---> " + json.getString("errors"));
					else{
						logger.info("reboot result of new Linode instance with id: " + id + " ---> " + json.toString());
						logger.info("seems new Linode instance is rebooted successfully, stopped sending new reboot requests.");
						isBooted = true;
					}
				} catch (Exception e) {
				    e.printStackTrace();
				}
			}
		};
	rebootScheduler.scheduleAtFixedRate(r, WAIT_TO_CLONE_TIME, REBOOT_REQUEST_INTERVAL, TimeUnit.SECONDS);
    }

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

    // TODO remove it, unused
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

    // TODO remove it, unused
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
	int currentLinodeId = findCurrentLinodeId(personalAccessToken);
        OkHttpClient client = new OkHttpClient();
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        String payload = "{\"type\":\"g6-standard-4\",\"region\":\"us-east\",\"backups_enabled\":false}";
	String cloneUrl = LINODE_INSTANCES_ENDPOINT + "/" + currentLinodeId + "/clone";
        RequestBody body = RequestBody.create(JSON, payload);
        Request request = new Request.Builder().url(cloneUrl)
                .header("Authorization", "Bearer " + personalAccessToken).post(body).build();
        try {
            Response response = client.newCall(request).execute();
            JSONObject json = new JSONObject(response.body().string());
            logger.info(json.toString());
            int newLinodeId = json.getInt("id");
            logger.info("created new Linode instance with id: " + newLinodeId);
	    rebootLinodeInstance(newLinodeId, personalAccessToken);
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
