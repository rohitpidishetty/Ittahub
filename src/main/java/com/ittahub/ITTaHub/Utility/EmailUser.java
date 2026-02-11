package com.ittahub.ITTaHub.Utility;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class EmailUser {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    public String from;

    public void sendSimpleEmail(String to, String subject, String repoName, String fromUser) {
        try {
//            System.out.println(to + " " + subject + " " + repoName + " " + fromUser);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);

            String htmlContent = String.format("""
                    <!DOCTYPE html>
                    <html>
                    <head>
                      <meta charset="UTF-8">
                      <title>ITTaHub - Repository Cloned</title>
                      <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    </head>
                    <body style="margin:0; padding:0; background-color:#0d1117; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Helvetica,Arial,sans-serif;">
                      <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#0d1117; padding:40px 0;">
                        <tr>
                          <td align="center">
                            <table width="600" cellpadding="0" cellspacing="0" style="background-color:#161b22; border-radius:12px; padding:30px; box-shadow:0 0 0 1px #30363d;">
                              <tr>
                                <td align="center" style="padding-bottom:15px;">
                                  <img src="https://ittahub.web.app/icon.png" alt="ITTaHub Logo" width="50px" style="display:block; border-radius:12px;">
                                </td>
                              </tr>
                              <tr>
                                <td style="padding-bottom:15px;">
                                  <h1 style="margin:0; color:#ffffff; font-size:22px; font-weight:600; text-align:center;">
                                    Repository Cloned Successfully 🚀
                                  </h1>
                                </td>
                              </tr>
                              <tr>
                                <td style="padding-bottom:20px; color:#c9d1d9; font-size:15px; line-height:1.6; text-align:center;">
                                  Hello <strong style="color:#ffffff;">%s</strong>,<br><br>
                                  The repository <strong style="color:#58a6ff;">%s</strong> has been successfully cloned to <strong style="color:#8b949e;">%s</strong>.
                                </td>
                              </tr>
                              <tr>
                                <td style="background-color:#0d1117; padding:18px; border-radius:8px; border:1px solid #30363d; margin-bottom:20px; text-align:center;">
                                  <p style="margin:0; color:#8b949e; font-size:13px;">Repository</p>
                                  <p style="margin:5px 0 0 0; color:#58a6ff; font-size:16px; font-weight:500;">
                                    %s / %s
                                  </p>
                                </td>
                              </tr>
                              <tr>
                                <td style="border-top:1px solid #30363d; padding-top:20px;">
                                  <p style="margin:0; color:#8b949e; font-size:12px; text-align:center;">
                                    You’re receiving this notification because you performed a clone action on ITTaHub.
                                  </p>
                                  <p style="margin:8px 0 0 0; color:#6e7681; font-size:11px; text-align:center;">
                                    © NFRAC, All rights reserved.
                                  </p>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>
                      </table>
                    </body>
                    </html>
                    """, to, repoName, fromUser, fromUser, repoName);

            helper.setText(htmlContent, true);
            helper.setFrom(from);

            mailSender.send(message);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
