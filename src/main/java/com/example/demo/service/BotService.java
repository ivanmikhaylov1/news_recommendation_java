package com.example.demo.service;

import com.example.demo.domain.model.User;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BotService {
  @Value("${bot.secretKey}")
  private String secretKey;

  @Value("${bot.botUserName}")
  private String staticToken;

  private final UsersService usersService;

  public String getBotUrl() throws NoSuchPaddingException, NoSuchAlgorithmException {
    String botUrl =
  }

  public String encodeUserId(long userId) throws Exception {
    Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
    cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());

    ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
    buffer.putLong(userId);
    byte[] userIdBytes = buffer.array();

    byte[] encryptedBytes = cipher.doFinal(userIdBytes);
    return Base64.getEncoder().encodeToString(encryptedBytes);
  }

  public long decodeUserId(String encryptedUserId) throws Exception {
    Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
    cipher.init(Cipher.DECRYPT_MODE, getSecretKey());

    byte[] encryptedBytes = Base64.getDecoder().decode(encryptedUserId);
    byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

    ByteBuffer buffer = ByteBuffer.wrap(decryptedBytes);
    return buffer.getLong();
  }

  private SecretKey getSecretKey() {
    byte[] keyBytes = Base64.getDecoder().decode(secretKey);
    return new SecretKeySpec(keyBytes, "AES");
  }
}
