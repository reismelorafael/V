package com.vectras.vm.view;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.vectras.vm.R;
import com.vectras.vm.network.EndpointValidator;
import com.vectras.vm.network.NetworkEndpoints;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GithubUserView extends LinearLayout {
    private LinearLayout mainView;
    private ImageView profileImage;
    private TextView userName;
    private TextView userDescription;
    private String thisUserNameGitHub = "";
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public GithubUserView(Context context) {
        super(context);
        init(context);
    }

    public GithubUserView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public GithubUserView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.github_user_view, this, true);

        mainView = findViewById(R.id.main);
        profileImage = findViewById(R.id.profile_image);
        userName = findViewById(R.id.user_name);
        userDescription = findViewById(R.id.user_description);
        ImageButton githubProfile = findViewById(R.id.githubProfile);

        mainView.setOnClickListener(v -> openGithubProfile(context));
        githubProfile.setOnClickListener(v -> openGithubProfile(context));
    }

    public void setUsername(String username) {
        profileImage.setImageResource(R.drawable.account_circle_24px);
        userName.setText(getContext().getString(R.string.unknow));
        userDescription.setText(getContext().getString(R.string.unknow));

        io.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(NetworkEndpoints.githubUserApi(username));
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(25000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");

                if (connection.getResponseCode() != 200) {
                    return;
                }

                String response = readUtf8(connection.getInputStream());
                JSONObject json = new JSONObject(response);
                String login = json.optString("login", "");
                String bio = json.optString("bio", "");
                String avatar = json.optString("avatar_url", "");

                Bitmap avatarBitmap = null;
                if (EndpointValidator.isValidHttpUrl(avatar)) {
                    avatarBitmap = downloadBitmap(avatar);
                }

                Bitmap finalAvatarBitmap = avatarBitmap;
                post(() -> {
                    thisUserNameGitHub = login;
                    userName.setText(login.isEmpty() ? getContext().getString(R.string.unknow) : login);
                    userDescription.setText(bio.isEmpty() ? getContext().getString(R.string.unknow) : bio);
                    if (finalAvatarBitmap != null) {
                        profileImage.setImageBitmap(finalAvatarBitmap);
                    } else {
                        profileImage.setImageResource(R.drawable.account_circle_24px);
                    }
                });
            } catch (Exception ignored) {
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private String readUtf8(InputStream in) throws Exception {
        byte[] buffer = new byte[4096];
        StringBuilder out = new StringBuilder();
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.append(new String(buffer, 0, read));
        }
        return out.toString();
    }

    private Bitmap downloadBitmap(String imageUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(imageUrl).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(25000);
            connection.setDoInput(true);
            try (InputStream in = connection.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void openGithubProfile(Context context) {
        if (thisUserNameGitHub.isEmpty()) {
            return;
        }
        if (context instanceof Activity activity && (activity.isFinishing() || activity.isDestroyed())) {
            return;
        }
        String url = NetworkEndpoints.githubProfile(thisUserNameGitHub);
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }
}
