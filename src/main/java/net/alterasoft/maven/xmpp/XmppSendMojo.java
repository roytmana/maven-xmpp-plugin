package net.alterasoft.maven.xmpp;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;

@Mojo(name = "send", defaultPhase = LifecyclePhase.DEPLOY)
public class XmppSendMojo extends AbstractMojo {
  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Parameter(property = "configFile")
  private File configFile;

  @Parameter(property = "configText", defaultValue = "${xmpp-plugin.configText}")
  private String configText;

  @Parameter(property = "failOnError", defaultValue = "false", required = true)
  private boolean failOnError;

  @Parameter(property = "host")
  private String host;

  @Parameter(property = "port")
  private Integer port;

  @Parameter(property = "domain")
  private String domain;

  @Parameter(property = "userName")
  private String userName;

  @Parameter(property = "password")
  private String password;

  @Parameter(property = "recipients")
  private List<String> recipients = new ArrayList<>();

  @Parameter(property = "message", defaultValue = "${project.groupId}::${project.artifactId}-${project.version} ${project.build.finalName} has been deployed\\nCommit ${git.commit.id.abbrev} by ${git.commit.user.email}: \"${git.commit.message.short}\"")
  private String message;

  @Parameter(property = "mailConfig")
  private Properties mailConfig = new Properties();

  @Parameter(property = "mailConfigText", defaultValue = "${xmpp-plugin.mailConfigText}")
  private String mailConfigText;

  @Parameter(property = "mailEnabled")
  private Boolean mailEnabled;

  @Override public void execute() throws MojoExecutionException {
    Properties properties = new Properties();
    if (configFile != null) {
      try {
        properties.load(new FileReader(configFile));
      } catch (IOException e) {
        throw new MojoExecutionException("Could not open property file configFile" + configFile, e);
      }
    }
    configText = configText == null ? null : configText.trim();
    if (configText != null && !configText.isEmpty()) {
      try {
        properties.load(new StringReader(configText));
      } catch (IOException e) {
        throw new MojoExecutionException("Error parsing configText property. It must be a valid java properties format", e);
      }
    }
    if (!properties.isEmpty()) {
      String p = properties.getProperty("host");
      if (p != null && host == null) {
        host = p;
      }
      p = properties.getProperty("port", "5222");
      if (port == null) {
        port = Integer.valueOf(p);
      }
      p = properties.getProperty("domain");
      if (p != null && domain == null) {
        domain = p;
      }
      p = properties.getProperty("userName");
      if (p != null && userName == null) {
        userName = p;
      }
      p = properties.getProperty("message");
      if (p != null && password == null) {
        message = p;
      }
      p = properties.getProperty("password");
      if (p != null && password == null) {
        password = p;
      }
      p = properties.getProperty("recipients");
      if (p != null && recipients == null || recipients.isEmpty()) {
        recipients = Arrays.asList(p.split("[; ,]"));
      }
    }
    mailConfigText = mailConfigText == null ? null : mailConfigText.trim();
    if (mailConfigText != null && !mailConfigText.isEmpty()) {
      try {
        properties = new Properties();
        properties.load(new StringReader(mailConfigText));
        properties.putAll(this.mailConfig);
        this.mailConfig = properties;
      } catch (IOException e) {
        throw new MojoExecutionException("Error parsing mailConfigText property. It must be a valid java properties format", e);
      }
    }

    XMPPTCPConnectionConfiguration xmppConfig = null;
    try {
      xmppConfig = XMPPTCPConnectionConfiguration.builder()
          .setUsernameAndPassword(userName, password)
          .setXmppDomain(domain)
          .setHost(host)
          .setPort(port)
//          .setDebuggerEnabled(true)
          .setHostnameVerifier(new HostnameVerifier() {
            @Override public boolean verify(String s, SSLSession sslSession) {
              return true;
            }
          })
//          .setSendPresence(true)
          .build();
    } catch (Exception e) {
      throw new MojoExecutionException("Invalid XMPP configuration", e);
    }

    AbstractXMPPConnection conn = new XMPPTCPConnection(xmppConfig);
    try {
      final String connectTo = host + ":" + port;
      getLog().info("Attempting to connect to " + connectTo);
      final AbstractXMPPConnection connect = conn.connect();
      getLog().info("Successfully connected to " + connectTo);
      connect.login();
      getLog().info("Successfully authenticated " + connectTo);
//      conn.sendStanza(new Presence(Presence.Type.available));
      if (recipients != null && !recipients.isEmpty()) {
        ChatManager chatManager = ChatManager.getInstanceFor(conn);
        for (String recipient: recipients) {
          sendMessage(chatManager, recipient);
        }
      }
//      conn.sendStanza(new Presence(Presence.Type.unavailable));
    } catch (Throwable e) {
      getLog().error("Error sending XMPP message", e);
      if (failOnError) {
        throw new MojoExecutionException("Error sending XMPP message", e);
      }
    } finally {
      conn.disconnect();
    }

    if (Boolean.TRUE.equals(mailEnabled) || mailEnabled == null && mailConfig.getProperty("mail.smtp.host") != null) {
      final String mailPassword = trimNull(mailConfig.getProperty("password"), password);
      final Session smtp;
      if (mailPassword != null) {
        smtp = Session.getInstance(mailConfig, new Authenticator() {
          @Override protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(mailConfig.getProperty("mail.smtp.user"), mailPassword);
          }
        });
      } else {
        smtp = Session.getInstance(mailConfig);
      }
//      smtp.setDebug(true);
      final String rcp = trimNull(mailConfig.getProperty("recipients"));
      if (rcp != null) {
        final List<String> emails = Arrays.asList(rcp.split("[; ,]"));
        MimeMessage msg = new MimeMessage(smtp);
        try {
          List<Address> addresses = new ArrayList<>();
          for (String email: emails) {
            addresses.add(new InternetAddress(email, true));
          }
          msg.setRecipients(Message.RecipientType.TO, addresses.toArray(new Address[0]));
          msg.setSubject(message);
          msg.setSentDate(new Date());
          msg.setText(message, "utf-8", "plain");
        } catch (MessagingException e) {
          throw new MojoExecutionException("invalid email configuration", e);
        }
        try {
          getLog().info("Sending " + Arrays.asList(msg.getAllRecipients()));
          Transport.send(msg);
          getLog().info("Email has been sent");
        } catch (Throwable e) {
          getLog().error("Error sending SMTP message", e);
          if (failOnError) {
            throw new MojoExecutionException("Error sending SMTP message", e);
          }
        }
      }
    }
  }

  private String trimNull(String s) {
    if (s == null) {
      return s;
    } else {
      s = s.trim();
      return s.isEmpty() ? null : s;
    }
  }

  private String trimNull(String s, String defaultValue) {
    if (s == null) {
      return defaultValue;
    } else {
      s = s.trim();
      return s.isEmpty() ? defaultValue : s;
    }
  }

  private void sendMessage(ChatManager chatManager, String recipient) throws Exception {
    recipient = recipient == null ? null : recipient.trim();
    if (recipient != null && !recipient.isEmpty()) {
      EntityBareJid jid = JidCreate.entityBareFrom(recipient);
      chatManager.chatWith(jid).send(message);
      getLog().info("Sent message to " + recipient);
    }
  }
}
