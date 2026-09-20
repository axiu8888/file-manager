package com.kiftd.service;

import com.kiftd.entity.Propertie;
import com.kiftd.repository.PropertieRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@Service
@Slf4j
public class SystemService {

    private final PropertieRepository propertieRepository;

    public SystemService(PropertieRepository propertieRepository) {
        this.propertieRepository = propertieRepository;
    }

    public String osInfo() {
        return System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " / Java " + System.getProperty("java.version");
    }

    public String ping() {
        return "pong";
    }

    public Map<String, String> noticeMd5() {
        String notice = propertieRepository.findById("notice").map(Propertie::getPropertieValue).orElse("");
        String md5 = md5(notice);
        return Map.of("md5", md5);
    }

    public String noticeContext() {
        return propertieRepository.findById("notice").map(Propertie::getPropertieValue).orElse("");
    }

    @Transactional
    public void updateNotice(String content) {
        Propertie p = propertieRepository.findById("notice").orElseGet(() -> {
            Propertie n = new Propertie();
            n.setPropertieKey("notice");
            return n;
        });
        p.setPropertieValue(content);
        propertieRepository.save(p);
        Propertie md5 = propertieRepository.findById("notice_md5").orElseGet(() -> {
            Propertie n = new Propertie();
            n.setPropertieKey("notice_md5");
            return n;
        });
        md5.setPropertieValue(md5(content));
        propertieRepository.save(md5);
    }

    private String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }
}
