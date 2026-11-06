package com.claimflow.document;

import com.claimflow.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentServiceTest {

    @Test
    void acceptsRealPdfHeader() {
        DocumentService.verifyMagicBytes("%PDF-1.7 rest".getBytes(), "application/pdf");
    }

    @Test
    void rejectsFakePdf() {
        assertThatThrownBy(() -> DocumentService.verifyMagicBytes("<html>evil</html>".getBytes(), "application/pdf"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void sanitizesFileNames() {
        assertThat(DocumentService.sanitize("../../etc/passwd")).isEqualTo("passwd");
        assertThat(DocumentService.sanitize("C:\\Users\\me\\bill (1).pdf")).isEqualTo("bill _1_.pdf");
        assertThat(DocumentService.sanitize(null)).isEqualTo("document");
    }

    @Test
    void hashesConsistently() {
        assertThat(DocumentService.sha256("abc".getBytes()))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
