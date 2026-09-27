package org.aml.service;

import jakarta.mail.internet.MimeMessage;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Value("${app.frontend.password-setup-url:http://localhost:3000/set-password}")
    private String passwordSetupUrl;

    @Value("${app.frontend.password-reset-url:http://localhost:3000/reset-password}")
    private String passwordResetUrl;

    /**
     * Creates the email service with the configured mail sender.
     *
     * @param mailSender sender used to dispatch email messages
     */
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
        logger.info("EmailService initialized successfully with JavaMailSender auto-configuration.");
    }

    /**
     * Sends a password setup invitation to a newly registered user.
     *
     * @param firstName recipient's first name
     * @param lastName recipient's last name
     * @param email recipient's email address
     * @param token password setup token
     * @param expiryTime expiry time associated with the setup token
     */
    @Async
    public void mailSend(String firstName, String lastName, String email, String token, LocalDateTime expiryTime) {
        String fullName = buildFullName(firstName, lastName);
        String subject = "Action Required: Activate Your AML Account and Set Password";
        String body = buildPasswordSetupBody(fullName, token, passwordSetupUrl);

        sendHtmlEmailInternal(email, subject, body, "New User Password Setup");
    }


    /**
     * Sends a password recovery email containing a reset token.
     *
     * @param email recipient's email address
     * @param firstName recipient's first name
     * @param lastName recipient's last name
     * @param token password reset token
     */
    @Async
    public void sendForgotPasswordEmail(String email, String firstName, String lastName, String token) {
        String fullName = buildFullName(firstName, lastName);
        String subject = "Password Reset Request";
        String body = buildForgotPasswordBody(fullName, token, passwordResetUrl);

        sendHtmlEmailInternal(email, subject, body, "Password Reset Request");
    }

    /**
     * Sends a notification confirming that the account password changed.
     *
     * @param email recipient's email address
     * @param firstName recipient's first name
     * @param lastName recipient's last name
     */
    @Async
    public void sendPasswordChangedNotification(String email, String firstName, String lastName) {
        String fullName = buildFullName(firstName, lastName);
        String subject = "Security Alert: Password Updated";
        String body = buildPasswordChangedBody(fullName, email);

        sendHtmlEmailInternal(email, subject, body, "Password Changed Security Alert");
    }

    // ==========================================
    // Central Reusable Core Execution Engine
    // ==========================================

    /**
     * Builds and sends an HTML message while logging delivery failures.
     *
     * @param toEmail recipient email address
     * @param subject message subject
     * @param body HTML message body
     * @param contextLabel label used in delivery logs
     */
    private void sendHtmlEmailInternal(String toEmail, String subject, String body, String contextLabel) {
        logger.info("Background thread '{}' started email workflow [{}]",
                Thread.currentThread().getName(), contextLabel);

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");

            helper.setFrom(senderEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body, true); // Keep consistent styling across REST consumption layouts

            mailSender.send(mimeMessage);
            logger.info("[{}] email communication successfully dispatched", contextLabel);
        } catch (Exception e) {
            logger.error("Critical delivery failure: Unable to dispatch [{}] message payload", contextLabel, e);
        }
    }

    // ==========================================
    // Modular Reusable Helper Utilities
    // ==========================================

    /**
     * Combines first and last names, using a generic name when both are blank.
     *
     * @param firstName recipient first name
     * @param lastName recipient last name
     * @return combined display name
     */
    private String buildFullName(String firstName, String lastName) {
        String safeFirstName = (firstName != null) ? firstName.trim() : "";
        String safeLastName = (lastName != null) ? lastName.trim() : "";
        String fullName = (safeFirstName + " " + safeLastName).trim();
        return fullName.isEmpty() ? "User" : fullName;
    }

    /**
     * Builds the HTML message body for initial account password setup.
     *
     * @param fullName recipient display name
     * @param token password setup token
     * @param setupUrl configured frontend password setup route
     * @return HTML password setup message
     */
    private static @NonNull String buildPasswordSetupBody(String fullName, String token, String setupUrl) {
        String setupPasswordLink = appendToken(setupUrl, token);

        return "<html>"
                + "<body style=\"font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; color: #333333; background-color: #f9fafb; margin: 0; padding: 40px 20px;\">"
                + "  <div style=\"max-width: 600px; margin: 0 auto; background-color: #ffffff; padding: 40px; border-radius: 8px; box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05); border: 1px solid #e5e7eb;\">"
                + "    <h2 style=\"color: #111827; font-size: 24px; font-weight: 600; margin-top: 0; margin-bottom: 16px;\">Welcome to the AML Platform!</h2>"
                + "    <p style=\"font-size: 16px; line-height: 1.6; color: #4b5563; margin-bottom: 24px;\">"
                + "      Dear " + escapeHtml(fullName) + ",<br><br>"
                + "      Thank you for registering. We are thrilled to welcome you to our platform. Your profile has been successfully configured, and your enterprise workspace is ready for deployment."
                + "    </p>"
                + "    <p style=\"font-size: 16px; line-height: 1.6; color: #4b5563; margin-bottom: 32px;\">"
                + "      To finalize your credentials onboarding sequence and secure your profile dashboard, please establish your personal portal password by clicking the button below:"
                + "    </p>"
                + "    <div style=\"text-align: center; margin-bottom: 32px;\">"
                + "      <a href=\"" + setupPasswordLink + "\" style=\"background-color: #2563eb; color: #ffffff; padding: 14px 28px; text-decoration: none; display: inline-block; border-radius: 6px; font-weight: 500; font-size: 16px; box-shadow: 0 2px 4px rgba(37, 99, 235, 0.2); transition: background-color 0.2s;\">"
                + "        Activate Account & Set Password"
                + "      </a>"
                + "    </div>"
                + "    <hr style=\"border: 0; border-top: 1px solid #e5e7eb; margin-bottom: 24px;\">"
                + "    <p style=\"font-size: 13px; line-height: 1.5; color: #9ca3af; margin-bottom: 0;\">"
                + "      <strong>Security Note:</strong> This activation context configuration routing link is strictly encrypted and will remain valid for the next 24 hours. "
                + "      If you did not initiate this registration process, please disregard this communication safely."
                + "    </p>"
                + "    <p style=\"font-size: 14px; color: #6b7280; margin-top: 32px; margin-bottom: 0;\">"
                + "      Best regards,<br>"
                + "      <strong>The AML Corporate Security Team</strong>"
                + "    </p>"
                + "  </div>"
                + "</body>"
                + "</html>";
    }

    /**
     * Builds the HTML message body for a password reset request.
     *
     * @param fullName recipient display name
     * @param token password reset token
     * @param resetUrl configured frontend password reset route
     * @return HTML password reset message
     */
    private static @NonNull String buildForgotPasswordBody(String fullName, String token, String resetUrl) {
        String resetLink = appendToken(resetUrl, token);
        return "<html><body>"
                + "<p>Dear " + escapeHtml(fullName) + ",</p>"
                + "<p>We received a request to reset your password. Click the button below to configure new credentials:</p>"
                + "<p><a href=\"" + resetLink + "\" style=\"background-color: #E11D48; color: white; padding: 10px 20px; text-decoration: none; display: inline-block; border-radius: 4px; font-weight: bold;\">Reset My Password</a></p>"
                + "<p><em>This link is valid for the next 15 minutes. If you did not make this request, please ignore this email safely.</em></p>"
                + "<br><p>Best regards,<br>The AML Security Team</p>"
                + "</body></html>";
    }

    /**
     * Builds the HTML message body confirming a password change.
     *
     * @param fullName recipient display name
     * @param email account email address
     * @return HTML password change notification
     */
    private static @NonNull String buildPasswordChangedBody(String fullName, String email) {
        return "<html><body>"
                + "<p>Dear " + escapeHtml(fullName) + ",</p>"
                + "<p>This is a confirmation that the password for your account (<strong>" + escapeHtml(email) + "</strong>) was successfully changed recently.</p>"
                + "<p style='color: #DC2626; font-weight: bold;'>If you did not make this change, please contact our security and compliance operations support team immediately to secure your profile.</p>"
                + "<br><p>Best regards,<br>The Security Team</p>"
                + "</body></html>";
    }

    /**
     * Adds a URL-encoded token to a configured frontend route.
     *
     * @param baseUrl configured frontend route
     * @param token verification or reset token
     * @return route with a token query parameter
     */
    private static String appendToken(String baseUrl, String token) {
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    /**
     * Escapes user-provided text before embedding it in HTML email.
     *
     * @param value text to escape
     * @return HTML-escaped text, or an empty string when the input is null
     */
    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
