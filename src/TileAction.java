import com.google.zxing.NotFoundException;
import org.json.JSONObject;
import yanwittmann.notification.BlurNotification;
import yanwittmann.types.File;
import yanwittmann.utils.FileUtils;
import yanwittmann.utils.GeneralUtils;
import yanwittmann.utils.Log;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class TileAction {
    private String actionType;
    private JSONObject action;

    public TileAction(JSONObject json) {
        actionType = json.getString("type");
        action = json.getJSONObject("action");
    }

    public TileAction(String actionType, String parameters) {
        this.actionType = actionType;
        setParametersFromString(parameters);
    }

    public TileAction() {
        actionType = "none";
        action = new JSONObject();
    }

    public void execute() {
        Log.info("Executing: " + actionType + " " + action);

        switch (actionType) {
            case "openFile":
                if (action.has("path")) {
                    String path = action.getString("path");
                    try {
                        FileUtils.openFile(new File(path), new File(path.replaceAll("(^.+)(?:/|\\\\)[^\\\\/]+", "$1")));
                    } catch (IOException e) {
                        try {
                            FileUtils.openFile(new File(path));
                        } catch (IOException e2) {
                            new BlurNotification("Unable to open file");
                            e2.printStackTrace();
                        }
                    }
                }
                break;
            case "openURL":
                if (action.has("url")) {
                    String path = action.getString("url");
                    try {
                        Desktop.getDesktop().browse(URI.create(path));
                    } catch (IOException e) {
                        new BlurNotification("Unable to open URL");
                        e.printStackTrace();
                    }
                }
                break;
            case "copyToClipboard":
                if (action.has("text")) {
                    copyString(action.getString("text").replaceAll(" ?EOL ?", "\n"));
                }
                break;
            case "scanForQR":
                scanForQR();
                break;
            case "settings":
                if (action.has("setting")) {
                    String setting = action.getString("setting");
                    if (setting.equals("settings")) {
                        try {
                            Main.setOpenMode(false);
                        } catch (IOException e) {
                            new BlurNotification("Unable to open settings");
                            e.printStackTrace();
                        }
                    }
                    if (setting.equals("exit")) System.exit(1);
                    if (setting.equals("lafolder")) {
                        try {
                            FileUtils.openFile(new File("."));
                        } catch (IOException e) {
                            new BlurNotification("Unable to folder");
                            e.printStackTrace();
                        }
                    }
                }
                break;
            default:
                new BlurNotification("Invalid action: " + actionType);
        }
    }

    private final static File TMP_QR_CODE_FILE = new File("tmp.png");
    private final static String URL_REGEX = "https?://w{0,3}\\w*?\\.(\\w*?\\.)?\\w{2,3}\\S*|www\\.(\\w*?\\.)?\\w*?\\.\\w{2,3}\\S*|(\\w*?\\.)?\\w*?\\.\\w{2,3}[/?]\\S";

    private void scanForQR() {
        BufferedImage screenshot = LaunchBar.getScreenshotImage();
        writeBufferedImage(TMP_QR_CODE_FILE, screenshot);
        try {
            String result = QRCode.readQRCode(TMP_QR_CODE_FILE.getPath());
            Log.info("QR code text: " + result);

            if (result.matches(URL_REGEX)) {
                Desktop desktop = Desktop.getDesktop();
                try {
                    desktop.browse(new URI(result));
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            } else if (result.startsWith("MATMSG:")) {
                result = result.replace("MATMSG:", "");
                String to = result.replaceAll(".*TO:([^;]+);.*", "$1");
                String sub = result.replaceAll(".*SUB:([^;]+);.*", "$1");
                String body = result.replaceAll(".*BODY:([^;]+);.*", "$1");
                GeneralUtils.mailto("mailto:" + to + "?subject=" + sub + "&body=" + body);
            } else {
                copyString(result);
                new BlurNotification("Copied QR code text to clipboard");
            }

            if (!TMP_QR_CODE_FILE.delete())
                Log.warning("Unable to delete temp image file: " + TMP_QR_CODE_FILE.getPath());
        } catch (IOException | NotFoundException e) {
            new BlurNotification("No QR code detected");
        }
    }

    public static void writeBufferedImage(File file, BufferedImage bufferedImage) {
        try {
            ImageIO.write(bufferedImage, "png", file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getActionType() {
        return actionType;
    }

    public String getParametersAsString() {
        switch (actionType) {
            case "openFile":
                if (action.has("path")) {
                    return "path=" + action.getString("path");
                }
                break;
            case "openURL":
                if (action.has("url")) {
                    return "url=" + action.getString("url");
                }
                break;
            case "copyToClipboard":
                if (action.has("text")) {
                    return "text=" + action.getString("text");
                }
            case "settings":
                if (action.has("setting")) {
                    return "setting=" + action.getString("setting");
                }
        }
        return "";
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public void setParametersFromString(String parameters) {
        action = new JSONObject();
        for (String s : parameters.split(" ?&{3} ?")) {
            String[] p = s.split("=", 2);
            if (p.length == 2)
                action.put(p[0], p[1]);
            else if (p.length == 1)
                action.put(p[0], "");
            else if (p.length == 0)
                action.put("", "");
        }
    }

    public JSONObject generateJSON() {
        JSONObject object = new JSONObject();
        object.put("type", actionType);
        object.put("action", action);
        return object;
    }

    public static void copyString(String text) {
        StringSelection selection = new StringSelection(text);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, selection);
    }

    @Override
    public String toString() {
        return actionType + " " + action;
    }
}
