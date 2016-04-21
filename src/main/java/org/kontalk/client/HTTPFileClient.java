/*
 *  Kontalk Java client
 *  Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.client;

import java.util.logging.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.kontalk.misc.KonException;
import org.kontalk.util.EncodingUtils;
import org.kontalk.util.MediaUtils;
import org.kontalk.util.TrustUtils;

/**
 * HTTP file transfer client.
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public class HTTPFileClient {
    private static final Logger LOGGER = Logger.getLogger(HTTPFileClient.class.getName());

    static final String KON_UPLOAD_FEATURE = "http://kontalk.org/extensions/message#upload";

    /** Regex used to parse content-disposition headers for download. */
    private static final Pattern CONTENT_DISPOSITION_PATTERN = Pattern
            .compile("attachment;\\s*filename\\s*=\\s*\"([^\"]*)\"");

    /** Message flags header for upload. */
    private static final String HEADER_MESSAGE_FLAGS = "X-Message-Flags";

    private final PrivateKey mPrivateKey;
    private final X509Certificate mCertificate;
    private final boolean mValidateCertificate;

    private HttpRequestBase mCurrentRequest;
    private CloseableHttpClient mHTTPClient = null;
    private ProgressListener mCurrentListener = null;

    public HTTPFileClient(PrivateKey privateKey,
            X509Certificate bridgeCert,
            boolean validateCertificate) {
        mPrivateKey = privateKey;
        mCertificate = bridgeCert;
        mValidateCertificate = validateCertificate;
    }

    // TODO unused
    public void abort() {
        if (mCurrentRequest != null){
            mCurrentRequest.abort();
            mCurrentRequest = null;
        }
        if (mCurrentListener != null) {
            mCurrentListener.updateProgress(-3);
            mCurrentListener = null;
        }
    }

    /**
     * Download file to directory.
     * @param url URL of file
     * @param base base directory in which the download is saved
     * @return absolute path of downloaded file, empty if download failed
     */
    public Path download(URI url, Path base, ProgressListener listener) throws KonException {
        if (mHTTPClient == null) {
            mHTTPClient = httpClientOrNull(mPrivateKey, mCertificate, mValidateCertificate);
            if (mHTTPClient == null)
                throw new KonException(KonException.Error.DOWNLOAD_CREATE);
        }

        LOGGER.config("from URL=" + url+ " ...");
        mCurrentRequest = new HttpGet(url);
        mCurrentListener = listener;

        // execute request
        CloseableHttpResponse response;
        try {
            response = mHTTPClient.execute(mCurrentRequest);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't execute request", ex);
            throw new KonException(KonException.Error.DOWNLOAD_EXECUTE);
        }

        try {
            int code = response.getStatusLine().getStatusCode();
            if (code != HttpStatus.SC_OK) {
                LOGGER.warning("unexpected response code: " + code);
                throw new KonException(KonException.Error.DOWNLOAD_RESPONSE);
            }

            HttpEntity entity = response.getEntity();
            if (entity == null) {
                LOGGER.warning("no download response entity");
                throw new KonException(KonException.Error.DOWNLOAD_RESPONSE);
            }

            // try getting filename from header
            String filename = "";
            Header dispHeader = response.getFirstHeader("Content-Disposition");
            if (dispHeader != null) {
                filename = parseContentDisposition(dispHeader.getValue());
                // never trust incoming data
                filename = Paths.get(filename).getFileName().toString();
                if (filename.isEmpty()) {
                    LOGGER.warning("can't parse filename in content: "+dispHeader.getValue());
                }
            }
            // NOTE: could try getting the extension (and filename) from URL, security?
            if (filename.isEmpty()) {
                // fallback
                String type = StringUtils.defaultString(entity.getContentType().getValue());
                String ext = MediaUtils.extensionForMIME(type);
                filename = "att_" + EncodingUtils.randomString(4) + "." + ext;
            }

            // get file size
            long s = -1;
            Header lengthHeader = response.getFirstHeader("Content-Length");
            if (lengthHeader == null) {
                LOGGER.warning("no length header");
            } else {
                try {
                    s = Long.parseLong(lengthHeader.getValue());
                } catch (NumberFormatException ex) {
                    LOGGER.log(Level.WARNING, "can' parse file size", ex);
                }
            }
            final long fileSize = s;
            mCurrentListener.updateProgress(s < 0 ? -2 : 0);

            Path destination = Paths.get(base.toString(), filename);
            if (Files.exists(destination)) {
                destination = Paths.get(base.toString(),
                        FilenameUtils.getBaseName(filename) +
                                "_" + EncodingUtils.randomString(4) +
                                "." + FilenameUtils.getExtension(filename));
                if (Files.exists(destination)) {
                    LOGGER.warning("not possible");
                    return Paths.get("");
                }
            }
            try (FileOutputStream out = new FileOutputStream(destination.toFile())){
                CountingOutputStream cOut = new CountingOutputStream(out) {
                    @Override
                    protected synchronized void afterWrite(int n) {
                        if (fileSize <= 0)
                            return;

                        // inform listener
                        mCurrentListener.updateProgress(
                                (int) (this.getByteCount() /(fileSize * 1.0) * 100));
                    }
                };
                entity.writeTo(cOut);
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "can't download file", ex);
                throw new KonException(KonException.Error.DOWNLOAD_WRITE);
            }

            // release http connection resource
            EntityUtils.consumeQuietly(entity);

            // TODO
            mCurrentRequest = null;
            mCurrentListener = null;

            return destination.toAbsolutePath();
        } finally {
            try {
                response.close();
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "can't close response", ex);
                // TODO can't use this client anymore(?)
            }
        }
    }

    /**
     * Upload file using a PUT request.
     * @return the URL the file can be downloaded with.
     */
    public void upload(File file, URI uploadURL, String mime, boolean encrypted) throws KonException {
        this.upload(file, uploadURL, mime, encrypted, false);
    }

    /**
     * Upload file using a POST request and parse download URL from response.
     * @return the URL the uploaded file can be downloaded with.
     */
    public URI uploadLegacy(File file, URI url, String mime, boolean encrypted) throws KonException {
        return this.upload(file, url, mime, encrypted, true);
    }

    private URI upload(File file, URI url, String mime, boolean encrypted, boolean legacy)
            throws KonException {
        if (mHTTPClient == null) {
            mHTTPClient = httpClientOrNull(mPrivateKey, mCertificate, mValidateCertificate);
            if (mHTTPClient == null)
                throw new KonException(KonException.Error.UPLOAD_CREATE);
        }

        // request type
        HttpEntityEnclosingRequestBase req = legacy ?
                new HttpPost(url) :
                new HttpPut(url);
        req.setHeader("Content-Type", mime);
        if (encrypted)
            req.addHeader(HEADER_MESSAGE_FLAGS, "encrypted");

        LOGGER.config("to URL=" + url+ " ...");

        // execute request
        CloseableHttpResponse response;
        try(FileInputStream in = new FileInputStream(file)) {
            req.setEntity(new InputStreamEntity(in, file.length()));

            mCurrentRequest = req;

            //response = execute(currentRequest);
            response = mHTTPClient.execute(mCurrentRequest);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't upload file", ex);
            throw new KonException(KonException.Error.UPLOAD_EXECUTE);
        }

        // get URL from response entity
        String downloadURL;
        try {
            int code = response.getStatusLine().getStatusCode();
            if (code != HttpStatus.SC_OK) {
                LOGGER.warning("unexpected response code: " + code);
                throw new KonException(KonException.Error.UPLOAD_RESPONSE);
            }

            if (!legacy)
                return null;

            HttpEntity entity = response.getEntity();
            if (entity == null) {
                LOGGER.warning("no upload response entity");
                throw new KonException(KonException.Error.UPLOAD_RESPONSE);
            }

            downloadURL = EntityUtils.toString(entity);

            // release http connection resource
            EntityUtils.consume(entity);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't get url from response", ex);
            throw new KonException(KonException.Error.UPLOAD_RESPONSE);
        } finally {
           try {
                response.close();
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "can't close response", ex);
            }
        }

        try {
            return new URI(downloadURL);
        } catch (URISyntaxException ex) {
            LOGGER.log(Level.WARNING, "can't parse URI", ex);
            throw new KonException(KonException.Error.UPLOAD_RESPONSE);
        }
    }

    private static CloseableHttpClient httpClientOrNull(PrivateKey privateKey,
            X509Certificate certificate,
            boolean validateCertificate) {
        HttpClientBuilder clientBuilder = HttpClients.custom();
        try {
            SSLContext sslContext = TrustUtils.getCustomSSLContext(privateKey,
                    certificate,
                    validateCertificate);
            clientBuilder.setSslcontext(sslContext);
        }
        catch (KeyStoreException |
                NoSuchAlgorithmException |
                CertificateException |
                IOException |
                KeyManagementException |
                UnrecoverableKeyException |
                NoSuchProviderException ex) {
            LOGGER.log(Level.WARNING, "unable to set SSL context", ex);
            return null;
        }

        RequestConfig requestConfig = RequestConfig.custom()
                // handle redirects :) TODO ?
                .setRedirectsEnabled(true)
                // HttpClient bug caused by Lighttpd
                .setExpectContinueEnabled(false)
                .setConnectTimeout(10 * 1000)
                .setSocketTimeout(10 * 1000)
                .build();
        clientBuilder.setDefaultRequestConfig(requestConfig);

        // create connection manager
        //ClientConnectionManager connMgr = new SingleClientConnManager(params, registry);

        return clientBuilder.build();
    }

    /*
     * Parse the Content-Disposition HTTP Header. The format of the header
     * is defined here: http://www.w3.org/Protocols/rfc2616/rfc2616-sec19.html
     * This header provides a filename for content that is going to be
     * downloaded to the file system. We only support the attachment type.
     */
    private static String parseContentDisposition(String contentDisposition) {
        Matcher m = CONTENT_DISPOSITION_PATTERN.matcher(contentDisposition);
        if (m.find())
            return StringUtils.defaultString(m.group(1));
        return "";
    }

    public interface ProgressListener {
        void updateProgress(int percent);
    }
}
