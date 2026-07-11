package com.wms.scheduler;

import com.wms.dao.OrderDAO;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * WebsiteOrderAutoCompleteScheduler — auto-advances Website orders from
 * DELIVERED to COMPLETED once 7 days have passed with no return request.
 *
 * <p>Scoped strictly to orders with {@code web_order_ref IS NOT NULL} (the
 * Website channel marker) — Lazada/Shopee/TikTok orders are untouched, they
 * have their own platform-driven lifecycle.
 *
 * <p>Context parameters (web.xml):
 * <ul>
 *   <li>{@code website.autocomplete.enabled} — "true" to enable (default: true)</li>
 *   <li>{@code website.autocomplete.interval.minutes} — run interval (default: 60)</li>
 * </ul>
 */
@WebListener
public class WebsiteOrderAutoCompleteScheduler implements ServletContextListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebsiteOrderAutoCompleteScheduler.class);

    public static final String CTX_ENABLED  = "website.autocomplete.enabled";
    public static final String CTX_INTERVAL = "website.autocomplete.interval.minutes";

    private Timer timer;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext ctx = sce.getServletContext();
        String enabledStr = ctx.getInitParameter(CTX_ENABLED);
        boolean enabled = "true".equalsIgnoreCase(enabledStr) || enabledStr == null; // default ON

        if (!enabled) {
            LOGGER.info("WebsiteOrderAutoCompleteScheduler: disabled (set {} to enable)", CTX_ENABLED);
            return;
        }

        int intervalMin = parseInt(ctx.getInitParameter(CTX_INTERVAL), 60);
        LOGGER.info("WebsiteOrderAutoCompleteScheduler: enabled, interval={} min", intervalMin);

        timer = new Timer("WebsiteOrderAutoCompleteTimer", true);
        timer.scheduleAtFixedRate(new AutoCompleteTask(), 90_000L, intervalMin * 60_000L);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (timer != null) {
            timer.cancel();
            LOGGER.info("WebsiteOrderAutoCompleteScheduler: timer cancelled");
        }
    }

    private static class AutoCompleteTask extends TimerTask {
        private final OrderDAO orderDAO = new OrderDAO();

        @Override
        public void run() {
            try {
                List<Integer> eligible = orderDAO.findWebsiteOrderIdsEligibleForAutoComplete();
                if (eligible.isEmpty()) {
                    LOGGER.debug("WebsiteOrderAutoCompleteScheduler: cycle completed, 0 eligible");
                    return;
                }
                int completed = 0;
                for (int orderId : eligible) {
                    if (orderDAO.markCompletedByOrderId(orderId)) completed++;
                }
                LOGGER.info("WebsiteOrderAutoCompleteScheduler: cycle completed, {}/{} orders auto-completed",
                        completed, eligible.size());
            } catch (Exception e) {
                LOGGER.error("WebsiteOrderAutoCompleteScheduler: run failed", e);
            }
        }
    }

    private static int parseInt(String s, int fallback) {
        if (s == null || s.trim().isEmpty()) return fallback;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return fallback; }
    }
}
