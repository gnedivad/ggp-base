package apps.tiltyard;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import external.JSON.JSONException;
import external.JSON.JSONObject;

import util.configuration.RemoteResourceLoader;
import util.crypto.SignableJSON;
import util.crypto.BaseCryptography.EncodedKeyPair;
import util.files.FileUtils;
import util.http.HttpReader;
import util.http.HttpRequest;
import util.http.HttpWriter;

/**
 * The Tiltyard Request Farm is a multi-threaded web server that opens network
 * connections, makes requests, and reports back responses on behalf of a remote
 * client. It serves as a backend for intermediary systems that, due to various
 * restrictions, cannot make long-lived HTTP connections themselves.
 *
 * This is the backend for the continuously-running online GGP.org Tiltyard,
 * which schedules matches between players around the world and aggregates stats
 * based on the outcome of those matches.
 * 
 * SAMPLE INVOCATION (when running locally):
 *
 * ResourceLoader.load_raw('http://127.0.0.1:9124/' + escape(JSON.stringify({
 * "targetPort":9147,"targetHost":"0.player.ggp.org","timeoutClock":30000,
 * "forPlayerName":"Webplayer-0","callbackURL":"http://tiltyard.ggp.org/farm/",
 * "requestContent":"( play foo bar baz )"})))
 * 
 * Tiltyard Request Farm will open up a network connection to the target, send
 * the request string, and wait for the response. Once the response arrives, it
 * will close the connection and call the callback, sending the response to the
 * remote client that issued the original request.
 * 
 * You shouldn't be running this server unless you are bringing up an instance of the
 * online GGP.org Tiltyard or an equivalent service.
 * 
 * @author Sam Schreiber
 */
public final class TiltyardRequestFarm
{
    public static final int SERVER_PORT = 9125;   
    private static final String registrationURL = "http://tiltyard.ggp.org/backends/register/farm";
    
    private static Integer ongoingRequests = new Integer(0);
    private static Integer failedPosts = new Integer(0);
    
    public static boolean testMode = false;
    
    static EncodedKeyPair getKeyPair(String keyPairString) {
    	if (keyPairString == null)
    		return null;
        try {
            return new EncodedKeyPair(keyPairString);
        } catch (JSONException e) {
            return null;
        }
    }
    public static final EncodedKeyPair theTiltyardKeys = getKeyPair(FileUtils.readFileAsString(new File("src/apps/tiltyard/TiltyardKeys.json")));
    public static String generateSignedPing() {
        JSONObject thePing = new JSONObject();
        try {
            thePing.put("lastTimeBlock", (System.currentTimeMillis() / 3600000));
            thePing.put("nextTimeBlock", (System.currentTimeMillis() / 3600000)+1);
            SignableJSON.signJSON(thePing, theTiltyardKeys.thePublicKey, theTiltyardKeys.thePrivateKey);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return thePing.toString();
    }
    
    // Connections are run asynchronously in their own threads.
    static class RunRequestThread extends Thread {
    	String targetHost, requestContent, forPlayerName, callbackURL, originalRequest;    	
    	int targetPort, timeoutClock;
    	Set<String> activeRequests;

        public RunRequestThread(Socket connection, Set<String> activeRequests) throws IOException, JSONException {
            String line = HttpReader.readAsServer(connection);
            System.out.println("On " + new Date() + ", client has requested: " + line);
            
            String response = null;
            if (line.equals("ping")) {
                response = generateSignedPing();
            } else {
                synchronized (activeRequests) {
                	if (activeRequests.contains(line)) {
                		connection.close();
                	} else {
                		activeRequests.add(line);
                	}
                	this.activeRequests = activeRequests;
                }
                
                JSONObject theJSON = new JSONObject(line);
                targetPort = theJSON.getInt("targetPort");
                targetHost = theJSON.getString("targetHost");
                timeoutClock = theJSON.getInt("timeoutClock");
                callbackURL = theJSON.getString("callbackURL");
                forPlayerName = theJSON.getString("forPlayerName");
                requestContent = theJSON.getString("requestContent");
                originalRequest = line;
                response = "okay";
            }

            HttpWriter.writeAsServer(connection, response);
            connection.close();
        }
        
        @Override
        public void run() {
            if (originalRequest != null) {                
                synchronized (ongoingRequests) {
                	ongoingRequests++;
                }                
                System.out.println("On " + new Date() + ", starting request. There are now " + ongoingRequests + " ongoing requests.");
                JSONObject responseJSON = new JSONObject();
                try {
                	responseJSON.put("originalRequest", originalRequest);
	                try {
	                	String response = HttpRequest.issueRequest(targetHost, targetPort, forPlayerName, requestContent, timeoutClock);
	                	responseJSON.put("response", response);
	                	responseJSON.put("responseType", "OK");	                	
	                } catch (SocketTimeoutException te) {
	                	responseJSON.put("responseType", "TO");
	                } catch (IOException ie) {
	                	responseJSON.put("responseType", "CE");
	                }
	                if (!testMode) {
	                	SignableJSON.signJSON(responseJSON, theTiltyardKeys.thePublicKey, theTiltyardKeys.thePrivateKey);
	                }
                } catch (JSONException je) {
                	throw new RuntimeException(je);
                }
                boolean successfulPost = false;
                for (int retries = 0; retries < 10; retries++) {
                	try {
                		RemoteResourceLoader.postRawWithTimeout(callbackURL, responseJSON.toString(), Integer.MAX_VALUE);
                		successfulPost = true;
                		break;
                	} catch (IOException ie) {
                		try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							break;
						}
                	}
                }
                if (!successfulPost) {
                	System.err.println("Failed to post response.");
                	failedPosts++;
                }
                synchronized (ongoingRequests) {
                	ongoingRequests--;                	
                	if (ongoingRequests == 0) {
                		System.gc();
                		System.out.println("On " + new Date() + ", completed request. Garbage collecting since there are no ongoing requests.");
                	} else {
                		System.out.println("On " + new Date() + ", completed request. There are now " + ongoingRequests + " ongoing requests.");
                	}
                	if (failedPosts > 0) {
                		System.out.println("So far there have been " + failedPosts + " failed POST attempts overall.");
                	}
                }
                synchronized (activeRequests) {
                	activeRequests.remove(originalRequest);
                }                
            }
        }
    }
    
    static class TiltyardRegistration extends Thread {
        @Override
        public void run() {
            // Send a registration ping to Tiltyard every five minutes.
            while (true) {
                try {
                    RemoteResourceLoader.postRawWithTimeout(registrationURL, generateSignedPing(), 2500);                    
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    Thread.sleep(5 * 60 * 1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
       }
    }
    
    public static void main(String[] args) {        
        ServerSocket listener = null;
        try {
             listener = new ServerSocket(SERVER_PORT);
        } catch (IOException e) {
            System.err.println("Could not open server on port " + SERVER_PORT + ": " + e);
            e.printStackTrace();
            return;
        }
        if (!testMode) {
	        if (theTiltyardKeys == null) {
	            System.err.println("Could not load cryptographic keys for signing Tiltyard request responses.");
	            return;
	        }	
	        new TiltyardRegistration().start();
        }
        
        Set<String> activeRequests = new HashSet<String>();
        while (true) {
            try {
                Socket connection = listener.accept();
                RunRequestThread handlerThread = new RunRequestThread(connection, activeRequests);
                handlerThread.start();
            } catch (Exception e) {
                System.err.println(e);
            }
        }        
    }
}