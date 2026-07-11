package com.wms.controller.sales;

import com.wms.controller.BaseController;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * GET /return-evidence/{filename} — streams back customer-uploaded return
 * evidence (photos/video) saved by WebsiteOrderActionApiServlet, so Sales
 * staff can view them in the RMA review screen. Session-authenticated like
 * the rest of /sales/* (no AuthFilter carve-out needed) — same pattern as
 * PublishImageServlet's GET handler.
 */
@WebServlet(urlPatterns = {"/return-evidence/*"})
public class ReturnEvidenceServlet extends BaseController {

    private static final Path EVIDENCE_ROOT =
            Paths.get(System.getProperty("user.home"), "wms-uploads", "return-evidence");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.contains("..")) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String name = pathInfo.substring(1);
        Path file = EVIDENCE_ROOT.resolve(name);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String lower = name.toLowerCase();
        String ct = lower.endsWith(".mp4") ? "video/mp4"
                : lower.endsWith(".png") ? "image/png"
                : lower.endsWith(".webp") ? "image/webp"
                : "image/jpeg";
        resp.setContentType(ct);
        resp.setHeader("Cache-Control", "private, max-age=86400");
        Files.copy(file, resp.getOutputStream());
    }
}
