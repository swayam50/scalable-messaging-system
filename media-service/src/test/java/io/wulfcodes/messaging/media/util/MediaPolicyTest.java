package io.wulfcodes.messaging.media.util;

import io.wulfcodes.messaging.common.model.vo.ContentType;
import io.wulfcodes.messaging.media.model.vo.MediaRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class MediaPolicyTest {

    @ParameterizedTest
    @CsvSource({
            "image/png,        cat.png,      IMAGE, 10",
            "IMAGE/JPEG,       me.JPG,       IMAGE, 10",
            "video/mp4,        clip.mp4,     VIDEO, 100",
            "audio/mpeg,       song.mp3,     AUDIO, 20",
            "application/pdf,  report.pdf,   FILE,  25",
            "text/plain,       notes.txt,    FILE,  25",
    })
    void classifiesByMimeTypeWithPerTypeLimits(String mime, String name, ContentType type, long maxMb) {
        MediaRule rule = MediaPolicy.classify(mime, name).orElseThrow();

        assertThat(rule.contentType()).isEqualTo(type);
        assertThat(rule.maxBytes()).isEqualTo(maxMb * MediaPolicy.MB);
    }

    @ParameterizedTest
    @CsvSource({
            "application/x-msdownload, setup.exe",
            "application/octet-stream, run.sh",       // dangerous extension even with a generic MIME type
            "image/png,                 trick.png.exe",
            "text/javascript,           app.js",
    })
    void executablesAndScriptsAreRefused(String mime, String name) {
        assertThat(MediaPolicy.classify(mime, name)).isEmpty();
    }

    @Test
    void fileNamesAreSanitizedForObjectKeysAndHeaders() {
        assertThat(MediaPolicy.sanitizeFileName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(MediaPolicy.sanitizeFileName("C:\\Users\\me\\My Photo (1).png")).isEqualTo("My_Photo_1_.png");
        assertThat(MediaPolicy.sanitizeFileName("..")).isEqualTo("file");
        assertThat(MediaPolicy.sanitizeFileName("a".repeat(300) + ".pdf")).hasSize(120).endsWith(".pdf");
    }
}
