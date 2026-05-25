package com.aaron.cloud.rag.crawl.fetch;

/** HTTP 拉取失败，含可重试的状态码（429/503 等）。 */
public class HttpFetcherException extends Exception {

    private final int statusCode;

    public HttpFetcherException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }

    public boolean retryable() {
        return statusCode == 429 || statusCode == 503 || statusCode == 502 || statusCode == 504;
    }
}
