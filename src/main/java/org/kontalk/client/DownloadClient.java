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
import java.io.FileOutputStream;
import java.io.IOException;
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
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.kontalk.util.TrustUtils;

/**
 *
 * @author Alexander Bikadorov {@literal <bikaejkb@mail.tu-berlin.de>}
 */
public class DownloadClient {
    private final static Logger LOGGER = Logger.getLogger(DownloadClient.class.getName());

    /** Regex used to parse content-disposition headers */
    private static final Pattern CONTENT_DISPOSITION_PATTERN = Pattern
            .compile("attachment;\\s*filename\\s*=\\s*\"([^\"]*)\"");

    private final PrivateKey mPrivateKey;
    private final X509Certificate mCertificate;
    private final boolean mValidateCertificate;
    private final ProgressListener mListener;

    private HttpRequestBase mCurrentRequest;
    private CloseableHttpClient mHTTPClient;

    public DownloadClient(PrivateKey privateKey,
            X509Certificate bridgeCert,
            boolean validateCertificate,
            ProgressListener listener) {
        mPrivateKey = privateKey;
        mCertificate = bridgeCert;
        mValidateCertificate = validateCertificate;
        mListener = listener;
    }

    // TODO unused
    public void abort() {
        if (mCurrentRequest != null)
            mCurrentRequest.abort();
        mListener.updateProgress(-3);
    }

    /**
     * Downloads to a directory represented by a {@link File} object,
     * determining the file name from the Content-Disposition header.
     * @param url URL of file
     * @param base base directory in which the download is saved
     * @return the absolute file path of the downloaded file, or an empty string
     * if the file could not be downloaded
     */
    public String download(String url, File base) {
        if (mHTTPClient == null) {
            mHTTPClient = createHTTPClient(mPrivateKey, mCertificate, mValidateCertificate);
            if (mHTTPClient == null)
                return "";
        }

        LOGGER.info("downloading file from URL=" + url+ "...");
        mCurrentRequest = new HttpGet(url);

        // execute request
        CloseableHttpResponse response;
        try {
            response = mHTTPClient.execute(mCurrentRequest);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "can't execute request", ex);
            return "";
        }

        try {
            int code = response.getStatusLine().getStatusCode();
            // HTTP/1.1 200 OK -- other codes should throw Exceptions
            if (code != 200) {
                LOGGER.warning("invalid response code: " + code);
                return "";
            }

            // get filename
            Header dispHeader = response.getFirstHeader("Content-Disposition");
            if (dispHeader == null) {
                LOGGER.warning("no content header");
                return "";
            }
            String filename = parseContentDisposition(dispHeader.getValue());
            // never trust incoming data
            filename = filename != null ? new File(filename).getName() : "";
            if (filename.isEmpty()) {
                LOGGER.warning("no filename in content: "+dispHeader.getValue());
                return "";
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
            mListener.updateProgress(s < 0 ? -2 : 0);

            // TODO should check for content-disposition parsing here
            // and choose another filename if necessary
            HttpEntity entity = response.getEntity();
            if (entity == null) {
                LOGGER.warning("no entity in response");
                return "";
            }

            File destination = new File(base, filename);
            if (destination.exists()) {
                LOGGER.warning("file already exists: "+destination.getAbsolutePath());
                return "";
            }
            try (FileOutputStream out = new FileOutputStream(destination)){
                CountingOutputStream cOut = new CountingOutputStream(out) {
                    @Override
                    protected synchronized void afterWrite(int n) {
                        if (fileSize <= 0)
                            return;

                        // inform listener
                        mListener.updateProgress(
                                (int) (this.getByteCount() /(fileSize * 1.0) * 100));
                    }
                };
                entity.writeTo(cOut);
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "can't download file", ex);
                return "";
            }

            LOGGER.info("... download successful!");
            return destination.getAbsolutePath();
        } finally {
            try {
                response.close();
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "can't close response", ex);
            }
        }
    }

    private static CloseableHttpClient createHTTPClient(PrivateKey privateKey,
            X509Certificate certificate,
            boolean validateCertificate) {
        //HttpClientBuilder clientBuilder = HttpClientBuilder.create();
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

        RequestConfig.Builder rcBuilder = RequestConfig.custom();
        // handle redirects :)
        rcBuilder.setRedirectsEnabled(true);
        // HttpClient bug caused by Lighttpd
        rcBuilder.setExpectContinueEnabled(false);
        clientBuilder.setDefaultRequestConfig(rcBuilder.build());

        // create connection manager
        //ClientConnectionManager connMgr = new SingleClientConnManager(params, registry);

        //return new DefaultHttpClient(connMgr, params);
        return clientBuilder.build();
    }

    /*
     * Parse the Content-Disposition HTTP Header. The format of the header
     * is defined here: http://www.w3.org/Protocols/rfc2616/rfc2616-sec19.html
     * This header provides a filename for content that is going to be
     * downloaded to the file system. We only support the attachment type.
     */
    private static String parseContentDisposition(String contentDisposition) {
        if (contentDisposition == null)
            return null;

        Matcher m = CONTENT_DISPOSITION_PATTERN.matcher(contentDisposition);
        if (m.find())
            return m.group(1);
        else return null;
    }

    public interface ProgressListener {
        void updateProgress(int percent);
    }
}
