package br.com.finamei.report;

public enum ReportExportFormat {
	PDF("application/pdf", "pdf"),
	XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

	private final String contentType;
	private final String extension;

	ReportExportFormat(String contentType, String extension) {
		this.contentType = contentType;
		this.extension = extension;
	}

	public String contentType() {
		return contentType;
	}

	public String extension() {
		return extension;
	}
}
