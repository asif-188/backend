package com.globalisor.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private NotificationService notificationService;

    public void sendPortalActivationEmail(String recipientEmail, String clientName, String plainPassword, String portalUrl) {
        logger.info("==========================================================");
        logger.info("[EMAIL DISPATCH] To: {}", recipientEmail);
        logger.info("[EMAIL DISPATCH] Subject: 🎉 Your Globalisor Client Portal is Activated!");
        logger.info("[EMAIL DISPATCH] Client Name: {}", clientName);
        logger.info("[EMAIL DISPATCH] Portal URL: {}", portalUrl);
        logger.info("[EMAIL DISPATCH] Login Email: {}", recipientEmail);
        logger.info("[EMAIL DISPATCH] Initial Password: {}", plainPassword);
        logger.info("==========================================================");

        // Send high-priority notification to client
        try {
            notificationService.sendNotification(
                recipientEmail,
                "🎉 Portal Activated & Welcome Pack",
                "Your application has been approved by your specialist! Login credentials have been dispatched to " + recipientEmail,
                "portal_activation",
                null,
                "High",
                portalUrl
            );
        } catch (Exception e) {
            logger.warn("Failed to send notification for email dispatch: {}", e.getMessage());
        }
    }
}
