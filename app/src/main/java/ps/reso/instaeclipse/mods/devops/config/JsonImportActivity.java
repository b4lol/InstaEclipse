package ps.reso.instaeclipse.mods.devops.config;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.core.CommonUtils;

public class JsonImportActivity extends Activity {

    private static final int PICK_JSON_FILE = 1234;
    static final String ACTION_IMPORT_CONFIG = "ps.reso.instaeclipse.ACTION_IMPORT_CONFIG";
    static final String ACTION_RESTORE_SETTINGS = "ps.reso.instaeclipse.ACTION_RESTORE_SETTINGS";
    private static final List<String> ALLOWED_ACTIONS =
            Arrays.asList(ACTION_IMPORT_CONFIG, ACTION_RESTORE_SETTINGS);
    /** Config/backup files are small; refuse anything larger than this. */
    private static final int MAX_JSON_BYTES = 2 * 1024 * 1024;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.json_select_config)), PICK_JSON_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_JSON_FILE) {
            if (resultCode == RESULT_OK && data != null) {
                Uri uri = data.getData();
                try (InputStream inputStream = uri == null ? null : getContentResolver().openInputStream(uri)) {
                    if (inputStream == null) throw new java.io.IOException("No input");
                    String json = readStream(inputStream).trim();
                    if (isJsonObject(json)) {
                        // This activity is exported (Instagram launches it), so the extras are
                        // untrusted: only forward to a supported Instagram package, and only
                        // one of the two import actions.
                        String targetPackage = getIntent().getStringExtra("target_package");
                        String action = getIntent().getStringExtra("broadcast_action");
                        if (action == null || action.isEmpty()) action = ACTION_IMPORT_CONFIG;
                        if (targetPackage == null || !CommonUtils.SUPPORTED_PACKAGES.contains(targetPackage)
                                || !ALLOWED_ACTIONS.contains(action)) {
                            Toast.makeText(this, getString(R.string.json_target_not_specified), Toast.LENGTH_LONG).show();
                        } else {
                            Intent broadcast = new Intent(action);
                            broadcast.setPackage(targetPackage);
                            broadcast.putExtra("json_content", json);
                            sendBroadcast(broadcast);
                            Toast.makeText(this, getString(R.string.json_sent), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, getString(R.string.json_not_valid), Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, getString(R.string.json_read_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, getString(R.string.json_cancelled), Toast.LENGTH_SHORT).show();
            }
        }
        finish();
    }

    private static String readStream(InputStream inputStream) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = inputStream.read(buf)) != -1) {
            out.write(buf, 0, n);
            if (out.size() > MAX_JSON_BYTES) throw new java.io.IOException("File too large");
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static boolean isJsonObject(String json) {
        try {
            new JSONObject(json);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }
}
