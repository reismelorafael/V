package com.vectras.qemu.utils;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

import com.vectras.qemu.Config;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

public class QmpClient {

	private static final String TAG = "QmpClient";
	private static String requestCommandMode = "{ \"execute\": \"qmp_capabilities\" }";
	private static final int MAX_RESPONSE_LINES = 128;
	private static final int SOCKET_CONNECT_TIMEOUT_MS = 5000;
	private static final int SOCKET_READ_TIMEOUT_MS = 5000;
	private static final int DEFAULT_RETRIES = 10;
	private static final int DEFAULT_RETRY_DELAY_MS = 1000;
	private static final int STOP_RETRIES = 1;
	private static final int STOP_RETRY_DELAY_MS = 150;
	public static boolean allow_external = false;

	public synchronized static String sendCommand(String command) {
		return sendCommand(command, DEFAULT_RETRIES, DEFAULT_RETRY_DELAY_MS);
	}

	public synchronized static String sendCommandForStopPath(String command) {
		return sendCommand(command, STOP_RETRIES, STOP_RETRY_DELAY_MS);
	}

	public synchronized static String sendCommand(String command, int maxRetries, int retryDelayMs) {
		String response = null;
		int trial=0;
		Socket pingSocket = null;
		LocalSocket localSocket = null;
		PrintWriter out = null;
		BufferedReader in = null;

		try {
		    if(allow_external) {
                pingSocket = new Socket();
                pingSocket.connect(new InetSocketAddress(Config.QMPServer, Config.QMPPort), SOCKET_CONNECT_TIMEOUT_MS);
                pingSocket.setSoTimeout(SOCKET_READ_TIMEOUT_MS);
                out = new PrintWriter(pingSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(pingSocket.getInputStream()));
		    } else {
		        localSocket = new LocalSocket();
		        String localQMPSocketPath = Config.getLocalQMPSocketPath();
                LocalSocketAddress localSocketAddr = new LocalSocketAddress(localQMPSocketPath, LocalSocketAddress.Namespace.FILESYSTEM);
                localSocket.connect(localSocketAddr);
                localSocket.setSoTimeout(SOCKET_READ_TIMEOUT_MS);
                out = new PrintWriter(localSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(localSocket.getInputStream()));
            }


			response = negotiateCapabilities(out, in, maxRetries, retryDelayMs);
			if (!isGreetingAndCapabilitiesContractSatisfied(response)) {
				Log.w(TAG, "QMP greeting/capabilities contract not satisfied. Raw response=" + response);
			}

			sendRequest(out, command);
			trial=0;
			while (trial < maxRetries) {
				response = getResponse(in);
				if (response != null && !response.isEmpty()) {
					break;
				}
				Thread.sleep(retryDelayMs);
				trial++;
			}
		} catch (java.net.ConnectException e) {
			Log.w(TAG, "Could not connect to QMP", e);
			if(Config.debugQmp)
			    e.printStackTrace();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			Log.e(TAG, "Interrupted while waiting for QMP response", e);
		} catch (IOException e) {
			Log.e(TAG, "I/O error while connecting to QMP", e);
			if(Config.debugQmp)
				e.printStackTrace();
		} catch(Exception e) {
            Log.e(TAG, "Error while connecting to QMP", e);
            if(Config.debugQmp)
				e.printStackTrace();
		} finally {
			if (out != null)
				out.close();
			try {
				if (in != null)
					in.close();
				if (pingSocket != null)
					pingSocket.close();
				if (localSocket != null)
					localSocket.close();
			} catch (IOException e) {
				Log.e(TAG, "Error closing QMP connection", e);
			}

		}

		return response;
	}

	static String negotiateCapabilities(PrintWriter out, BufferedReader in, int maxRetries, int retryDelayMs) throws Exception {
		sendRequest(out, QmpClient.requestCommandMode);
		String response = null;
		for (int trial = 0; trial < maxRetries; trial++) {
			response = getResponse(in);
			if (response != null && !response.isEmpty()) {
				break;
			}
			Thread.sleep(retryDelayMs);
		}
		return response;
	}

	static boolean isGreetingAndCapabilitiesContractSatisfied(String response) {
		if (response == null || response.trim().isEmpty()) {
			return false;
		}

		boolean hasGreeting = false;
		boolean hasCapabilitiesAck = false;
		String[] lines = response.split("\\n");
		for (String line : lines) {
			if (line == null || line.trim().isEmpty()) {
				continue;
			}
			try {
				JSONObject object = new JSONObject(line);
				if (object.has("QMP") && !object.isNull("QMP")) {
					hasGreeting = true;
				}
				if (object.has("return") && !object.isNull("return")) {
					hasCapabilitiesAck = true;
				}
			} catch (Exception ignored) {
				return false;
			}
		}

		return hasGreeting && hasCapabilitiesAck;
	}

