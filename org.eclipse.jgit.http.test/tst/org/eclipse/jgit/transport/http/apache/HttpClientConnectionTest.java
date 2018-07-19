package org.eclipse.jgit.transport.http.apache;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.message.AbstractHttpMessage;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HttpClientConnectionTest {
    @Test
    public void testGetHeaderFieldsAllowMultipleValues() throws MalformedURLException {
        HttpResponse responseMock = new HttpResponseMock();
        String headerField = "WWW-Authenticate";
        responseMock.addHeader(headerField, "Basic");
        responseMock.addHeader(headerField, "Digest");
        responseMock.addHeader(headerField, "NTLM");
        HttpClientConnection connection = new HttpClientConnection("http://0.0.0.0/");
        connection.resp = responseMock;
        List<String> headerValues = connection.getHeaderFields().get(headerField);
        assertEquals(3, headerValues.size());
        assertTrue(headerValues.contains("Basic"));
        assertTrue(headerValues.contains("Digest"));
        assertTrue(headerValues.contains("NTLM"));
    }

    private class HttpResponseMock extends AbstractHttpMessage implements HttpResponse {
        @Override
        public StatusLine getStatusLine() {
            return null;
        }

        @Override
        public void setStatusLine(StatusLine statusLine) {

        }

        @Override
        public void setStatusLine(ProtocolVersion protocolVersion, int i) {

        }

        @Override
        public void setStatusLine(ProtocolVersion protocolVersion, int i, String s) {

        }

        @Override
        public void setStatusCode(int i) throws IllegalStateException {

        }

        @Override
        public void setReasonPhrase(String s) throws IllegalStateException {

        }

        @Override
        public HttpEntity getEntity() {
            return null;
        }

        @Override
        public void setEntity(HttpEntity httpEntity) {

        }

        @Override
        public Locale getLocale() {
            return null;
        }

        @Override
        public void setLocale(Locale locale) {

        }

        @Override
        public ProtocolVersion getProtocolVersion() {
            return null;
        }
    }
}
