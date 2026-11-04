package br.com.finamei.report;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Arquivo de relatório já gerado, com o nome e o tipo de conteúdo corretos para o download. */
public record ExportedReport(byte[] content, String filename, String contentType) {

	public ResponseEntity<byte[]> toResponse() {
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(contentType))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(filename).build().toString())
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(content);
	}
}
