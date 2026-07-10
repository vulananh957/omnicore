package com.wms.controller.sales;

import com.wms.controller.BaseController;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PublishImageServlet — Two-endpoint handler for the wizard's image flow:
 *
 * <ol>
 *   <li>{@code POST /sales/publish-image?action=upload} — accepts a multipart
 *       file and saves it under {@code ~/wms-uploads/publish-images/}. Returns
 *       a JSON body with the public URL the wizard must hand off to
 *       {@code /sales/channel-products} (e.g. {@code /publish-images/abc.jpg}).</li>
 *   <li>{@code GET /publish-images/{filename}} — streams the saved file back.
 *       We can't use Tomcat's default servlet to serve a folder outside
 *       {@code webapp/}, so this GET handler doubles as a static file server.</li>
 * </ol>
 *
 * <p>Why a custom upload folder? Lazada's {@code /image/migrate} endpoint
 * refuses {@code data:image/...;base64,…} payloads with
 * {@code E302: Not supported URL}, so the wizard must hand off a real HTTPS
 * URL. The publish-images folder is regenerated on each app restart and
 * never backed up — old uploads are safe to prune at any time.</p>
 */
@WebServlet(urlPatterns = {"/sales/publish-image", "/publish-images/*"})
@MultipartConfig(
        fileSizeThreshold = 1 * 1024 * 1024,
        maxFileSize = 10 * 1024 * 1024,
        maxRequestSize = 50 * 1024 * 1024
)
public class PublishImageServlet extends BaseController {

    private static final Logger LOGGER = Logger.getLogger(PublishImageServlet.class.getName());

    /** Root for transient uploads — outside the webapp so they survive redeploy. */
    private static final Path UPLOAD_ROOT =
            Paths.get(System.getProperty("user.home"), "wms-uploads", "publish-images");

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String action = req.getParameter("action");
        if (!"upload".equals(action)) {
            writeJson(resp, "{\"success\":false,\"message\":\"Unknown action\"}");
            return;
        }
        try {
            Part file = req.getPart("file");
            if (file == null || file.getSize() == 0) {
                writeJson(resp, "{\"success\":false,\"message\":\"No file uploaded\"}");
                return;
            }
            String original = file.getSubmittedFileName();
            String ext = "";
            if (original != null && original.contains(".")) {
                ext = original.substring(original.lastIndexOf('.')).toLowerCase();
            }
            if (ext.isBlank() || (!ext.equals(".jpg") && !ext.equals(".jpeg")
                    && !ext.equals(".png") && !ext.equals(".webp"))) {
                ext = ".jpg";
            }
            String name = UUID.randomUUID().toString().replace("-", "") + ext;
            Files.createDirectories(UPLOAD_ROOT);
            Path target = UPLOAD_ROOT.resolve(name);
            try (var in = file.getInputStream()) {
                Files.copy(in, target);
            }
            String publicUrl = req.getContextPath() + "/publish-images/" + name;
            LOGGER.info("PublishImageServlet: saved " + target + " size=" + Files.size(target));
            writeJson(resp, "{\"success\":true,\"url\":\"" + publicUrl + "\"}");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "PublishImageServlet: upload failed", e);
            writeJson(resp, "{\"success\":false,\"message\":\""
                    + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // /publish-images/{name}
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.contains("..")) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String name = pathInfo.substring(1);
        Path file = UPLOAD_ROOT.resolve(name);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        // Content type
        String ct = "image/jpeg";
        if (name.toLowerCase().endsWith(".png")) ct = "image/png";
        else if (name.toLowerCase().endsWith(".webp")) ct = "image/webp";
        resp.setContentType(ct);
        resp.setHeader("Cache-Control", "public, max-age=86400");
        Files.copy(file, resp.getOutputStream());
    }
}
