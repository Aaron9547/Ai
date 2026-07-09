package com.aaron.cloud.common.document;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import com.aaron.cloud.common.api.enums.infra.PlatformSettingKey;
import com.aaron.cloud.common.platform.PlatformSettingApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Component;

/**
 * 本机 Tesseract OCR（Tess4J），不依赖外部视觉 API。
 *
 * <p>需安装 Tesseract 或提供 {@code tessdata}（见 {@code TESSDATA_PREFIX}、项目根 {@code tessdata/}、
 * Windows 默认 {@code C:/Program Files/Tesseract-OCR/tessdata}）。语言包至少 {@code eng}，中文建议 {@code chi_sim}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalTesseractImageOcr {

    /** 短边低于该值时放大，利于表格/小字截图识别。 */
    private static final int TARGET_MIN_EDGE_PX = 1800;
    private static final int MAX_EDGE_PX = 4200;
    /** 表格/截图常用 PSM：6=块文本，11=稀疏文本，3=全自动。 */
    private static final int[] PAGE_SEG_MODES = {6, 11, 3};

    private final PlatformSettingApplicationService platformSettings;

    private volatile ITesseract tesseract;
    private volatile String resolvedLanguages;
    private volatile boolean initFailed;

    public boolean isEnabled() {
        return platformSettings.getBoolean(PlatformSettingKey.DOCUMENT_LOCAL_OCR_ENABLED);
    }

    private String configuredLanguages() {
        String raw = platformSettings.getEffectiveValueText(PlatformSettingKey.DOCUMENT_LOCAL_OCR_LANGUAGES);
        return raw == null || raw.isBlank() ? "chi_sim+eng" : raw.trim();
    }

    public Optional<String> tryExtract(byte[] bytes) {
        if (!isEnabled() || bytes == null || bytes.length == 0 || initFailed) {
            return Optional.empty();
        }
        try {
            ITesseract engine = engine();
            if (engine == null) {
                return Optional.empty();
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                return Optional.empty();
            }
            BufferedImage prepared = prepareForOcr(image);
            String text = recognizeBest(engine, prepared);
            if (text.isBlank()) {
                return Optional.empty();
            }
            log.debug("[本地图片OCR] 识别成功，字数 {}", text.length());
            return Optional.of(text);
        } catch (TesseractException ex) {
            log.warn("[本地图片OCR] Tesseract 识别失败：{}", ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("[本地图片OCR] 处理失败", ex);
            return Optional.empty();
        }
    }

    private synchronized ITesseract engine() {
        if (initFailed) {
            return null;
        }
        if (tesseract != null) {
            return tesseract;
        }
        Optional<Path> dataPath = resolveDatapath();
        if (dataPath.isEmpty()) {
            initFailed = true;
            log.warn(
                    "[本地图片OCR] 未找到 tessdata。请安装 Tesseract 并设置 TESSDATA_PREFIX，"
                            + "或将 eng.traineddata / chi_sim.traineddata 放到项目根 tessdata/ 目录。"
                            + "详见 scripts/setup-tessdata.ps1");
            return null;
        }
        Optional<String> langs = resolveLanguages(dataPath.get());
        if (langs.isEmpty()) {
            initFailed = true;
            log.warn(
                    "[本地图片OCR] tessdata 目录 {} 中缺少语言包（至少需要 eng.traineddata）",
                    dataPath.get());
            return null;
        }
        try {
            Tesseract t = new Tesseract();
            t.setDatapath(dataPath.get().toString());
            t.setLanguage(langs.get());
            t.setVariable("preserve_interword_spaces", "1");
            tesseract = t;
            resolvedLanguages = langs.get();
            log.info("[本地图片OCR] 已启用：datapath={}，languages={}", dataPath.get(), langs.get());
            return tesseract;
        } catch (Exception ex) {
            initFailed = true;
            log.warn("[本地图片OCR] 初始化失败：{}", ex.getMessage());
            return null;
        }
    }

    private Optional<String> resolveLanguages(Path dataPath) {
        List<String> candidates = new ArrayList<>();
        candidates.add(configuredLanguages());
        if (!"eng".equals(configuredLanguages())) {
            candidates.add("eng");
        }
        for (String spec : candidates) {
            if (spec == null || spec.isBlank()) {
                continue;
            }
            if (languagePackPresent(dataPath, spec)) {
                return Optional.of(spec);
            }
        }
        return Optional.empty();
    }

    private static boolean languagePackPresent(Path dataPath, String languageSpec) {
        for (String lang : languageSpec.split("\\+")) {
            String code = lang.trim();
            if (code.isEmpty()) {
                continue;
            }
            if (!Files.isRegularFile(dataPath.resolve(code + ".traineddata"))) {
                return false;
            }
        }
        return true;
    }

    private static Optional<Path> resolveDatapath() {
        String env = System.getenv("TESSDATA_PREFIX");
        if (env != null && !env.isBlank()) {
            Path p = Path.of(env.trim());
            if (isTessdataDir(p)) {
                return Optional.of(p);
            }
        }
        String prop = System.getProperty("tessdata.prefix");
        if (prop != null && !prop.isBlank()) {
            Path p = Path.of(prop.trim());
            if (isTessdataDir(p)) {
                return Optional.of(p);
            }
        }
        Path projectTess = Path.of(System.getProperty("user.dir", "."), "tessdata");
        if (isTessdataDir(projectTess)) {
            return Optional.of(projectTess);
        }
        Path winDefault = Path.of("C:/Program Files/Tesseract-OCR/tessdata");
        if (isTessdataDir(winDefault)) {
            return Optional.of(winDefault);
        }
        Path winAlt = Path.of("C:/Program Files (x86)/Tesseract-OCR/tessdata");
        if (isTessdataDir(winAlt)) {
            return Optional.of(winAlt);
        }
        return Optional.empty();
    }

    private static boolean isTessdataDir(Path dir) {
        return Files.isDirectory(dir) && Files.isRegularFile(dir.resolve("eng.traineddata"));
    }

    private static String recognizeBest(ITesseract engine, BufferedImage image)
            throws TesseractException {
        String best = "";
        for (int psm : PAGE_SEG_MODES) {
            if (engine instanceof Tesseract t) {
                t.setPageSegMode(psm);
            }
            String candidate = normalize(engine.doOCR(image));
            if (candidate.length() > best.length()) {
                best = candidate;
            }
        }
        return best;
    }

    static BufferedImage prepareForOcr(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int min = Math.min(w, h);
        double scale = 1.0;
        if (min > 0 && min < TARGET_MIN_EDGE_PX) {
            scale = (double) TARGET_MIN_EDGE_PX / min;
        }
        int nw = (int) Math.round(w * scale);
        int nh = (int) Math.round(h * scale);
        int maxEdge = Math.max(nw, nh);
        if (maxEdge > MAX_EDGE_PX) {
            double shrink = (double) MAX_EDGE_PX / maxEdge;
            nw = Math.max(1, (int) Math.round(nw * shrink));
            nh = Math.max(1, (int) Math.round(nh * shrink));
        }
        if (nw == w && nh == h) {
            return toRgb(src);
        }
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(toRgb(src), 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    private static BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) {
            return src;
        }
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replace('\r', '\n').trim();
        if (s.isEmpty()) {
            return "";
        }
        String[] lines = s.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String t = line.strip();
            if (t.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(t);
        }
        return sb.toString();
    }

    String resolvedLanguagesForTest() {
        return resolvedLanguages;
    }
}