	private static void sendRequest(PrintWriter out, String request) {

	    if(Config.debugQmp)
		    Log.i(TAG, "QMP request" + request);
		out.println(request);
	}

    private static String getResponse(BufferedReader in) throws Exception {

        String line;
        StringBuilder stringBuilder = new StringBuilder("");

        try {
            for (int linesRead = 0; linesRead < MAX_RESPONSE_LINES; linesRead++) {
                line = in.readLine();
                if (line != null) {
                    if(Config.debugQmp)
                        Log.i(TAG, "QMP response: " + line);
                    JSONObject object = new JSONObject(line);
                    boolean hasReturn = object.has("return") && !object.isNull("return");
                    boolean hasError = object.has("error") && !object.isNull("error");

                    if (hasReturn) {
						stringBuilder.append(line);
						stringBuilder.append("\n");
                        break;
                    }

                    stringBuilder.append(line);
                    stringBuilder.append("\n");

                    if (hasError) {
                        break;
                    }


                } else
                    break;
            }
        } catch (Exception ex) {
            Log.e(TAG, "Could not get Response: " + ex.getMessage());
            if(Config.debugQmp)
                ex.printStackTrace();
        }
        return stringBuilder.toString();
    }

	private static String getQueryMigrateResponse(BufferedReader in) throws Exception {

		String line;
		StringBuilder stringBuilder = new StringBuilder("");

		try {
			for (int linesRead = 0; linesRead < MAX_RESPONSE_LINES; linesRead++) {
				line = in.readLine();
				if (line != null) {
				    if(Config.debugQmp)
					    Log.i(TAG, "QMP query-migrate response: " + line);
					JSONObject object = new JSONObject(line);
					boolean hasReturn = object.has("return") && !object.isNull("return");
					boolean hasError = object.has("error") && !object.isNull("error");

					if (hasReturn) {
						break;
					}

					stringBuilder.append(line);
					stringBuilder.append("\n");

					if (hasError) {
						break;
					}


				} else
					break;
			}
		} catch (Exception ex) {
			Log.e(TAG, "Could not get query-migrate response: " + ex.getMessage());
			if (Config.debugQmp)
				ex.printStackTrace();
		}
		return stringBuilder.toString();
	}

	public static String migrate(boolean block, boolean inc, String uri) {
		
		// XXX: Detach should not be used via QMP according to docs
		// return "{\"execute\":\"migrate\",\"arguments\":{\"detach\":" + detach
		// + ",\"blk\":" + block + ",\"inc\":" + inc
		// + ",\"uri\":\"" + uri + "\"},\"id\":\"vectras\"}";

		// its better not to use block (full disk copy) cause its slow (though
		// safer)
		// see qmp-commands.hx for more info
		return "{\"execute\":\"migrate\",\"arguments\":{\"blk\":" + block + ",\"inc\":" + inc + ",\"uri\":\"" + uri
				+ "\"},\"id\":\"vectras\"}";

	}

    public static String changevncpasswd(String passwd) {

		return "{\"execute\": \"change\", \"arguments\": { \"device\": \"vnc\", \"target\": \"password\", \"arg\": \"" + passwd +"\" } }";

    }

    public static String ejectdev(String dev) {

        return "{ \"execute\": \"eject\", \"arguments\": { \"device\": \""+ dev +"\" } }";

    }

    public static String changedev(String dev, String value) {

        return "{ \"execute\": \"change\", \"arguments\": { \"device\": \""+dev+"\", \"target\": \"" + value + "\" } }";

    }



    public static String query_migrate() {
		return "{ \"execute\": \"query-migrate\" }";

	}

	public static String save_snapshot(String snapshot_name) {
		return "{\"execute\": \"snapshot-create\", \"arguments\": {\"name\": \""+ snapshot_name+"\"} }";

	}

	public static String query_snapshot() {
		return "{ \"execute\": \"query-snapshot-status\" }";

	}

	public static String stop() {
		return "{ \"execute\": \"stop\" }";

	}

	public static String cont() {
		return "{ \"execute\": \"cont\" }";

	}

	public static String powerDown() {
		return "{ \"execute\": \"system_powerdown\" }";

	}

	public static String reset() {
		return "{ \"execute\": \"system_reset\" }";

	}

	public static String getState() {
		return "{ \"execute\": \"query-status\" }";

	}
}
