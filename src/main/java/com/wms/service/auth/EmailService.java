package com.wms.service.auth;

import com.wms.model.User;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * EmailService — Sends transactional emails via SMTP (Jakarta Mail / Angus).
 */
public class EmailService {

    private static final Logger LOGGER = Logger.getLogger(EmailService.class.getName());
    private static final DateTimeFormatter VIET_TIME = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final String MAIL_PROPERTIES = "/mail.properties";

    private Session mailSession;
    private String fromAddress;
    private String fromName;
    private Properties mailConfig;

    public boolean sendNewUserCredentials(User user, String tempPassword) {
        if (user == null || isBlank(user.getEmail())) {
            LOGGER.warning("EmailService: Missing recipient email for new user notification.");
            return false;
        }

        String subject = "[OmniCore WMS] Thong tin tai khoan moi";
        String html = buildNewUserHtml(user, tempPassword);
        try {
            sendHtml(user.getEmail(), subject, html);
            LOGGER.info("EmailService: New user credentials email sent to " + user.getEmail()
                    + " | Username=" + user.getUsername()
                    + " | TempPassword=" + tempPassword
                    + " | Role=" + user.getRole());
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "EmailService: Failed to send new user credentials.", e);
            return false;
        }
    }

    public boolean sendOtpCode(User user, String otpCode, long expiresAtMillis) {
        if (user == null || isBlank(user.getEmail())) {
            LOGGER.warning("EmailService: Missing recipient email for OTP.");
            return false;
        }

        String subject = "[OmniCore WMS] Ma OTP dang nhap";
        String html = buildOtpHtml(user, otpCode, expiresAtMillis);
        LOGGER.info("EmailService: OTP email sent to " + user.getEmail() + " | OTP=" + otpCode);
        try {
            sendHtml(user.getEmail(), subject, html);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "EmailService: Failed to send OTP email to " + user.getEmail() + " | OTP=" + otpCode, e);
            return false;
        }
    }

    private void sendHtml(String to, String subject, String htmlBody) throws MessagingException {
        Session session = getMailSession();
        MimeMessage message = new MimeMessage(session);

        InternetAddress from;
        try {
            if (!isBlank(fromName)) {
                from = new InternetAddress(fromAddress, fromName, StandardCharsets.UTF_8.name());
            } else {
                from = new InternetAddress(fromAddress);
            }
        } catch (Exception e) {
            throw new MessagingException("Invalid sender address", e);
        }

        message.setFrom(from);
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject, StandardCharsets.UTF_8.name());
        message.setContent(htmlBody, "text/html; charset=UTF-8");

        Transport.send(message);
    }

    private Session getMailSession() {
        if (mailSession != null) {
            return mailSession;
        }

        loadMailConfig();
        String host = getConfigOrDefault("SMTP_HOST", "smtp.gmail.com");
        String port = getConfigOrDefault("SMTP_PORT", "587");
        String username = getRequiredConfig("SMTP_USERNAME");
        String password = getRequiredConfig("SMTP_PASSWORD");

        boolean useTls = "true".equalsIgnoreCase(getConfigOrDefault("SMTP_USE_TLS", "true"));
        boolean useSsl = "true".equalsIgnoreCase(getConfigOrDefault("SMTP_USE_SSL", "false"));

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        props.put("mail.smtp.ssl.trust", host);
        if (useTls) {
            props.put("mail.smtp.starttls.enable", "true");
        }
        if (useSsl) {
            props.put("mail.smtp.ssl.enable", "true");
        }

        String debug = getConfigOrDefault("SMTP_DEBUG", "false");
        if ("true".equalsIgnoreCase(debug)) {
            props.put("mail.debug", "true");
        }

        fromAddress = getConfigOrDefault("SMTP_FROM", username);
        fromName = getConfigOrDefault("SMTP_FROM_NAME", "OmniCore WMS Hub");

        mailSession = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
        return mailSession;
    }

    private void loadMailConfig() {
        if (mailConfig != null) {
            return;
        }

        mailConfig = new Properties();
        try (InputStream in = EmailService.class.getResourceAsStream(MAIL_PROPERTIES)) {
            if (in != null) {
                mailConfig.load(in);
                LOGGER.info("EmailService: Loaded SMTP settings from " + MAIL_PROPERTIES);
            } else {
                LOGGER.warning("EmailService: mail.properties not found on classpath; using env/system properties only.");
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "EmailService: Failed to load mail.properties.", e);
        }
    }

    private String buildNewUserHtml(User user, String tempPassword) {
        String fullName = escapeHtml(nullToPlaceholder(user.getFullName(), "Bạn"));
        String username = escapeHtml(nullToPlaceholder(user.getUsername(), "N/A"));
        String email = escapeHtml(nullToPlaceholder(user.getEmail(), "N/A"));
        String phone = escapeHtml(nullToPlaceholder(user.getPhone(), "Chưa cập nhật"));
        String password = escapeHtml(nullToPlaceholder(tempPassword, "N/A"));

        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html>")
          .append("<html lang='vi'>")
          .append("<head>")
          .append("<meta charset='UTF-8'/>")
          .append("<meta name='viewport' content='width=device-width, initial-scale=1.0'/>")
          .append("<title>OmniCore WMS Hub</title>")
          .append("</head>")
          .append("<body style='margin:0;padding:0;background:#F0F4FA;font-family:Inter,Arial,sans-serif;'>")
          .append("<table role='presentation' width='100%' cellpadding='0' cellspacing='0' style='background:#F0F4FA;'>")
          .append("<tr><td align='center' style='padding:40px 16px;'>")
          .append("<table role='presentation' width='560' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 8px 32px rgba(16,55,92,0.10);'>")
          .append("<tr><td style='background:#10375C;padding:24px 32px;color:#ffffff;'>")
          .append("<div style='display:table;width:100%;'>")
          .append("<div style='display:table-cell;vertical-align:middle;'>")
          .append("<div style='font-size:20px;font-weight:800;letter-spacing:-0.02em;'>OmniCore WMS Hub</div>")
          .append("<div style='font-size:12px;opacity:0.80;margin-top:6px;'>Thông tin tài khoản nhân viên mới</div>")
          .append("</div>")
          .append("</div>")
          .append("</td></tr>")
          .append("<tr><td style='padding:28px 32px;color:#1f2937;'>")
          .append("<h2 style='margin:0 0 8px;font-size:20px;font-weight:700;color:#10375C;'>Chào ").append(fullName).append("!</h2>")
          .append("<p style='margin:0 0 20px;font-size:14px;line-height:1.6;color:#4b5563;'>")
          .append("Tài khoản của bạn đã được tạo thành công trên hệ thống OmniCore WMS Hub. Dưới đây là thông tin đăng nhập tạm thời:")
          .append("</p>")
          .append("<table role='presentation' width='100%' cellpadding='0' cellspacing='0' style='border-collapse:collapse;font-size:14px;border:1px solid #E5EAF3;border-radius:12px;overflow:hidden;'>")
          .append("<tr style='background:#F0F4FA;'><td style='padding:12px 16px;color:#10375C;font-weight:600;width:140px;border-bottom:1px solid #E5EAF3;'>Tên đăng nhập</td><td style='padding:12px 16px;color:#111827;font-weight:600;border-bottom:1px solid #E5EAF3;'>").append(username).append("</td></tr>")
          .append("<tr><td style='padding:12px 16px;color:#10375C;font-weight:600;width:140px;border-bottom:1px solid #E5EAF3;'>Email</td><td style='padding:12px 16px;color:#111827;border-bottom:1px solid #E5EAF3;'>").append(email).append("</td></tr>")
          .append("<tr style='background:#F0F4FA;'><td style='padding:12px 16px;color:#10375C;font-weight:600;width:140px;border-bottom:1px solid #E5EAF3;'>Số điện thoại</td><td style='padding:12px 16px;color:#111827;border-bottom:1px solid #E5EAF3;'>").append(phone).append("</td></tr>")
          .append("<tr><td style='padding:12px 16px;color:#10375C;font-weight:600;width:140px;' colspan='2'>Mật khẩu tạm thời</td></tr>")
          .append("<tr><td style='padding:4px 16px 16px 16px;' colspan='2'><div style='display:inline-block;padding:12px 20px;background:#10375C;color:#ffffff;font-size:18px;letter-spacing:2px;font-weight:700;border-radius:8px;'>").append(password).append("</div></td></tr>")
          .append("</table>")
          .append("<div style='margin-top:20px;padding:14px 16px;border-radius:10px;background:#FEF3C7;border-left:4px solid #EB8317;font-size:13px;line-height:1.6;color:#92400e;'>")
          .append("<strong>⚠️ Lưu ý:</strong> Bạn nên đổi mật khẩu ngay sau lần đăng nhập đầu tiên để đảm bảo an toàn tài khoản.")
          .append("</div>")
          .append("<p style='margin:20px 0 0;font-size:12px;color:#6b7280;line-height:1.6;'>")
          .append("Nếu bạn không yêu cầu tạo tài khoản này, vui lòng liên hệ quản trị hệ thống để được hỗ trợ.")
          .append("</p>")
          .append("</td></tr>")
          .append("<tr><td style='padding:16px 32px;background:#F0F4FA;color:#10375C;font-size:11px;text-align:center;font-weight:500;'>")
          .append("OmniCore WMS Hub © 2026. All rights reserved.")
          .append("</td></tr>")
          .append("</table>")
          .append("</td></tr>")
          .append("</table>")
          .append("</body></html>");

        return sb.toString();
    }

    private String buildOtpHtml(User user, String otpCode, long expiresAtMillis) {
        String fullName = escapeHtml(nullToPlaceholder(user.getFullName(), "Bạn"));
        String code = escapeHtml(nullToPlaceholder(otpCode, "------"));
        String expiry = VIET_TIME.format(Instant.ofEpochMilli(expiresAtMillis));

        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html>")
          .append("<html lang='vi'>")
          .append("<head>")
          .append("<meta charset='UTF-8'/>")
          .append("<meta name='viewport' content='width=device-width, initial-scale=1.0'/>")
          .append("<title>OmniCore WMS Hub - Mã xác thực OTP</title>")
          .append("</head>")
          .append("<body style='margin:0;padding:0;background:#F0F4FA;font-family:Inter,Arial,sans-serif;'>")
          .append("<table role='presentation' width='100%' cellpadding='0' cellspacing='0' style='background:#F0F4FA;'>")
          .append("<tr><td align='center' style='padding:40px 16px;'>")
          .append("<table role='presentation' width='520' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 8px 32px rgba(16,55,92,0.10);'>")
          .append("<tr><td style='background:#10375C;padding:20px 28px;color:#ffffff;'>")
          .append("<div style='display:table;width:100%;'>")
          .append("<div style='display:table-cell;vertical-align:middle;'>")
          .append("<div style='font-size:18px;font-weight:800;letter-spacing:-0.02em;'>OmniCore WMS Hub</div>")
          .append("<div style='font-size:12px;opacity:0.80;margin-top:4px;'>Mã xác thực đăng nhập</div>")
          .append("</div>")
          .append("</div>")
          .append("</td></tr>")
          .append("<tr><td style='padding:28px 28px;color:#1f2937;'>")
          .append("<h2 style='margin:0 0 8px;font-size:18px;font-weight:700;color:#10375C;'>Chào ").append(fullName).append("!</h2>")
          .append("<p style='margin:0 0 20px;font-size:14px;line-height:1.6;color:#4b5563;'>")
          .append("Đây là mã OTP để xác thực đăng nhập của bạn. Vui lòng nhập mã này để hoàn tất phiên đăng nhập.")
          .append("</p>")
          .append("<div style='text-align:center;margin:20px 0 24px;'>")
          .append("<div style='display:inline-block;padding:16px 28px;border-radius:12px;background:#10375C;color:#ffffff;font-size:28px;letter-spacing:8px;font-weight:800;'>")
          .append(code)
          .append("</div>")
          .append("</div>")
          .append("<div style='font-size:13px;color:#10375C;text-align:center;margin-bottom:20px;'>")
          .append("⏰ Mã OTP hết hạn lúc: <strong>").append(expiry).append("</strong>")
          .append("</div>")
          .append("<div style='padding:14px 16px;border-radius:10px;background:#FEF3C7;border-left:4px solid #EB8317;font-size:13px;line-height:1.5;color:#92400e;'>")
          .append("<strong>⚠️ Lưu ý:</strong> Nếu bạn không thực hiện đăng nhập, hãy đổi mật khẩu và thông báo cho quản trị viên ngay lập tức.")
          .append("</div>")
          .append("</td></tr>")
          .append("<tr><td style='padding:16px 28px;background:#F0F4FA;color:#10375C;font-size:11px;text-align:center;font-weight:500;'>")
          .append("OmniCore WMS Hub © 2026. All rights reserved.")
          .append("</td></tr>")
          .append("</table>")
          .append("</td></tr>")
          .append("</table>")
          .append("</body></html>");

        return sb.toString();
    }


    private String getConfigOrDefault(String key, String fallback) {
        String value = System.getProperty(key);
        if (isBlank(value)) {
            value = mailConfig != null ? mailConfig.getProperty(key) : null;
        }
        if (isBlank(value)) {
            value = System.getenv(key);
        }
        return isBlank(value) ? fallback : value;
    }

    private String getRequiredConfig(String key) {
        String value = getConfigOrDefault(key, null);
        if (isBlank(value)) {
            throw new IllegalStateException("Missing SMTP config: " + key);
        }
        return value;
    }

    private String nullToPlaceholder(String value, String placeholder) {
        return isBlank(value) ? placeholder : value;
    }

    private String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
